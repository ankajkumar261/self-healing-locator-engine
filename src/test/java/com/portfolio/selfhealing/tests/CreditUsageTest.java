package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AiTool;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.AiToolPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * CreditUsageTest - regression cover for the two reported credit defects.
 *
 *   DEF-001  AI Document generates but the balance never moves.
 *   DEF-005  The balance moves by the wrong amount (50 - 12 showed 35, not 38).
 *
 * Both are the same assertion from opposite sides, so one test covers them:
 * the balance must fall by exactly the price the app advertised on the button.
 * A run where nothing is deducted fails the first assert; a run where the wrong
 * amount is deducted fails the second, and the message says which.
 *
 * SPENDS REAL CREDITS. Not in the default suite. Run deliberately:
 *
 *   mvn test -Dtest=CreditUsageTest                    # Document + Image (26 credits)
 *   mvn test -Dtest=CreditUsageTest -Dcredits.all=true # every metered tool (~343)
 *
 * The advertised price is read off the generate button rather than taken from
 * {@link AiTool#creditCost()} on purpose: the defect is a mismatch between what
 * the app promises and what it charges, and a hardcoded expectation could not
 * tell those apart.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("Credits")
public class CreditUsageTest extends SingleSessionTest {

    /** Cheapest metered tools; DOCUMENT is the one named in the defect report. */
    private static final AiTool[] DEFAULT_TOOLS = {AiTool.DOCUMENT, AiTool.IMAGE};

    private static final int GENERATION_TIMEOUT_MS = 180_000;
    /** The debit lands server-side and is pushed back, so it trails the result. */
    private static final int BALANCE_SETTLE_MS     = 60_000;

    @DataProvider(name = "meteredTools")
    public Object[][] meteredTools() {
        AiTool[] tools = Boolean.getBoolean("credits.all")
                ? java.util.Arrays.stream(AiTool.values())
                    .filter(AiTool::hasCreditLabel)
                    .toArray(AiTool[]::new)
                : DEFAULT_TOOLS;

        logger.warn("Credit-usage run will spend approximately {} credits across {}",
                java.util.Arrays.stream(tools).mapToInt(AiTool::creditCost).sum(),
                java.util.Arrays.toString(tools));

        return java.util.Arrays.stream(tools).map(t -> new Object[]{t}).toArray(Object[][]::new);
    }

    // ----------------------------------------------------------------
    // TC_DEF_001 - A generation debits exactly the advertised cost
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "meteredTools",
            groups = {AppConstants.GROUP_DEFECTS, AppConstants.GROUP_GENERATION},
            description = "Generating debits the balance by exactly the advertised credit cost"
    )
    @Story("Credit deduction")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Covers DEF-001 (no deduction) and DEF-005 (wrong deduction). "
            + "Reads the balance before and after a real generation and compares the "
            + "delta against the price shown on the generate button.")
    public void TC_DEF_001_generationDebitsAdvertisedCost(AiTool tool) {
        AiToolPage page = new AiToolPage(tool).open();

        int before = page.getCreditBalance();
        Assert.assertTrue(before > 0,
                "Could not read a credit balance before generating (got " + before + ").");

        page.enterPrompt(tool.samplePrompt());

        int advertised = page.getAdvertisedCreditCost();
        Assert.assertNotEquals(advertised, AiTool.NO_CREDIT_LABEL,
                tool.displayName() + " is a metered tool but its generate button "
                        + "advertised no price ('" + page.getGenerateLabel() + "').");

        if (advertised == 0) {
            // The app currently prices some tools at 0 (AI Image, at the time of
            // writing). There is no debit to measure, and asserting one would
            // report a defect that the app never promised.
            throw new SkipException(String.format(
                    "%s advertises 0 credits ('%s') — no debit to verify.",
                    tool.displayName(), page.getGenerateLabel()));
        }

        if (before < advertised) {
            throw new SkipException(String.format(
                    "Balance %d is below %s's cost of %d — cannot measure a debit.",
                    before, tool.displayName(), advertised));
        }

        page.clickGenerate();

        // Tools priced at 100+ stop on a confirm dialog; cheap ones never do.
        boolean confirmed = page.confirmChargeIfShown();
        if (advertised >= AiToolPage.CONFIRM_CHARGE_THRESHOLD) {
            Assert.assertTrue(confirmed,
                    tool.displayName() + " costs " + advertised + " credits and should have "
                            + "asked for confirmation before charging.");
        }

        boolean produced = page.waitForGeneratedMedia(GENERATION_TIMEOUT_MS);

        Assert.assertFalse(page.isAuthModalShown(),
                tool.displayName() + " asked us to sign in — the session lapsed. "
                        + "Delete target/state/auth.json and re-run.");
        Assert.assertFalse(page.isPaywallShown(),
                tool.displayName() + " raised the upgrade dialog instead of generating.");

        // The balance is measured whether or not a result rendered. Bailing out
        // on a missing result would hide the worst outcome: charged and given
        // nothing. That case has to be reported as a charge, not as a timeout.
        int after = page.waitForSettledCreditBalance(before, BALANCE_SETTLE_MS);
        int deducted = before - after;

        // Two very different failures hide behind "no result", and they need
        // different owners: a charge with nothing delivered is a billing bug; no
        // charge and nothing delivered is a broken generation.
        Assert.assertTrue(produced, deducted > 0
                ? String.format(
                        "%s produced no on-page result within %ds, yet %d credits were "
                                + "taken (balance %d -> %d). CHARGED WITHOUT DELIVERING — %s",
                        tool.displayName(), GENERATION_TIMEOUT_MS / 1000,
                        deducted, before, after, page.lastGenerationOutcome())
                : String.format(
                        "%s produced no on-page result within %ds and took no credits "
                                + "(balance unchanged at %d, advertised %d) — %s. The "
                                + "generation did not run; billing is not at fault here.",
                        tool.displayName(), GENERATION_TIMEOUT_MS / 1000,
                        before, advertised, page.lastGenerationOutcome()));

        page.takeScreenshot("TC_DEF_001_" + tool.name() + "_Credits");
        logger.info("{}: balance {} -> {} (deducted {}, advertised {})",
                tool.displayName(), before, after, deducted, advertised);

        // DEF-001: the generation succeeded, so *something* must have been charged.
        Assert.assertNotEquals(after, before, String.format(
                "DEF-001: %s generated successfully but no credits were deducted — "
                        + "balance stayed at %d despite an advertised cost of %d.",
                tool.displayName(), before, advertised));

        // DEF-005: and it must be the advertised amount, not merely non-zero.
        Assert.assertEquals(deducted, advertised, String.format(
                "DEF-005: %s deducted %d credits but advertised %d "
                        + "(balance %d -> %d, expected %d).",
                tool.displayName(), deducted, advertised, before, after, before - advertised));

        logger.info("TC_DEF_001 PASSED: {} charged exactly {} credits.",
                tool.displayName(), advertised);
    }

    // ----------------------------------------------------------------
    // TC_DEF_002 - A cancelled charge confirmation costs nothing
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_DEFECTS},
            description = "Backing out of the charge confirmation leaves the balance untouched"
    )
    @Story("Credit deduction")
    @Severity(SeverityLevel.CRITICAL)
    @Description("The confirm-charge dialog is the last point of consent. Cancelling it "
            + "must not debit. Spends nothing, so this runs outside the generation group.")
    public void TC_DEF_002_cancelledChargeCostsNothing() {
        // Video is the dearest tool, so it reliably raises the confirm dialog.
        AiToolPage page = new AiToolPage(AiTool.VIDEO).open();

        int before = page.getCreditBalance();
        page.enterPrompt(AiTool.VIDEO.samplePrompt());
        page.clickGenerate();

        if (!page.waitForConfirmCharge(AppConstants.MEDIUM_WAIT)) {
            throw new SkipException(
                    "AI Video did not raise the confirm-charge dialog; nothing to cancel. "
                            + "It may have hit the paywall or an empty balance instead.");
        }

        Assert.assertEquals(page.getConfirmChargeCost(), page.getAdvertisedCreditCost(),
                "The confirm dialog should quote the same price as the generate button.");

        page.cancelCharge();
        page.waitForMs(3000);

        int after = page.getCreditBalance();
        page.takeScreenshot("TC_DEF_002_Cancelled_Charge");

        Assert.assertEquals(after, before, String.format(
                "Cancelling the charge confirmation still moved the balance (%d -> %d).",
                before, after));

        logger.info("TC_DEF_002 PASSED: cancelled charge left the balance at {}.", before);
    }
}
