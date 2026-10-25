package com.portfolio.selfhealing.pages;

import com.portfolio.selfhealing.base.BasePage;
import io.qameta.allure.Step;

/**
 * LoginPage - Page Object for Self-Healing Locator Engine AI sign-in.
 * URL: https://app.example.test/
 *
 * The base URL is a marketing landing page, not a login form. Signing in is a
 * modal opened from the nav, and the modal itself has two steps:
 *
 *   1. landing nav "Sign in"      -> opens .auth-modal (social buttons)
 *   2. "Continue with email"      -> reveals the email/password form
 *
 * Selectors below were taken from the live DOM.
 */
public class LoginPage extends BasePage {

    // ---- Selectors (verified against live DOM) ----

    // Step 1: entry points on the landing page
    private static final String NAV_SIGN_IN = "a.nav-login";

    // Step 2: the auth modal shell
    private static final String AUTH_MODAL        = ".auth-modal";
    private static final String CONTINUE_WITH_EMAIL =
            ".auth-modal button.auth-view__secondary-btn, .auth-modal button:has-text('Continue with email')";
    private static final String MODAL_CLOSE       = ".auth-modal__close";

    // Step 3: the email/password form
    private static final String EMAIL_INPUT       = "#auth-email";
    private static final String PASSWORD_INPUT    = "#auth-password";
    private static final String SIGN_IN_BUTTON    = "button.auth-view__submit";
    private static final String FORGOT_PASSWORD   = ".auth-view__forgot-row button.auth-view__link";

    // Errors: form-level banner vs. per-field messages
    private static final String ERROR_MESSAGE     = ".auth-view__error";
    private static final String EMAIL_ERROR       =
            ".auth-view__field:has(#auth-email) .auth-view__field-error";
    private static final String PASSWORD_ERROR    =
            ".auth-view__field:has(#auth-password) .auth-view__field-error";
    private static final String ANY_FIELD_ERROR   = ".auth-view__field-error";

    private static final String PAGE_HEADING      = ".auth-view__title";
    private static final String LOGO              = "button.landing-nav__brand";

    /** Sign-in is server-throttled; see {@link #login(String, String)}. */
    private static final int LOGIN_ATTEMPTS         = 4;
    /** Backoff grows per attempt (20s, 40s, 60s): the throttle window
     *  outlasts a flat retry when a whole suite re-authenticates. */
    private static final int RATE_LIMIT_BACKOFF_MS  = 20_000;

    // ---- Actions ----

    /**
     * Navigates to the landing page and drives the modal all the way to the
     * email/password form, so callers can treat this like a plain login page.
     */
    @Step("Navigate to Self-Healing Locator Engine login page")
    public LoginPage open(String baseUrl) {
        navigateTo(baseUrl);
        // Navigation resolves on response, before React has painted. The first
        // element wait therefore gets the navigation budget rather than the
        // 10s action timeout, which a cold load routinely overruns.
        waitForVisible(NAV_SIGN_IN,
                com.portfolio.selfhealing.utils.ConfigReader.getInstance().getNavigationTimeout());
        logger.info("Opened Self-Healing Locator Engine landing page: {}", baseUrl);
        openAuthModal();
        switchToEmailLogin();
        return this;
    }

    @Step("Open the sign-in modal")
    public LoginPage openAuthModal() {
        click(NAV_SIGN_IN);
        waitForVisible(AUTH_MODAL);
        logger.info("Sign-in modal opened");
        return this;
    }

    @Step("Switch to email/password login")
    public LoginPage switchToEmailLogin() {
        click(CONTINUE_WITH_EMAIL);
        waitForVisible(EMAIL_INPUT);
        logger.info("Switched to email login form");
        return this;
    }

    @Step("Enter email: {email}")
    public LoginPage enterEmail(String email) {
        fill(EMAIL_INPUT, email);
        logger.info("Entered email: {}", email);
        return this;
    }

    @Step("Enter password")
    public LoginPage enterPassword(String password) {
        fill(PASSWORD_INPUT, password);
        logger.info("Entered password (masked)");
        return this;
    }

    @Step("Click Sign In button")
    public void clickSignIn() {
        click(SIGN_IN_BUTTON);
        logger.info("Clicked Sign In button");
        waitForNetworkIdle();
    }

    /**
     * Signs in, backing off and retrying if the server rate-limits the attempt.
     * <p>
     * Staging and production both throttle repeated sign-ins ("Too many
     * requests. Please slow down and try again later."). Any suite that signs
     * out and straight back in — the logout tests, or two auth tests in a row —
     * trips it, and the throttle then surfaces as a 60s timeout waiting for a
     * workspace that was never going to appear. Retrying here keeps the failure
     * honest: a test fails on what it asserts, not on the throttle.
     */
    @Step("Login with email: {email}")
    public void login(String email, String password) {
        for (int attempt = 1; attempt <= LOGIN_ATTEMPTS; attempt++) {
            enterEmail(email);
            enterPassword(password);
            clickSignIn();

            if (!isRateLimited()) {
                return;
            }
            if (attempt < LOGIN_ATTEMPTS) {
                int backoff = RATE_LIMIT_BACKOFF_MS * attempt;
                logger.warn("Sign-in throttled ('{}'); backing off {}ms before attempt {}/{}.",
                        getErrorMessage().trim(), backoff, attempt + 1, LOGIN_ATTEMPTS);
                waitForMs(backoff);
            } else {
                logger.error("Sign-in still throttled after {} attempts.", LOGIN_ATTEMPTS);
            }
        }
    }

    /** True when the sign-in failure is the server's rate limiter, not bad credentials. */
    public boolean isRateLimited() {
        String error = getErrorMessage().toLowerCase();
        return error.contains("too many requests") || error.contains("slow down");
    }

    @Step("Click Forgot Password")
    public void clickForgotPassword() {
        click(FORGOT_PASSWORD);
        logger.info("Clicked Forgot Password");
    }

    @Step("Close the sign-in modal")
    public void closeAuthModal() {
        click(MODAL_CLOSE);
        waitForHidden(AUTH_MODAL);
        logger.info("Closed sign-in modal");
    }

    // ---- Validations ----

    @Step("Verify login form is displayed")
    @Override
    public boolean isPageLoaded() {
        boolean loaded = isVisible(EMAIL_INPUT) && isVisible(PASSWORD_INPUT);
        logger.info("Login form displayed: {}", loaded);
        return loaded;
    }

    /**
     * Signed-out state check. Logging out returns the user to the landing page
     * with the modal closed, so the nav "Sign in" button — not the login form —
     * is what proves the session ended.
     */
    @Step("Verify user is signed out (landing page shown)")
    public boolean isSignedOut() {
        boolean signedOut = isVisible(NAV_SIGN_IN);
        logger.info("Signed out (landing 'Sign in' visible): {}", signedOut);
        return signedOut;
    }

    @Step("Get error message text")
    public String getErrorMessage() {
        if (isVisible(ERROR_MESSAGE)) {
            return getText(ERROR_MESSAGE);
        }
        // Empty/malformed input produces per-field messages, not a banner.
        if (isVisible(ANY_FIELD_ERROR)) {
            return getText(ANY_FIELD_ERROR);
        }
        return "";
    }

    @Step("Check if error message is displayed")
    public boolean isErrorMessageDisplayed() {
        return isVisible(ERROR_MESSAGE) || isVisible(ANY_FIELD_ERROR);
    }

    @Step("Check if email field is visible")
    public boolean isEmailFieldVisible() {
        return isVisible(EMAIL_INPUT);
    }

    @Step("Check if password field is visible")
    public boolean isPasswordFieldVisible() {
        return isVisible(PASSWORD_INPUT);
    }

    @Step("Check if sign-in button is enabled")
    public boolean isSignInButtonEnabled() {
        return isEnabled(SIGN_IN_BUTTON);
    }

    @Step("Get email field validation message")
    public String getEmailValidationMessage() {
        if (isVisible(EMAIL_ERROR)) {
            return getText(EMAIL_ERROR);
        }
        return "";
    }

    @Step("Get password field validation message")
    public String getPasswordValidationMessage() {
        if (isVisible(PASSWORD_ERROR)) {
            return getText(PASSWORD_ERROR);
        }
        return "";
    }

    @Step("Verify page logo is visible")
    public boolean isLogoVisible() {
        return isVisible(LOGO);
    }

    @Step("Get form heading text")
    public String getPageHeading() {
        if (isVisible(PAGE_HEADING)) {
            return getText(PAGE_HEADING);
        }
        return "";
    }
}
