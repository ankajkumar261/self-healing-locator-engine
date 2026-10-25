package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.HelpCenterPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * HelpCenterTest - regression cover for the two reported Help Center defects.
 *
 *   DEF-002  Only the Billing & Credits section works; the others do not.
 *   DEF-003  Help Center search does not work.
 *
 * The Help Center renders signed out, so this extends {@link BaseTest} and
 * never authenticates — which also keeps it clear of the staging sign-in rate
 * limit that forced {@link SingleSessionTest} to exist.
 *
 * Every topic is exercised by clicking its card, not by navigating to its URL.
 * Direct navigation renders these pages fine; the defect is in the click path,
 * so a test that skipped the click would pass while the bug was live.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("Help Center")
public class HelpCenterTest extends BaseTest {

    private HelpCenterPage helpCenter;

    @BeforeMethod(alwaysRun = true)
    public void initPages() {
        helpCenter = new HelpCenterPage();
    }

    /** Every topic card, and where each should land. */
    @DataProvider(name = "topics")
    public Object[][] topics() {
        return new Object[][]{
                {"Getting started",    "/help-center/getting-started"},
                {"Account & profile",  "/help-center/account"},
                // The one topic that deliberately leaves the Help Center.
                {"Billing & credits",  AppConstants.BILLING_PATH},
                {"Generation modules", "/help-center/generation"},
                {"Editors",            "/help-center/editors"},
                {"Troubleshooting",    "/help-center/troubleshooting"},
        };
    }

    // ----------------------------------------------------------------
    // TC_HELP_001 - The Help Center lists all six topics
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_HELP},
            description = "The Help Center renders its search box, all six topic cards and the FAQ"
    )
    @Story("Help Center landing")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_HELP_001_landingRendersAllSections() {
        helpCenter.open();

        Assert.assertTrue(helpCenter.isPageLoaded(), "Help Center should load.");
        Assert.assertTrue(helpCenter.isSearchVisible(), "Search box should be present.");

        Assert.assertEquals(helpCenter.getTopicCardCount(), HelpCenterPage.EXPECTED_TOPICS.size(),
                "Help Center should show all six topic cards, got: "
                        + helpCenter.getTopicCardTitles());
        Assert.assertEquals(helpCenter.getTopicCardTitles(), HelpCenterPage.EXPECTED_TOPICS,
                "Topic cards should match the shipped content config.");
        Assert.assertTrue(helpCenter.getFaqCount() > 0, "FAQ list should not be empty.");

        helpCenter.takeScreenshot("TC_HELP_001_Landing");
        logger.info("TC_HELP_001 PASSED: {} topics, {} FAQs.",
                helpCenter.getTopicCardCount(), helpCenter.getFaqCount());
    }

    // ----------------------------------------------------------------
    // TC_DEF_003 - Every topic card navigates, not just Billing
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "topics",
            groups = {AppConstants.GROUP_HELP, AppConstants.GROUP_DEFECTS},
            description = "Clicking any topic card opens a page with real content"
    )
    @Story("Topic navigation")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Covers DEF-002. Clicks each topic card in turn and asserts the URL changed "
            + "and the destination rendered content. Billing is expected to leave the Help "
            + "Center for /credits-billing; the other five open their own topic page.")
    public void TC_DEF_003_everyTopicCardOpens(String title, String expectedPath) {
        helpCenter.open();

        Assert.assertEquals(helpCenter.getTopicCardLink(title), expectedPath,
                "Topic card '" + title + "' should link to " + expectedPath);

        helpCenter.clickTopicCard(title);

        String url = helpCenter.getCurrentUrl();
        Assert.assertTrue(url.contains(expectedPath), String.format(
                "DEF-002: clicking '%s' should have opened %s but the URL is %s.",
                title, expectedPath, url));

        // Billing lands on the standalone billing page, which has no article list.
        if (!expectedPath.equals(AppConstants.BILLING_PATH)) {
            Assert.assertTrue(helpCenter.isTopicPageRendered(), String.format(
                    "DEF-002: '%s' opened %s but rendered no articles — "
                            + "title was '%s', article count %d.",
                    title, url, helpCenter.getPageTitleText(), helpCenter.getArticleCount()));
            // The <h1> is not the plain title: the page renders the first word,
            // then the rest as an accent span — and for a single-word title
            // there is no rest, so it substitutes the literal "help"
            // ("Editors" -> "Editors help"). The breadcrumb is the only place
            // the exact topic name appears, so that is what gets the equality.
            Assert.assertTrue(helpCenter.getPageTitleText().startsWith(title.split(" ")[0]),
                    String.format("The heading '%s' should open with the topic name '%s'.",
                            helpCenter.getPageTitleText(), title));
            Assert.assertEquals(helpCenter.getBreadcrumb(), "Help Center/" + title,
                    "The breadcrumb should name the topic that was clicked.");
        }

        helpCenter.takeScreenshot("TC_DEF_003_" + title.replaceAll("[^A-Za-z]", "_"));
        logger.info("TC_DEF_003 PASSED: '{}' opened {}.", title, url);
    }

    // ----------------------------------------------------------------
    // TC_DEF_003b - Topic articles expand
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_HELP, AppConstants.GROUP_DEFECTS},
            description = "Articles on a topic page expand to reveal their body"
    )
    @Story("Topic navigation")
    @Severity(SeverityLevel.NORMAL)
    @Description("A topic page that lists articles but cannot open them is still broken "
            + "from the user's side, so the accordion is asserted separately.")
    public void TC_DEF_003b_topicArticlesExpand() {
        helpCenter.open().clickTopicCard("Getting started");

        int articles = helpCenter.getArticleCount();
        Assert.assertTrue(articles > 0,
                "The 'Getting started' topic should list articles.");

        int before = helpCenter.getArticleBodyCount();
        helpCenter.expandArticle(0);
        int after = helpCenter.getArticleBodyCount();

        Assert.assertTrue(after > before, String.format(
                "Expanding the first article should reveal its body (bodies %d -> %d).",
                before, after));

        helpCenter.takeScreenshot("TC_DEF_003b_Article_Expanded");
        logger.info("TC_DEF_003b PASSED: {} articles, body opened.", articles);
    }

    // ----------------------------------------------------------------
    // TC_DEF_004 - Search returns grouped results
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_HELP, AppConstants.GROUP_DEFECTS},
            description = "Searching filters the Help Center into topic, article and FAQ hits"
    )
    @Story("Help Center search")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Covers DEF-003. 'credits' is a term the shipped content matches across all "
            + "three buckets, so a working search must report a non-zero count and group it.")
    public void TC_DEF_004_searchReturnsResults() {
        helpCenter.open().search("credits");

        Assert.assertEquals(helpCenter.getSearchValue(), "credits",
                "The search box should hold what was typed.");

        String heading = helpCenter.getResultsHeading();
        Assert.assertFalse(heading.isEmpty(), String.format(
                "DEF-003: searching 'credits' produced no results banner at all "
                        + "(no-results state shown: %s).", helpCenter.isNoResultsShown()));

        int count = helpCenter.getResultsCount();
        Assert.assertTrue(count > 0, String.format(
                "DEF-003: searching 'credits' should match the shipped help content "
                        + "but the page reported '%s'.", heading));
        Assert.assertFalse(helpCenter.isNoResultsShown(),
                "DEF-003: 'credits' matches shipped content, so the empty state is wrong.");

        Assert.assertTrue(helpCenter.getArticleResultCount() > 0,
                "Searching 'credits' should surface article hits, got none. "
                        + "Groups shown: " + helpCenter.getResultGroupLabels());

        helpCenter.takeScreenshot("TC_DEF_004_Search_Results");
        logger.info("TC_DEF_004 PASSED: '{}' with {} article hits.",
                heading, helpCenter.getArticleResultCount());
    }

    // ----------------------------------------------------------------
    // TC_DEF_004b - Search narrows, and a nonsense term finds nothing
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_HELP, AppConstants.GROUP_DEFECTS},
            description = "Search narrows on a second term and reports honestly when nothing matches"
    )
    @Story("Help Center search")
    @Severity(SeverityLevel.NORMAL)
    @Description("A search box that returns everything regardless of input looks like it "
            + "works. Narrowing and the empty state are what prove it is really filtering.")
    public void TC_DEF_004b_searchNarrowsAndHandlesMisses() {
        helpCenter.open();

        int broad = helpCenter.search("credits").getResultsCount();
        int narrow = helpCenter.search("video credits").getResultsCount();

        Assert.assertTrue(broad > 0, "Baseline search for 'credits' should match something.");
        Assert.assertTrue(narrow <= broad, String.format(
                "Adding a term should narrow results, not widen them (%d -> %d).",
                broad, narrow));

        // Every term must appear, so a deliberate nonsense word matches nothing.
        helpCenter.search("zzzqqxnotarealhelpterm");
        Assert.assertEquals(helpCenter.getResultsCount(), -1,
                "A nonsense query should not report a numeric result count.");
        Assert.assertTrue(helpCenter.isNoResultsShown(),
                "A nonsense query should show the no-results state, heading was: '"
                        + helpCenter.getResultsHeading() + "'.");

        // Clearing restores the browse view.
        helpCenter.clearSearch();
        Assert.assertEquals(helpCenter.getTopicCardCount(),
                HelpCenterPage.EXPECTED_TOPICS.size(),
                "Clearing the search should restore all six topic cards.");

        helpCenter.takeScreenshot("TC_DEF_004b_Search_Narrowing");
        logger.info("TC_DEF_004b PASSED: {} -> {} results, empty state and clear both work.",
                broad, narrow);
    }
}
