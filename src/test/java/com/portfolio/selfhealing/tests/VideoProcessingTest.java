package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AiTool;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.AiToolPage;
import com.portfolio.selfhealing.pages.DashboardPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * VideoProcessingTest - regression cover for the reported background-processing defect.
 *
 *   DEF-007  Background video processing sticks in History: the row keeps
 *            showing "Processing" long after the job should have finished.
 *
 * The History row only renders a status badge while a job is unfinished — the
 * app drops the badge once the status reaches succeeded/completed. So "the
 * badge is gone" is the definition of settled, and a badge that survives the
 * budget is the defect.
 *
 * Two tests, because they cost very different amounts:
 *
 *   TC_DEF_007  free. Audits whatever the account's History already holds.
 *   TC_DEF_007b spends 125 credits. Generates a video and follows it through.
 *
 * Run the paid one deliberately:  mvn test -Dtest=VideoProcessingTest -Dgroups=generation
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("History")
public class VideoProcessingTest extends SingleSessionTest {

    /**
     * How long a job may legitimately stay unfinished before it counts as stuck.
     * Hailuo/Kling video jobs are the slowest thing the app runs, so this is
     * generous on purpose — a false "stuck" report is worse than a slow test.
     */
    private static final int SETTLE_TIMEOUT_MS = 10 * 60_000;
    private static final int POLL_MS           = 30_000;
    private static final int GENERATION_TIMEOUT_MS = 300_000;

    // ----------------------------------------------------------------
    // TC_DEF_007 - Nothing in History is stuck mid-processing
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_DEFECTS, AppConstants.GROUP_HISTORY},
            description = "History entries left in a processing state eventually settle"
    )
    @Story("Background processing")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Covers DEF-007 without spending credits. Audits the account's existing "
            + "History for unfinished rows and gives each one a generous window to settle. "
            + "A row still processing after that window is genuinely stuck, not merely slow.")
    public void TC_DEF_007_noHistoryEntryStaysStuck() {
        DashboardPage dashboard = new DashboardPage();
        dashboard.waitForDashboard().openHistory();

        int rows = dashboard.getHistoryEntryCount();
        if (rows == 0) {
            throw new SkipException(
                    "History is empty on this account, so there is nothing to audit. "
                            + "Run TC_DEF_007b to generate a video and follow it through.");
        }

        java.util.List<String> unsettled = dashboard.getUnsettledHistoryEntries();
        java.util.List<String> failed = dashboard.getFailedHistoryEntries();
        logger.info("History holds {} rows; {} in flight: {}; {} failed: {}",
                rows, unsettled.size(), unsettled, failed.size(), failed);

        // A failed generation is a finished one. Worth reporting, but it is not
        // the defect this test guards, so it must not be waited out as "stuck".
        if (!failed.isEmpty()) {
            logger.warn("History contains generations the app reports as failed: {}", failed);
        }

        if (unsettled.isEmpty()) {
            dashboard.takeScreenshot("TC_DEF_007_All_Settled");
            logger.info("TC_DEF_007 PASSED: all {} history rows are settled.", rows);
            return;
        }

        // Something is in flight. Give it the full budget before calling it stuck.
        int stuckIndex = -1;
        String stuckStatus = "";
        for (int i = 0; i < rows; i++) {
            if (!DashboardPage.isInFlightStatus(dashboard.getHistoryEntryStatus(i))) {
                continue;
            }
            stuckStatus = dashboard.getHistoryEntryStatus(i);
            if (!dashboard.waitForHistoryEntryToSettle(i, SETTLE_TIMEOUT_MS, POLL_MS)) {
                stuckIndex = i;
                break;
            }
        }

        dashboard.takeScreenshot("TC_DEF_007_History_Status");

        Assert.assertEquals(stuckIndex, -1, String.format(
                "DEF-007: history row #%d (%s) was still '%s' after %d minutes. "
                        + "Unfinished rows at start: %s",
                stuckIndex,
                stuckIndex >= 0 ? dashboard.getHistoryEntryTag(stuckIndex) : "?",
                stuckStatus, SETTLE_TIMEOUT_MS / 60_000, unsettled));

        logger.info("TC_DEF_007 PASSED: every in-flight history row settled.");
    }

    // ----------------------------------------------------------------
    // TC_DEF_007b - A generated video settles in History
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_DEFECTS, AppConstants.GROUP_GENERATION},
            description = "A freshly generated video leaves the processing state in History"
    )
    @Story("Background processing")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Covers DEF-007 end to end. SPENDS 125 CREDITS: generates a video, then "
            + "follows its History row until it settles or the budget expires.")
    public void TC_DEF_007b_generatedVideoSettlesInHistory() {
        AiToolPage video = new AiToolPage(AiTool.VIDEO).open();

        int before = video.getCreditBalance();
        if (before < AiTool.VIDEO.creditCost()) {
            throw new SkipException(String.format(
                    "Balance %d is below AI Video's cost of %d.",
                    before, AiTool.VIDEO.creditCost()));
        }

        video.enterPrompt(AiTool.VIDEO.samplePrompt()).clickGenerate();
        Assert.assertTrue(video.confirmChargeIfShown(),
                "AI Video costs " + AiTool.VIDEO.creditCost()
                        + " credits and should have asked for confirmation.");

        boolean produced = video.waitForGeneratedMedia(GENERATION_TIMEOUT_MS);
        Assert.assertFalse(video.isAuthModalShown(),
                "AI Video asked us to sign in — the session lapsed.");
        Assert.assertFalse(video.isPaywallShown(),
                "AI Video raised the upgrade dialog instead of generating.");

        DashboardPage dashboard = new DashboardPage();
        dashboard.openHistory();

        int index = dashboard.indexOfFirstEntryWithTag("Video");
        Assert.assertTrue(index >= 0,
                "The generated video should appear in History. Tags found: "
                        + dashboard.getHistoryEntryTags());

        String status = dashboard.getHistoryEntryStatus(index);
        logger.info("Newest video history row is '{}' (in-page result produced: {}).",
                status.isEmpty() ? "settled" : status, produced);

        boolean settled = dashboard.waitForHistoryEntryToSettle(
                index, SETTLE_TIMEOUT_MS, POLL_MS);

        dashboard.takeScreenshot("TC_DEF_007b_Video_History");

        Assert.assertTrue(settled, String.format(
                "DEF-007: the generated video was still '%s' in History after %d minutes.",
                dashboard.getHistoryEntryStatus(index), SETTLE_TIMEOUT_MS / 60_000));

        logger.info("TC_DEF_007b PASSED: the generated video settled in History.");
    }
}
