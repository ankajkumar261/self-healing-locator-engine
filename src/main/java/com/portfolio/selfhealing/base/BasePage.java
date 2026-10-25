package com.portfolio.selfhealing.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.selfhealing.healing.core.Intent;
import com.portfolio.selfhealing.healing.playwright.HealingLocator;
import com.portfolio.selfhealing.utils.PlaywrightManager;
import com.portfolio.selfhealing.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BasePage - Parent class for all Page Object classes.
 * Provides common Playwright interaction wrappers with built-in logging.
 * <p>
 * Selector-based helpers resolve through {@link HealingLocator}: the selector as
 * written is always tried first, and only one that matches nothing is replaced,
 * when a replacement scores at least healing.confidence.threshold. Boolean checks
 * (isVisible, isEnabled, isChecked) only reuse heals already approved, and
 * waitForHidden is never healed. See README "Self-healing locators".
 */
public abstract class BasePage {

    /** Cap on the best-effort network-idle settle; see waitForNetworkIdle. */
    private static final int NETWORK_IDLE_TIMEOUT_MS = 8_000;

    protected final Page page;
    protected final Logger logger = LogManager.getLogger(getClass());
    private final HealingLocator healing;

    public BasePage() {
        this.page = PlaywrightManager.getPage();
        this.healing = new HealingLocator(page, getClass());
    }

    // ---- Locator Resolution ----

    /**
     * Resolves a selector to a single Locator.
     * <p>
     * Page objects here use comma-separated fallback selectors (e.g.
     * "#email, input[type='email']") so they survive small DOM changes. Those
     * match every alternative at once, which makes Playwright's strict mode
     * throw before any action runs. Taking the first match keeps the fallback
     * style usable.
     */
    protected Locator locator(String selector) {
        return page.locator(selector).first();
    }

    // ---- Navigation ----

    /**
     * Navigates and waits for the document to be parsed.
     * <p>
     * Deliberately COMMIT — the navigation resolves as soon as the response
     * arrives, without waiting for any load event.
     * <p>
     * The landing page pulls third-party scripts (Google sign-in, Tag Manager)
     * that intermittently stall. Both LOAD and DOMCONTENTLOADED hang on that,
     * timing out after 60s on a page whose own HTML returned in under a second.
     * Readiness is proven by the explicit element waits every page object
     * already performs, so nothing is lost by not waiting for a load event —
     * and the suite stops failing on someone else's CDN.
     */
    public void navigateTo(String url) {
        logger.debug("Navigating to: {}", url);
        double navTimeout = com.portfolio.selfhealing.utils.ConfigReader.getInstance()
                .getNavigationTimeout();

        // With no SPA fallback on the host, a deep link can only be reached from
        // inside the running app. Route there directly rather than requesting the
        // URL first: the request is guaranteed to 404, and letting it happen would
        // tear down the tab that holds the session and force a reload every time.
        if (spaFallbackEnabled() && !isOriginRoot(url) && isAppLoaded(url)) {
            routeInPlace(url);
            return;
        }

        com.microsoft.playwright.Response response;
        try {
            response = page.navigate(url,
                    new Page.NavigateOptions()
                            .setTimeout(navTimeout)
                            .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT));
        } catch (com.microsoft.playwright.PlaywrightException e) {
            // Headed Chromium refuses an error response with an empty body outright
            // (net::ERR_HTTP_RESPONSE_CODE_FAILURE) rather than handing back a
            // Response to inspect, so the status check below never runs and the
            // same 404 that is merely reported in headless aborts the navigation
            // here. Both are the one condition, and both need the same handling.
            if (!isHttpErrorNavigation(e)) {
                throw e;
            }
            if (spaFallbackEnabled()) {
                navigateClientSide(url, HTTP_STATUS_UNKNOWN);
                return;
            }
            throw new IllegalStateException(String.format(
                    "%s could not be loaded: the server returned an HTTP error with no "
                            + "body, so no application was served. This is a server/routing "
                            + "problem, not a broken selector. To keep testing while it is "
                            + "fixed, set spa.fallback.navigation=true.", url), e);
        }

        // A 4xx/5xx serves no app, so every selector that follows is guaranteed to
        // miss — and each one then burns its full timeout before anyone learns the
        // page was never there. One suite run spent nearly an hour that way and
        // reported it as a wall of unrelated locator failures. Fail here instead,
        // naming the status, so the cause is the first thing in the report.
        if (response != null && response.status() >= 400) {
            if (spaFallbackEnabled()) {
                navigateClientSide(url, response.status());
                return;
            }
            throw new IllegalStateException(String.format(
                    "%s returned HTTP %d, so no application was served. Every locator "
                            + "on this page will time out. This is a server/routing "
                            + "problem, not a broken selector. To keep testing while it "
                            + "is fixed, set spa.fallback.navigation=true.",
                    url, response.status()));
        }
    }

    /** Used when the browser aborted before any status could be read. */
    private static final int HTTP_STATUS_UNKNOWN = -1;

    /** True when a navigation failed because the response carried an HTTP error. */
    private static boolean isHttpErrorNavigation(com.microsoft.playwright.PlaywrightException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        return message.contains("ERR_HTTP_RESPONSE_CODE_FAILURE")
                || message.contains("ERR_BAD_HTTP_RESPONSE");
    }

    private boolean spaFallbackEnabled() {
        return Boolean.parseBoolean(com.portfolio.selfhealing.utils.ConfigReader.getInstance()
                .getOrDefault("spa.fallback.navigation", "false"));
    }

    /**
     * Reaches a route the way a user inside the app does, when the server will
     * not serve it directly.
     * <p>
     * The host currently has no SPA fallback: only "/" is served and every deep
     * link returns an empty 404. The application is fine — routed to from inside,
     * the same pages render normally — so this loads the origin and then drives
     * the client-side router, which keeps the suite meaningful while the routing
     * defect is open.
     * <p>
     * This is a workaround, not a fix, and it is off by default: on it the suite
     * can no longer tell you that deep links are broken for real users.
     */
    private void navigateClientSide(String url, int status) {
        logger.warn("{} returned {} (no SPA fallback on the host). Reaching it through "
                        + "the running app instead.",
                url, status == HTTP_STATUS_UNKNOWN ? "an HTTP error" : "HTTP " + status);

        if (!isAppLoaded(url)) {
            java.net.URI uri = java.net.URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getAuthority();
            page.navigate(origin, new Page.NavigateOptions()
                    .setTimeout(com.portfolio.selfhealing.utils.ConfigReader.getInstance()
                            .getNavigationTimeout())
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        routeInPlace(url);
    }

    /** True when {@code url} points at the origin root, which the server does serve. */
    private static boolean isOriginRoot(String url) {
        String path = java.net.URI.create(url).getPath();
        return path == null || path.isEmpty() || path.equals("/");
    }

    /**
     * True when this tab already has the application running on the same origin.
     * <p>
     * If it does, a route change needs no page load at all — which is the whole
     * point: reloading would drop the signed-in session this tab is holding.
     */
    private boolean isAppLoaded(String url) {
        try {
            java.net.URI target = java.net.URI.create(url);
            String origin = target.getScheme() + "://" + target.getAuthority();
            if (!page.url().startsWith(origin)) {
                return false;
            }
            Object mounted = page.evaluate(
                    "() => { const r = document.getElementById('root');"
                    + " return !!(r && r.childElementCount > 0); }");
            return Boolean.TRUE.equals(mounted);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Changes route inside the already-loaded app, in this tab, without a reload.
     * <p>
     * pushState plus a popstate event is exactly what the client-side router
     * listens for, so this is the same transition the app's own links perform.
     * Everything therefore happens in one session and one tab — which is the only
     * way these routes can be reached at all while the host 404s deep links.
     */
    private void routeInPlace(String url) {
        java.net.URI uri = java.net.URI.create(url);
        String path = uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
        logger.debug("Routing in place to {}", path);
        page.evaluate("p => { window.history.pushState({}, '', p);"
                + " window.dispatchEvent(new PopStateEvent('popstate')); }", path);
    }

    public String getCurrentUrl() {
        return page.url();
    }

    public String getPageTitle() {
        return page.title();
    }

    // ---- Click Actions ----

    public void click(String selector) {
        logger.debug("Clicking: {}", selector);
        healing.resolve(selector, Intent.CLICK).click();
    }

    public void click(Locator locator) {
        logger.debug("Clicking locator");
        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        locator.click();
    }

    // ---- Input Actions ----

    public void fill(String selector, String value) {
        logger.debug("Filling '{}' with '{}'", selector, value);
        healing.resolve(selector, Intent.INPUT).fill(value);
    }

    public void clearAndFill(String selector, String value) {
        logger.debug("Clearing and filling '{}' with '{}'", selector, value);
        Locator field = healing.resolve(selector, Intent.INPUT);
        field.clear();
        field.fill(value);
    }

    public void pressKey(String selector, String key) {
        logger.debug("Pressing key '{}' on '{}'", key, selector);
        healing.resolve(selector, Intent.INPUT, WaitForSelectorState.ATTACHED, null).press(key);
    }

    // ---- Text / Value Getters ----

    public String getText(String selector) {
        return healing.resolve(selector, Intent.READ).textContent();
    }

    public String getInputValue(String selector) {
        return healing.resolve(selector, Intent.READ, WaitForSelectorState.ATTACHED, null).inputValue();
    }

    public String getAttribute(String selector, String attribute) {
        return healing.resolve(selector, Intent.READ, WaitForSelectorState.ATTACHED, null)
                .getAttribute(attribute);
    }

    // ---- Visibility / State Checks ----

    public boolean isVisible(String selector) {
        try {
            return healing.isVisible(selector);
        } catch (Exception e) {
            // Log it — silently returning false here turns a broken selector
            // into an ordinary "element not present" and hides the real cause.
            logger.warn("isVisible('{}') failed: {}", selector, e.getMessage());
            return false;
        }
    }

    public boolean isEnabled(String selector) {
        return healing.probe(selector, Locator::isEnabled);
    }

    public boolean isChecked(String selector) {
        return healing.probe(selector, Locator::isChecked);
    }

    // ---- Waits ----

    public void waitForVisible(String selector) {
        healing.resolve(selector, Intent.WAIT);
    }

    /** For waits that outlast the default action timeout, e.g. a login round-trip. */
    public void waitForVisible(String selector, double timeoutMs) {
        healing.resolve(selector, Intent.WAIT, WaitForSelectorState.VISIBLE, timeoutMs);
    }

    /** Never healed: a selector that matches nothing already satisfies "hidden". */
    public void waitForHidden(String selector) {
        locator(selector).waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN)
        );
    }

    public void waitForUrl(String urlPattern) {
        logger.debug("Waiting for URL to match: {}", urlPattern);
        page.waitForURL("**" + urlPattern + "**");
    }

    /**
     * Best-effort wait for the network to go quiet.
     * <p>
     * Network-idle never arrives on pages that hold a socket or poll in the
     * background, which is most of this app once signed in. Callers use this as
     * a settling hint before reading the DOM, never as an assertion, so a page
     * that simply stays chatty must not fail the test — the explicit waits that
     * follow are what actually prove readiness.
     */
    public void waitForNetworkIdle() {
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(NETWORK_IDLE_TIMEOUT_MS));
        } catch (com.microsoft.playwright.TimeoutError e) {
            logger.debug("Network never went idle within {}ms; continuing.",
                    NETWORK_IDLE_TIMEOUT_MS);
        }
    }

    public void waitForMs(int milliseconds) {
        page.waitForTimeout(milliseconds);
    }

    // ---- Scroll ----

    public void scrollToElement(String selector) {
        healing.resolve(selector, Intent.READ, WaitForSelectorState.ATTACHED, null).scrollIntoViewIfNeeded();
    }

    public void scrollToBottom() {
        page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
    }

    // ---- Screenshots ----

    public void takeScreenshot(String name) {
        ScreenshotUtils.takeScreenshot(name);
    }

    // ---- Abstract ----

    /**
     * Each page must implement this to verify it is fully loaded.
     */
    public abstract boolean isPageLoaded();
}
