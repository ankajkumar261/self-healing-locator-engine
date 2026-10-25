package com.portfolio.selfhealing.healing;

import com.portfolio.selfhealing.base.BasePage;

/**
 * A page object over a static fixture, so healing is exercised through the
 * real BasePage helpers without touching the live site.
 */
public class FixturePage extends BasePage {

    public static final String SUBMIT   = "button.auth-view__submit";
    public static final String EMAIL    = "#auth-email";
    public static final String PASSWORD = "#auth-password";
    public static final String ERROR    = ".auth-view__error";

    public FixturePage show(String html) {
        page.setContent(html);
        return this;
    }

    /** data-k of the control the last click landed on, or null if nothing was clicked. */
    public String lastClicked() {
        return (String) page.evaluate("() => window.__clicked");
    }

    public String valueOf(String selector) {
        return page.inputValue(selector);
    }

    /**
     * Mirrors HelpCenterPage.open(): a TimeoutError means "reload once and retry".
     *
     * @return true if the retry path was taken
     */
    public boolean waitForSubmitOrRetryWith(String retryHtml) {
        try {
            waitForVisible(SUBMIT);
            return false;
        } catch (com.microsoft.playwright.TimeoutError first) {
            show(retryHtml);
            waitForVisible(SUBMIT);
            return true;
        }
    }

    @Override
    public boolean isPageLoaded() {
        return true;
    }
}
