package com.portfolio.selfhealing.pages;

import com.portfolio.selfhealing.base.BasePage;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.utils.ConfigReader;
import io.qameta.allure.Step;

/**
 * HelpCenterPage - Page Object for /help-center and /help-center/:topicId.
 *
 * The Help Center is a public route: it renders signed out, so these tests need
 * no session. It has three parts, each reported broken at least once:
 *
 *   - a topic card grid, each card linking to its own topic page
 *   - a live search box filtering topics, articles and FAQs as you type
 *   - an FAQ accordion
 *
 * One topic is different by design: "Billing & credits" carries an explicit
 * link to /credits-billing, while every other card falls back to
 * /help-center/&lt;id&gt;. That asymmetry is exactly the shape of the reported
 * "only Billing works" defect, so it is asserted explicitly rather than
 * papered over.
 *
 * Selectors below were taken from the live DOM and the app's own source.
 */
public class HelpCenterPage extends BasePage {

    // ---- Selectors ----

    private static final String PAGE_ROOT     = ".privacy-page";
    private static final String PAGE_TITLE    = ".privacy-page__title";
    private static final String EYEBROW       = ".privacy-page__eyebrow";

    private static final String SEARCH_INPUT  = ".help-search__input";
    private static final String SEARCH_CLEAR  = ".help-search__clear";
    private static final String SECTION_HEAD  = ".help-page__section-heading";
    private static final String RESULT_LABEL  = ".help-results__label";
    private static final String RESULT_ITEM   = ".help-result";
    private static final String RESULT_TITLE  = ".help-result__title";
    private static final String SEARCH_EMPTY  = ".help-faq__empty";

    private static final String TOPIC_CARD    = "a.help-card";
    private static final String CARD_TITLE    = ".help-card__title";
    private static final String CARD_META     = ".help-card__meta";

    private static final String FAQ_QUESTION  = ".help-faq__q";
    private static final String FAQ_ANSWER    = ".help-faq__a";

    private static final String BREADCRUMB    = ".help-breadcrumb";
    private static final String ARTICLE_Q     = ".help-article__q";
    private static final String ARTICLE_BODY  = ".help-article__body";
    private static final String CONTACT       = ".help-contact";

    /** The six top-level topics the content config ships. */
    public static final java.util.List<String> EXPECTED_TOPICS = java.util.List.of(
            "Getting started",
            "Account & profile",
            "Billing & credits",
            "Generation modules",
            "Editors",
            "Troubleshooting");

    // ---- Navigation ----

    /**
     * Opens the Help Center, reloading once if the first attempt never paints.
     * <p>
     * This is a public marketing-side route whose first load intermittently
     * overruns even the navigation budget. One reload is cheap and turns a
     * transient stall into a pass; a second failure is reported honestly.
     */
    @Step("Open the Help Center")
    public HelpCenterPage open() {
        String baseUrl = ConfigReader.getInstance().getBaseUrl().replaceAll("/$", "");
        String url = baseUrl + AppConstants.HELP_CENTER_PATH;
        double timeout = ConfigReader.getInstance().getNavigationTimeout();

        navigateTo(url);
        try {
            waitForVisible(SEARCH_INPUT, timeout);
        } catch (com.microsoft.playwright.TimeoutError first) {
            logger.warn("Help Center did not paint within {}ms; reloading once.", timeout);
            navigateTo(url);
            waitForVisible(SEARCH_INPUT, timeout);
        }
        logger.info("Opened the Help Center");
        return this;
    }

    @Step("Open help topic directly: {topicId}")
    public HelpCenterPage openTopic(String topicId) {
        String baseUrl = ConfigReader.getInstance().getBaseUrl().replaceAll("/$", "");
        navigateTo(baseUrl + AppConstants.HELP_CENTER_PATH + "/" + topicId);
        waitForVisible(PAGE_TITLE, ConfigReader.getInstance().getNavigationTimeout());
        logger.info("Opened help topic: {}", topicId);
        return this;
    }

    // ---- Topic cards ----

    public int getTopicCardCount() {
        return page.locator(TOPIC_CARD).count();
    }

    public java.util.List<String> getTopicCardTitles() {
        return page.locator(TOPIC_CARD + " " + CARD_TITLE)
                .allTextContents().stream().map(String::trim).toList();
    }

    /** The href each card points at, in DOM order. */
    public java.util.List<String> getTopicCardLinks() {
        return page.locator(TOPIC_CARD).all().stream()
                .map(l -> l.getAttribute("href")).toList();
    }

    public String getTopicCardLink(String title) {
        return cardByTitle(title).getAttribute("href");
    }

    /** e.g. "6 articles" from the card footer. */
    public String getTopicCardMeta(String title) {
        return cardByTitle(title).locator(CARD_META).textContent().trim();
    }

    /**
     * Clicks a topic card by its visible title.
     * <p>
     * Clicked rather than navigated-to on purpose: the reported defect is that
     * the cards do not take you anywhere, and a direct {@code navigateTo} would
     * bypass the client-side routing that is the thing under test.
     */
    @Step("Click help topic card: {title}")
    public HelpCenterPage clickTopicCard(String title) {
        cardByTitle(title).click();
        waitForNetworkIdle();
        logger.info("Clicked help topic card: {}", title);
        return this;
    }

    private com.microsoft.playwright.Locator cardByTitle(String title) {
        return page.locator(TOPIC_CARD)
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(title))
                .first();
    }

    // ---- Search ----

    /**
     * Types into the search box the way a user does.
     * <p>
     * The field is a controlled React input filtering on every keystroke, so it
     * is filled and then nudged — there is no submit button and pressing Enter
     * does nothing.
     */
    @Step("Search the help center for: {query}")
    public HelpCenterPage search(String query) {
        click(SEARCH_INPUT);
        fill(SEARCH_INPUT, query);
        // Let the re-render land before anything reads the results.
        waitForMs(500);
        logger.info("Searched help center for: {}", query);
        return this;
    }

    @Step("Clear the help center search")
    public HelpCenterPage clearSearch() {
        if (isVisible(SEARCH_CLEAR)) {
            click(SEARCH_CLEAR);
            waitForMs(300);
        }
        return this;
    }

    public boolean isSearchVisible() {
        return isVisible(SEARCH_INPUT);
    }

    public String getSearchValue() {
        return getInputValue(SEARCH_INPUT);
    }

    /** The results banner, e.g. "37 results for “credits”" or "No results". */
    public String getResultsHeading() {
        return isVisible(SECTION_HEAD) ? getText(SECTION_HEAD).trim() : "";
    }

    /** The count the results banner advertises, or -1 when it shows none. */
    public int getResultsCount() {
        String heading = getResultsHeading();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)\\s+results?").matcher(heading);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** The group labels the results are bucketed under: Topics / Articles / Frequently asked. */
    public java.util.List<String> getResultGroupLabels() {
        return page.locator(RESULT_LABEL).allTextContents()
                .stream().map(String::trim).toList();
    }

    public int getArticleResultCount() {
        return page.locator(RESULT_ITEM).count();
    }

    public java.util.List<String> getArticleResultTitles() {
        return page.locator(RESULT_ITEM + " " + RESULT_TITLE)
                .allTextContents().stream().map(String::trim).toList();
    }

    public boolean isNoResultsShown() {
        return isVisible(SEARCH_EMPTY);
    }

    // ---- FAQ ----

    public int getFaqCount() {
        return page.locator(FAQ_QUESTION).count();
    }

    @Step("Expand FAQ #{index}")
    public HelpCenterPage expandFaq(int index) {
        page.locator(FAQ_QUESTION).nth(index).click();
        waitForMs(400);
        return this;
    }

    public int getVisibleFaqAnswerCount() {
        return page.locator(FAQ_ANSWER).count();
    }

    // ---- Topic page ----

    public String getPageTitleText() {
        return isVisible(PAGE_TITLE) ? getText(PAGE_TITLE).trim() : "";
    }

    /** On a topic page this reads e.g. "6 ARTICLES". */
    public String getEyebrow() {
        return isVisible(EYEBROW) ? getText(EYEBROW).trim() : "";
    }

    public String getBreadcrumb() {
        return isVisible(BREADCRUMB) ? getText(BREADCRUMB).trim() : "";
    }

    /** Number of collapsible articles rendered on a topic page. */
    public int getArticleCount() {
        return page.locator(ARTICLE_Q).count();
    }

    @Step("Expand article #{index}")
    public HelpCenterPage expandArticle(int index) {
        page.locator(ARTICLE_Q).nth(index).click();
        waitForMs(400);
        return this;
    }

    public int getArticleBodyCount() {
        return page.locator(ARTICLE_BODY).count();
    }

    public boolean isContactStripVisible() {
        return isVisible(CONTACT);
    }

    /**
     * True when a topic page actually rendered its content.
     * <p>
     * A blank shell still carries the page chrome, so the title alone proves
     * nothing — the article list is what separates a working topic from the
     * reported "section doesn't work".
     */
    public boolean isTopicPageRendered() {
        return isVisible(PAGE_TITLE)
                && !getPageTitleText().isEmpty()
                && getArticleCount() > 0;
    }

    @Override
    public boolean isPageLoaded() {
        return isVisible(PAGE_ROOT) && isVisible(SEARCH_INPUT);
    }
}
