package com.portfolio.selfhealing.pages;

import com.microsoft.playwright.Locator;
import com.portfolio.selfhealing.base.BasePage;
import com.portfolio.selfhealing.utils.ConfigReader;
import io.qameta.allure.Step;

/**
 * DashboardPage - Page Object for the Self-Healing Locator Engine AI workspace (post-login).
 *
 * The app is a single-page app: signing in swaps the landing page for the
 * workspace without changing the URL, so never assert on a "/dashboard" path.
 *
 * Selectors below were taken from the live authenticated DOM.
 */
public class DashboardPage extends BasePage {

    // ---- Selectors (verified against live DOM) ----

    private static final String MAIN_CONTENT      = "main.main-content";
    private static final String DASHBOARD_HEADING = "main.main-content h1";
    private static final String SIDEBAR_NAV       = "aside.sidebar";
    private static final String SIDEBAR_ITEM      = "button.sidebar-item";
    private static final String AGENT_CARD        = "button.agent-card";

    // The avatar is a div with role=button in the sidebar footer.
    private static final String USER_AVATAR       = ".sidebar__avatar";
    private static final String PROFILE_MENU      = ".profile-popup";
    private static final String USER_NAME_DISPLAY = ".profile-popup__name";
    private static final String USER_EMAIL        = ".profile-popup__email";
    private static final String LOGOUT_BUTTON     =
            ".profile-popup__menu-item:has-text('Sign out')";

    // Signing out is confirmed first: the menu item only opens this dialog.
    // Involuntary sign-outs (session expiry) deliberately skip it.
    private static final String SIGNOUT_CONFIRM       = ".profile-popup__cancel-confirm";
    private static final String SIGNOUT_CONFIRM_CARD  = ".profile-popup__cancel-confirm-card";
    private static final String SIGNOUT_CONFIRM_TITLE = "#signout-confirm-title";
    private static final String SIGNOUT_CONFIRM_YES   =
            ".profile-popup__cancel-confirm-btn--primary";
    private static final String SIGNOUT_CONFIRM_STAY  =
            ".profile-popup__cancel-confirm-actions button:has-text('Stay signed in')";

    /**
     * Plan-activation modal. A free-tier account is greeted with this the moment
     * the workspace opens, and it covers the whole shell — including the avatar —
     * so anything that touches the workspace has to clear it first.
     */
    private static final String PLAN_MODAL        = ".pricing-modal";
    private static final String PLAN_MODAL_CLOSE  = ".pricing-modal__close";

    private static final String LOADER            = ".loader, .spinner";

    // ---- History panel (verified against live DOM) ----
    // Opened from the sidebar; the panel flies out over the workspace home.
    private static final String HISTORY_TRIGGER   =
            "button.sidebar-item[aria-label='History']";
    /** Root only carries --visible once the flyout has actually opened. */
    private static final String HISTORY_PANEL      = ".history-api--visible";
    private static final String HISTORY_TITLE_COUNT = ".history-api__title-count";
    private static final String HISTORY_CHIP        = ".history-api__chip";
    private static final String HISTORY_CHIP_ACTIVE = ".history-api__chip--active";
    private static final String HISTORY_ROW         = ".history-api__row";
    private static final String HISTORY_ROW_TITLE   = ".history-api__row-title";
    private static final String HISTORY_ROW_TAG     = ".history-api__row-tag";
    /**
     * Only rendered while a job is unfinished — the app hides this span once the
     * status reaches "succeeded"/"completed". Its presence is therefore the
     * signal that a generation has not settled, and its text is the raw status.
     */
    private static final String HISTORY_ROW_STATUS  = ".history-api__row-status";
    /** Settled empty-state; also shown transiently while the fetch is in flight. */
    private static final String HISTORY_EMPTY       =
            ".history-api__list:has-text('No history')";

    // Only rendered inside the History panel, not on the workspace home.
    private static final String SEARCH_BAR        = "input.history-api__search-input";

    // ---- Navigation Actions ----

    @Step("Wait for dashboard to fully load")
    public DashboardPage waitForDashboard() {
        // The sign-in round-trip regularly outlasts action.timeout (10s), so the
        // first wait after login gets the navigation budget instead.
        waitForVisible(MAIN_CONTENT, ConfigReader.getInstance().getNavigationTimeout());
        waitForHidden(LOADER);
        waitForNetworkIdle();
        dismissPlanModalIfShown();
        logger.info("Dashboard loaded successfully");
        return this;
    }

    /** True while the "active plan is required" modal is covering the workspace. */
    public boolean isPlanModalShown() {
        return isVisible(PLAN_MODAL);
    }

    /**
     * Closes the plan-activation modal if a free-tier account raised it.
     * <p>
     * Only the modal's own close control is clicked — never one of its plan
     * buttons, which would start a real Stripe checkout.
     *
     * @return true if a modal was actually dismissed.
     */
    @Step("Dismiss the plan-activation modal if shown")
    public boolean dismissPlanModalIfShown() {
        if (!isVisible(PLAN_MODAL)) {
            return false;
        }
        click(PLAN_MODAL_CLOSE);
        waitForHidden(PLAN_MODAL);
        logger.info("Dismissed the plan-activation modal (free-tier account).");
        return true;
    }

    /**
     * Opens the profile popup, or leaves it open if it already is.
     * <p>
     * The avatar is a toggle, so clicking it unconditionally closes a popup that
     * was already showing — and the wait then times out on an element the click
     * itself dismissed. Callers that open the menu twice (read a value, then act
     * on it) depend on this being idempotent.
     */
    @Step("Click user avatar / profile menu")
    public DashboardPage openProfileMenu() {
        if (!isVisible(PROFILE_MENU)) {
            click(USER_AVATAR);
            waitForVisible(PROFILE_MENU);
            logger.info("Opened profile menu");
        }
        return this;
    }

    /**
     * Signs out, all the way through the confirmation dialog.
     * <p>
     * The menu must be open first — "Sign out" only exists inside the profile
     * popup, so a failure to open it cannot be swallowed. The menu item itself
     * only <em>raises</em> the confirm dialog; without the second click the
     * session is still live, which reads as "logout silently did nothing".
     */
    @Step("Sign out")
    public void logout() {
        openSignOutConfirm();
        confirmSignOut();
        logger.info("Signed out successfully");
    }

    /** Opens the profile menu and clicks "Sign out", raising the confirm dialog. */
    @Step("Click Sign out (raises the confirmation dialog)")
    public DashboardPage openSignOutConfirm() {
        openProfileMenu();
        click(LOGOUT_BUTTON);
        return this;
    }

    /** True while the "Sign out?" confirmation dialog is on screen. */
    public boolean isSignOutConfirmShown() {
        return isVisible(SIGNOUT_CONFIRM_CARD);
    }

    public String getSignOutConfirmTitle() {
        return isVisible(SIGNOUT_CONFIRM_TITLE) ? getText(SIGNOUT_CONFIRM_TITLE).trim() : "";
    }

    /** The dialog is an alertdialog; the role is part of the contract, not decoration. */
    public String getSignOutConfirmRole() {
        return isVisible(SIGNOUT_CONFIRM_CARD)
                ? getAttribute(SIGNOUT_CONFIRM_CARD, "role")
                : "";
    }

    public boolean isStaySignedInVisible() {
        return isVisible(SIGNOUT_CONFIRM_STAY);
    }

    /** Backs out of the dialog via "Stay signed in". The session must survive this. */
    @Step("Cancel sign-out via 'Stay signed in'")
    public DashboardPage cancelSignOut() {
        click(SIGNOUT_CONFIRM_STAY);
        waitForHidden(SIGNOUT_CONFIRM);
        logger.info("Cancelled sign-out; session kept");
        return this;
    }

    @Step("Confirm sign-out via 'Yes, sign out'")
    public void confirmSignOut() {
        click(SIGNOUT_CONFIRM_YES);
        waitForNetworkIdle();
    }

    @Step("Search history for: {query}")
    public DashboardPage search(String query) {
        fill(SEARCH_BAR, query);
        pressKey(SEARCH_BAR, "Enter");
        waitForNetworkIdle();
        logger.info("Searched for: {}", query);
        return this;
    }

    // ---- History panel ----

    /**
     * Opens the History flyout and waits for it to actually be visible.
     * <p>
     * The panel root only gains {@code --visible} once the flyout has opened, so
     * that is the wait — the sidebar button existing is not proof the panel did.
     */
    @Step("Open the History panel")
    public DashboardPage openHistory() {
        if (!isVisible(HISTORY_PANEL)) {
            click(HISTORY_TRIGGER);
            waitForVisible(HISTORY_PANEL);
        }
        waitForHistoryLoaded();
        logger.info("Opened History panel");
        return this;
    }

    /**
     * Waits for the history list to finish loading.
     * <p>
     * The panel opens instantly but its entries are fetched asynchronously — the
     * list shows a "No history yet" placeholder until the request resolves. A test
     * that reads the list the moment the panel appears sees zero rows and a missing
     * header count even on an account that has history. This waits for the fetch to
     * settle: rows appearing, or the empty-state persisting past the initial load.
     */
    @Step("Wait for history to finish loading")
    private void waitForHistoryLoaded() {
        waitForNetworkIdle();
        long start = System.currentTimeMillis();
        long deadline = start + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (page.locator(HISTORY_ROW).count() > 0) {
                return;
            }
            // Only trust the empty-state once the fetch has had time to return,
            // since the same placeholder flashes while the request is in flight.
            if (System.currentTimeMillis() - start > 2_000 && isVisible(HISTORY_EMPTY)) {
                logger.info("History loaded with no entries.");
                return;
            }
            page.waitForTimeout(400);
        }
        logger.info("History load did not produce rows within timeout; treating as empty.");
    }

    public boolean isHistoryPanelVisible() {
        return isVisible(HISTORY_PANEL);
    }

    /** Closes the History flyout if it is open (it can overlay a result screen). */
    @Step("Close the History panel")
    public DashboardPage closeHistoryIfOpen() {
        if (isVisible(HISTORY_PANEL)) {
            page.locator(".history-api__header-btn[aria-label='Close'], "
                    + ".history-api__header-btn[title='Close']").first().click();
            waitForHidden(HISTORY_PANEL);
            logger.info("Closed History panel");
        }
        return this;
    }

    /** The count the header advertises next to the title, or -1 if absent. */
    public int getHistoryHeaderCount() {
        if (!isVisible(HISTORY_TITLE_COUNT)) {
            return -1;
        }
        String raw = getText(HISTORY_TITLE_COUNT).replaceAll("[^0-9]", "");
        return raw.isEmpty() ? -1 : Integer.parseInt(raw);
    }

    /** Indexes of every history row with the given tag that is worth opening. */
    public java.util.List<Integer> indexesOfEntriesWithTag(String tag) {
        java.util.List<String> tags = getHistoryEntryTags();
        java.util.List<Integer> found = new java.util.ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            if (!tags.get(i).equalsIgnoreCase(tag)) {
                continue;
            }
            String status = getHistoryEntryStatus(i).trim().toLowerCase();
            if (!status.equals("failed") && !status.equals("error")) {
                found.add(i);
            }
        }
        return found;
    }

    /** Number of history rows currently rendered in the list. */
    public int getHistoryEntryCount() {
        return page.locator(HISTORY_ROW).count();
    }

    /** The tool tags of the rendered rows, in order, e.g. [Document, Chat, Chat]. */
    public java.util.List<String> getHistoryEntryTags() {
        return page.locator(HISTORY_ROW + " " + HISTORY_ROW_TAG)
                .allTextContents().stream().map(String::trim).toList();
    }

    /** The visible filter chip labels, e.g. [All, Chat, Image, ...] with counts stripped. */
    public java.util.List<String> getHistoryFilterLabels() {
        return page.locator(HISTORY_CHIP).allTextContents().stream()
                .map(s -> s.replaceAll("\\d", "").trim())
                .filter(s -> !s.isEmpty()).toList();
    }

    public String getActiveHistoryFilter() {
        return isVisible(HISTORY_CHIP_ACTIVE)
                ? getText(HISTORY_CHIP_ACTIVE).replaceAll("\\d", "").trim()
                : "";
    }

    @Step("Filter history by: {label}")
    public DashboardPage clickHistoryFilter(String label) {
        page.locator(HISTORY_CHIP)
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(label))
                .first().click();
        // The list clears and re-renders for the new filter; wait for it to settle
        // so a read cannot catch the momentarily empty list mid-render.
        waitForHistoryLoaded();
        logger.info("Filtered history by: {}", label);
        return this;
    }

    public String getHistoryEntryTitle(int index) {
        return page.locator(HISTORY_ROW).nth(index)
                .locator(HISTORY_ROW_TITLE).textContent().trim();
    }

    public String getHistoryEntryTag(int index) {
        return page.locator(HISTORY_ROW).nth(index)
                .locator(HISTORY_ROW_TAG).textContent().trim();
    }

    /**
     * Opens the history row at {@code index} by clicking its title.
     * <p>
     * The title is clicked rather than the row so the click can never land on the
     * row's Delete button and destroy the entry mid-test.
     */
    /**
     * Where each history tag opens. Not simply "/" + tag: the media tools open a
     * dedicated viewer for the saved item rather than the generator page, and
     * Design opens the editor.
     */
    private static final java.util.Map<String, String> ENTRY_ROUTES = java.util.Map.of(
            "video",    "/video/history",
            "image",    "/image/history",
            "music",    "/music/history",
            "slides",   "/slides/history",
            "document", "/document",
            "design",   "/design/editor",
            "chat",     "/chat");

    /** The route a history entry with this tag is expected to open. */
    public static String routeForEntryTag(String tag) {
        return ENTRY_ROUTES.getOrDefault(tag.trim().toLowerCase(),
                "/" + tag.trim().toLowerCase());
    }

    /**
     * Opens the history row at {@code index} and waits for the route to change.
     * <p>
     * The app deliberately holds the URL until the destination's chunk has
     * downloaded — it navigates only after an await — so reading the URL straight
     * after the click races that and sees the previous route. The wait is on the
     * URL actually changing, not on a fixed pause.
     */
    @Step("Open history entry #{index}")
    public DashboardPage openHistoryEntry(int index) {
        String title = getHistoryEntryTitle(index);
        String before = getCurrentUrl();

        page.locator(HISTORY_ROW).nth(index).locator(HISTORY_ROW_TITLE).click();

        long deadline = System.currentTimeMillis() + ENTRY_OPEN_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && getCurrentUrl().equals(before)) {
            page.waitForTimeout(250);
        }
        waitForNetworkIdle();
        logger.info("Opened history entry #{}: '{}' -> {}", index, title, getCurrentUrl());
        return this;
    }

    /** Budget for the route change, which waits on a code-split chunk download. */
    private static final int ENTRY_OPEN_TIMEOUT_MS = 20_000;

    /**
     * The unfinished-status text of row {@code index} ("processing", "queued",
     * ...), or "" once the row has settled and the app drops the badge.
     */
    public String getHistoryEntryStatus(int index) {
        Locator status = page.locator(HISTORY_ROW).nth(index).locator(HISTORY_ROW_STATUS);
        return status.count() == 0 ? "" : status.first().textContent().trim();
    }

    /**
     * Statuses a job never leaves. "failed" is as finished as "succeeded" — the
     * job is over, it just ended badly. Treating it as unfinished makes a
     * stuck-job check wait out its whole budget on a row that was never going to
     * move, then report a permanent failure as though it were still processing.
     */
    private static final java.util.Set<String> TERMINAL_STATUSES = java.util.Set.of(
            "failed", "error", "cancelled", "canceled", "succeeded", "completed");

    /** True while a status means "still working", as opposed to finished or absent. */
    public static boolean isInFlightStatus(String status) {
        return !status.isEmpty()
                && !TERMINAL_STATUSES.contains(status.trim().toLowerCase());
    }

    /** Rows still genuinely in flight, as "tag: status" pairs. */
    public java.util.List<String> getUnsettledHistoryEntries() {
        java.util.List<String> unsettled = new java.util.ArrayList<>();
        int rows = getHistoryEntryCount();
        for (int i = 0; i < rows; i++) {
            if (isInFlightStatus(getHistoryEntryStatus(i))) {
                unsettled.add(getHistoryEntryTag(i) + ": " + getHistoryEntryStatus(i));
            }
        }
        return unsettled;
    }

    /**
     * Rows that finished in a failure state. Distinct from unsettled rows: these
     * are the app reporting a generation that did not work, which is worth
     * surfacing but is not the "stuck in processing" defect.
     */
    public java.util.List<String> getFailedHistoryEntries() {
        java.util.List<String> failed = new java.util.ArrayList<>();
        int rows = getHistoryEntryCount();
        for (int i = 0; i < rows; i++) {
            String status = getHistoryEntryStatus(i).trim().toLowerCase();
            if (status.equals("failed") || status.equals("error")) {
                failed.add(getHistoryEntryTag(i) + ": " + getHistoryEntryStatus(i));
            }
        }
        return failed;
    }

    /**
     * Re-opens History every {@code pollMs} until row {@code index} settles.
     * <p>
     * The panel caches its fetch, so the list is closed and re-opened rather than
     * re-read in place — otherwise a stuck row and a stale render are
     * indistinguishable, and the test would report a defect that isn't there.
     *
     * @return true if the row settled within the budget.
     */
    @Step("Wait for history entry #{index} to leave the processing state")
    public boolean waitForHistoryEntryToSettle(int index, int timeoutMs, int pollMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String last = getHistoryEntryStatus(index);
        while (System.currentTimeMillis() < deadline) {
            if (!isInFlightStatus(last)) {
                logger.info("History entry #{} settled{}.", index,
                        last.isEmpty() ? "" : " as '" + last + "'");
                return true;
            }
            logger.info("History entry #{} still '{}'; re-checking in {}ms.",
                    index, last, pollMs);
            page.waitForTimeout(pollMs);
            closeHistoryIfOpen();
            openHistory();
            last = getHistoryEntryStatus(index);
        }
        logger.warn("History entry #{} stuck at '{}' after {}ms.", index, last, timeoutMs);
        return false;
    }

    /**
     * Index of the first history row with the given tool tag that is worth
     * opening, or -1 if none.
     * <p>
     * Rows the app marks failed are skipped: a generation that failed has no
     * result screen to open, so selecting one makes a redirect test fail on
     * account state rather than on the redirect it means to check.
     */
    public int indexOfFirstEntryWithTag(String tag) {
        java.util.List<String> tags = getHistoryEntryTags();
        int firstFailed = -1;
        for (int i = 0; i < tags.size(); i++) {
            if (!tags.get(i).equalsIgnoreCase(tag)) {
                continue;
            }
            String status = getHistoryEntryStatus(i).trim().toLowerCase();
            if (status.equals("failed") || status.equals("error")) {
                if (firstFailed < 0) {
                    firstFailed = i;
                }
                continue;
            }
            return i;
        }
        if (firstFailed >= 0) {
            logger.info("Every '{}' history row is in a failed state (first at #{}).",
                    tag, firstFailed);
        }
        return -1;
    }

    /**
     * Opens the first history entry of the given tag (e.g. "Design").
     * Returns false if the account has no such entry.
     */
    @Step("Open first '{tag}' history entry")
    public boolean openFirstHistoryEntryWithTag(String tag) {
        int i = indexOfFirstEntryWithTag(tag);
        if (i < 0) {
            logger.info("No '{}' entry found in history.", tag);
            return false;
        }
        openHistoryEntry(i);
        return true;
    }

    /** Sidebar items are buttons labelled via aria-label: New, Home, History, Settings. */
    @Step("Navigate to sidebar item: {itemText}")
    public DashboardPage clickSidebarItem(String itemText) {
        click(String.format("%s[aria-label='%s']", SIDEBAR_ITEM, itemText));
        waitForNetworkIdle();
        logger.info("Clicked sidebar item: {}", itemText);
        return this;
    }

    /** Agent cards are buttons labelled via aria-label: AI Chat, AI Video, AI Design, ... */
    @Step("Open agent: {agentName}")
    public DashboardPage clickAgentCard(String agentName) {
        click(String.format("%s[aria-label='%s']", AGENT_CARD, agentName));
        waitForNetworkIdle();
        logger.info("Opened agent card: {}", agentName);
        return this;
    }

    // ---- Validations ----

    @Step("Verify dashboard is loaded")
    @Override
    public boolean isPageLoaded() {
        boolean loaded = isVisible(MAIN_CONTENT) && isVisible(SIDEBAR_NAV);
        logger.info("Dashboard page loaded: {}", loaded);
        return loaded;
    }

    @Step("Get dashboard page title/heading")
    public String getDashboardHeading() {
        if (isVisible(DASHBOARD_HEADING)) {
            return getText(DASHBOARD_HEADING);
        }
        return "";
    }

    @Step("Check if user is logged in (avatar visible)")
    public boolean isUserLoggedIn() {
        return isVisible(USER_AVATAR);
    }

    /** Name lives inside the profile popup, so the menu is opened to read it. */
    @Step("Get displayed user name")
    public String getDisplayedUserName() {
        openProfileMenu();
        return isVisible(USER_NAME_DISPLAY) ? getText(USER_NAME_DISPLAY) : "";
    }

    @Step("Get displayed user email")
    public String getDisplayedUserEmail() {
        openProfileMenu();
        return isVisible(USER_EMAIL) ? getText(USER_EMAIL) : "";
    }

    @Step("Check if sidebar navigation is visible")
    public boolean isSidebarVisible() {
        return isVisible(SIDEBAR_NAV);
    }

    @Step("Check if search bar is visible")
    public boolean isSearchBarVisible() {
        return isVisible(SEARCH_BAR);
    }

    @Step("Get current page URL")
    public String getDashboardUrl() {
        return getCurrentUrl();
    }

    @Step("Check if loader is hidden (page fully loaded)")
    public boolean isLoaderHidden() {
        return !isVisible(LOADER);
    }

    @Step("Check if main content area is visible")
    public boolean isMainContentVisible() {
        return isVisible(MAIN_CONTENT);
    }
}
