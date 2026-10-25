package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AiTool;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.AiToolPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * NoTokenTest - "when there is no token, the request is not generated".
 *
 * The property under test is the security one: with no auth token, no generation
 * happens. The app enforces that at one of two points, and both are valid —
 * which one you see depends on the environment:
 *
 *   1. Route gate     - the tool route itself bounces to / and raises the
 *                       sign-in modal before the page renders (production).
 *   2. Generate gate  - the tool page renders fully, and the block happens on
 *                       the Generate click (staging).
 *
 * Asserting only mechanism 2 made this suite fail wholesale against production,
 * where the app blocks *earlier* — a stricter gate reported as seven failures.
 * So the test accepts either gate and fails only if a generation actually gets
 * through. This holds for every tool, metered or not (AI Chat is unmetered and
 * still blocks), so it is checked across all seven.
 *
 * Spends no credits: nothing ever generates. Extends {@link BaseTest} rather than
 * {@link SingleSessionTest} precisely so each method runs in a fresh, signed-out
 * context with no restored session.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("Generation Auth Gate")
public class NoTokenTest extends BaseTest {

    private static final int MODAL_TIMEOUT_MS = 10_000;

    @DataProvider(name = "allTools")
    public Object[][] allTools() {
        AiTool[] tools = AiTool.values();
        Object[][] data = new Object[tools.length][1];
        for (int i = 0; i < tools.length; i++) {
            data[i][0] = tools[i];
        }
        return data;
    }

    // ----------------------------------------------------------------
    // TC_NOAUTH_001 - No token: Generate is blocked by the sign-in modal
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "allTools",
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_AUTH},
            description = "With no session, clicking Generate raises the sign-in modal and generates nothing"
    )
    @Story("No token blocks generation")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Guards the generation auth gate: a signed-out user must not be able to spend the "
            + "generate path. The request must be intercepted by the sign-in modal, not sent.")
    public void TC_NOAUTH_001_generateBlockedWithoutToken(AiTool tool) {
        // Fresh BaseTest context = no token.
        AiToolPage page = new AiToolPage(tool);
        boolean pageRendered = page.openWhileSignedOut();

        if (!pageRendered) {
            // Gate 1: the route itself was guarded. The generate path was never
            // reachable, which is a stricter block than the one below.
            // Either signal is a real gate: the sign-in modal, or being bounced off
            // the tool's own route. Demanding the modal specifically made this fail
            // on a redirect that had already blocked the user just as effectively.
            Assert.assertTrue(page.isAuthModalShown() || page.isRouteGated(),
                    tool.displayName() + " did not render its page signed out, but also "
                            + "neither raised the sign-in modal nor left its route — the "
                            + "user is stranded on a blank tool page. URL: "
                            + page.getCurrentUrl());
            Assert.assertFalse(page.hasGeneratedResult(),
                    tool.displayName() + " must not produce any result while signed out.");

            page.takeScreenshot("TC_NOAUTH_001_" + tool.name() + "_Route_Gated");
            logger.info("TC_NOAUTH_001 PASSED: {} gated the route before rendering.",
                    tool.displayName());
            return;
        }

        // Gate 2: the page is usable while signed out — that is the risk being guarded.
        page.enterPrompt(tool.samplePrompt());
        Assert.assertTrue(page.isGenerateEnabled(),
                tool.displayName() + " generate should be clickable once a prompt is entered, "
                        + "even signed out.");

        page.clickGenerate();

        // The request must be stopped by the sign-in gate, not carried out.
        Assert.assertTrue(page.waitForAuthModal(MODAL_TIMEOUT_MS),
                tool.displayName() + " must raise the sign-in modal when generating with no token.");
        Assert.assertFalse(page.hasGeneratedResult(),
                tool.displayName() + " must not produce any result while signed out.");
        Assert.assertFalse(page.isPaywallShown(),
                tool.displayName() + " should raise the sign-in modal, not the upgrade paywall, "
                        + "when the block is a missing token rather than a plan limit.");

        page.takeScreenshot("TC_NOAUTH_001_" + tool.name() + "_SignIn_Required");
        logger.info("TC_NOAUTH_001 PASSED: {} blocked generation with the sign-in modal.",
                tool.displayName());
    }
}
