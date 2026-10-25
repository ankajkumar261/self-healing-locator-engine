package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.DashboardPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * HistoryTest - coverage for the workspace History panel.
 *
 * The headline case is TC_HIST_003: a past generation in History must reopen on
 * its own tool screen. Verified against the live app — clicking a Chat entry
 * navigates to /chat and restores the conversation, a Document entry opens the
 * /document viewer, and so on. The entry's tag maps to the route as
 * {@code "/" + tag.toLowerCase()}.
 *
 * These tests read existing history; they never generate, so they spend no
 * credits. They do assume the account has at least one history entry — a fresh
 * account would legitimately have none, so the redirect test skips itself rather
 * than fail when the list is empty.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("History")
public class HistoryTest extends SingleSessionTest {

    private DashboardPage dashboard;

    // alwaysRun: testng.xml filters on groups, which would otherwise skip this
    // @BeforeMethod and leave the page object null.
    @BeforeMethod(alwaysRun = true)
    public void openWorkspace() {
        dashboard = new DashboardPage();
        dashboard.navigateTo(config.getBaseUrl());
        dashboard.waitForDashboard();
    }

    // ----------------------------------------------------------------
    // TC_HIST_001 - History panel opens and lists past work
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_HISTORY},
            description = "History panel opens from the sidebar and renders its entries"
    )
    @Story("History Panel")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Opening History shows the panel, a header count, and the tool filter chips.")
    public void TC_HIST_001_historyPanelOpens() {
        dashboard.openHistory();

        Assert.assertTrue(dashboard.isHistoryPanelVisible(),
                "History panel should be visible after clicking the sidebar History item.");
        Assert.assertTrue(dashboard.getHistoryFilterLabels().contains("All"),
                "History should offer an 'All' filter chip. Found: "
                        + dashboard.getHistoryFilterLabels());

        dashboard.takeScreenshot("TC_HIST_001_History_Panel");
        logger.info("TC_HIST_001 PASSED: History open with {} entries, filters {}.",
                dashboard.getHistoryEntryCount(), dashboard.getHistoryFilterLabels());
    }

    // ----------------------------------------------------------------
    // TC_HIST_002 - Header count agrees with the rendered rows
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_HISTORY},
            description = "The count beside the History title matches the rows rendered"
    )
    @Story("History Panel")
    @Severity(SeverityLevel.NORMAL)
    @Description("Guards against the header count and the list drifting apart under the 'All' filter.")
    public void TC_HIST_002_headerCountMatchesRows() {
        dashboard.openHistory();

        int header = dashboard.getHistoryHeaderCount();
        int rows = dashboard.getHistoryEntryCount();

        if (header < 0) {
            throw new org.testng.SkipException("History header shows no count to compare against.");
        }

        // The header and the list are populated from the same fetch but not in
        // the same frame, and a generation finishing elsewhere adds a row between
        // the two reads. One re-read settles that; a difference that survives it
        // is the counts genuinely disagreeing, which is what this guards.
        if (rows != header) {
            logger.info("Header {} and rows {} disagreed; re-reading once.", header, rows);
            dashboard.closeHistoryIfOpen().openHistory();
            header = dashboard.getHistoryHeaderCount();
            rows = dashboard.getHistoryEntryCount();
        }

        Assert.assertEquals(rows, header,
                "Rendered history rows (" + rows + ") should match the header count ("
                        + header + ") — they disagreed on two consecutive reads.");

        logger.info("TC_HIST_002 PASSED: header count {} matches {} rendered rows.", header, rows);
    }

    // ----------------------------------------------------------------
    // TC_HIST_003 - A history entry reopens on its own screen  (scenario #4)
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_HISTORY},
            description = "Clicking a history entry redirects to that entry's tool screen"
    )
    @Story("Redirect from History")
    @Severity(SeverityLevel.BLOCKER)
    @Description("The core requirement: opening a past item from History lands on the matching tool "
            + "route for that tag. Not simply '/' + tag: the media tools open a saved-item "
            + "viewer (/video/history) and Design opens the editor (/design/editor).")
    public void TC_HIST_003_entryRedirectsToItsScreen() {
        dashboard.openHistory();

        if (dashboard.getHistoryEntryCount() == 0) {
            throw new org.testng.SkipException(
                    "No history entries on this account; nothing to redirect to.");
        }

        String tag = dashboard.getHistoryEntryTag(0);
        String title = dashboard.getHistoryEntryTitle(0);
        String expectedRoute = DashboardPage.routeForEntryTag(tag);

        dashboard.openHistoryEntry(0);

        String url = dashboard.getCurrentUrl();
        Assert.assertTrue(url.contains(expectedRoute),
                "Opening the '" + tag + "' entry ('" + title + "') should redirect to "
                        + expectedRoute + ". Actual URL: " + url);

        dashboard.takeScreenshot("TC_HIST_003_Redirect_" + tag);
        logger.info("TC_HIST_003 PASSED: '{}' entry redirected to {}.", tag, url);
    }

    // ----------------------------------------------------------------
    // TC_HIST_004 - Filter chips narrow the list to one tool
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_HISTORY},
            description = "Selecting a tool filter shows only that tool's entries"
    )
    @Story("History Filtering")
    @Severity(SeverityLevel.NORMAL)
    @Description("Picking a specific chip (e.g. Chat) should leave only rows tagged with that tool.")
    public void TC_HIST_004_filterNarrowsToOneTool() {
        dashboard.openHistory();

        // Pick a tool that actually has entries so the assertion is meaningful.
        String target = dashboard.getHistoryEntryTags().stream()
                .findFirst().orElse(null);
        if (target == null) {
            throw new org.testng.SkipException("No history entries to filter.");
        }

        dashboard.clickHistoryFilter(target);

        java.util.List<String> tagsAfter = dashboard.getHistoryEntryTags();
        Assert.assertFalse(tagsAfter.isEmpty(),
                "Filtering by '" + target + "' should still show its entries.");
        Assert.assertTrue(tagsAfter.stream().allMatch(t -> t.equalsIgnoreCase(target)),
                "Every visible row should be a '" + target + "' entry after filtering. Saw: " + tagsAfter);

        dashboard.takeScreenshot("TC_HIST_004_Filter_" + target);
        logger.info("TC_HIST_004 PASSED: '{}' filter left only {} entries.", target, tagsAfter);
    }

    // ----------------------------------------------------------------
    // TC_HIST_005 - Search filters the history list
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_HISTORY},
            description = "Searching history narrows the list to matching entries"
    )
    @Story("History Search")
    @Severity(SeverityLevel.NORMAL)
    @Description("Typing a term from an existing entry into the history search should reduce the list.")
    public void TC_HIST_005_searchNarrowsList() {
        dashboard.openHistory();

        if (dashboard.getHistoryEntryCount() == 0) {
            throw new org.testng.SkipException("No history entries to search.");
        }

        // Use the first word of the newest entry's title as a term guaranteed to match.
        String title = dashboard.getHistoryEntryTitle(0);
        String term = title.split("\\s+")[0];
        int before = dashboard.getHistoryEntryCount();

        dashboard.search(term);

        int after = dashboard.getHistoryEntryCount();
        Assert.assertTrue(after >= 1,
                "Search for '" + term + "' should keep at least the entry it came from.");
        Assert.assertTrue(after <= before,
                "Search should not grow the list (before=" + before + ", after=" + after + ").");

        dashboard.takeScreenshot("TC_HIST_005_Search");
        logger.info("TC_HIST_005 PASSED: search '{}' narrowed {} -> {} entries.", term, before, after);
    }
}
