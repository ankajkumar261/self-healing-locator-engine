package com.portfolio.selfhealing.constants;

/**
 * AppConstants - Central place for all test constants across the Self-Healing Locator Engine suite.
 */
public final class AppConstants {

    private AppConstants() {}

    // ---- URLs ----
    public static final String LOGIN_PATH       = "/";
    /** The workspace replaces the landing page in-place; there is no /dashboard route. */
    public static final String DASHBOARD_PATH   = "/";
    public static final String HELP_CENTER_PATH = "/help-center";
    /** The "Billing & credits" card is the one topic that leaves /help-center. */
    public static final String BILLING_PATH     = "/credits-billing";

    // ---- Page Titles ----
    /** The app rebranded from "Private AI workspace" to "ProTool App"; the repo name did not. */
    public static final String APP_TITLE        = "ProTool App";
    /** Substring the browser tab title must carry, lowercased for comparison. */
    public static final String APP_TITLE_TOKEN  = "protool";

    // ---- Test Groups ----
    public static final String GROUP_SMOKE      = "smoke";
    public static final String GROUP_REGRESSION = "regression";
    public static final String GROUP_AUTH       = "auth";
    public static final String GROUP_DASHBOARD  = "dashboard";
    public static final String GROUP_TOOLS      = "tools";
    public static final String GROUP_HISTORY    = "history";
    public static final String GROUP_REGENERATE = "regenerate";
    public static final String GROUP_HELP       = "help";
    /** Spends real credits. Excluded from the default suite; run deliberately. */
    public static final String GROUP_GENERATION = "generation";
    /**
     * Regression cover for reported defects. These assert the *fixed* behaviour,
     * so a red test here means the defect is still open — kept out of the smoke
     * suite so open defects cannot mask a genuine regression elsewhere.
     */
    public static final String GROUP_DEFECTS    = "defects";
    /** Self-healing engine checks. Offline: no live site, no logins, no credits. */
    public static final String GROUP_HEALING    = "healing";

    // ---- Timeouts ----
    public static final int SHORT_WAIT  = 2000;
    public static final int MEDIUM_WAIT = 5000;
    public static final int LONG_WAIT   = 10000;

    // ---- Error Messages (expected) ----
    public static final String INVALID_CREDENTIALS_MSG = "Invalid credentials";
    public static final String REQUIRED_FIELD_MSG      = "required";
    public static final String INVALID_EMAIL_MSG       = "valid email";

    // ---- Test Data ----
    public static final String INVALID_EMAIL    = "invalid@example.test";
    public static final String INVALID_PASSWORD = "WrongPass@999";
    public static final String EMPTY_STRING     = "";
    public static final String MALFORMED_EMAIL  = "notanemail";
}
