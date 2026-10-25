package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.DashboardPage;
import com.portfolio.selfhealing.pages.LoginPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * LoginTest - End-to-end tests for Self-Healing Locator Engine authentication.
 *
 * Test Coverage:
 *  TC_AUTH_001 - Verify login page loads correctly
 *  TC_AUTH_002 - Valid login navigates to dashboard
 *  TC_AUTH_003 - Invalid password shows error
 *  TC_AUTH_004 - Invalid email shows error
 *  TC_AUTH_005 - Empty email shows validation
 *  TC_AUTH_006 - Empty password shows validation
 *  TC_AUTH_007 - Malformed email format validation
 *  TC_AUTH_008 - Sign-in button is disabled with empty fields (if applicable)
 *  TC_AUTH_009 - Logout from dashboard redirects to login
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("Authentication")
public class LoginTest extends BaseTest {

    private LoginPage loginPage;
    private DashboardPage dashboardPage;

    // alwaysRun is required: testng.xml filters on the "smoke" group, and a
    // group filter excludes configuration methods too unless they opt out.
    @BeforeMethod(alwaysRun = true)
    public void initPages() {
        loginPage = new LoginPage();
        dashboardPage = new DashboardPage();
    }

    // ----------------------------------------------------------------
    // TC_AUTH_001 - Login Page Loads Correctly
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_AUTH},
            description = "Verify Self-Healing Locator Engine login page loads with all required elements"
    )
    @Story("Login Page Visibility")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify that the login page loads correctly with email, password fields, sign-in button, and logo.")
    public void TC_AUTH_001_verifyLoginPageLoads() {
        loginPage.open(config.getBaseUrl());

        Assert.assertTrue(loginPage.isPageLoaded(),
                "Login page should be loaded with email and password fields.");
        Assert.assertTrue(loginPage.isEmailFieldVisible(),
                "Email input field should be visible.");
        Assert.assertTrue(loginPage.isPasswordFieldVisible(),
                "Password input field should be visible.");
        Assert.assertTrue(loginPage.isSignInButtonEnabled(),
                "Sign In button should be present and enabled.");

        loginPage.takeScreenshot("TC_AUTH_001_Login_Page_Load");
        logger.info("TC_AUTH_001 PASSED: Login page loaded correctly.");
    }

    // ----------------------------------------------------------------
    // TC_AUTH_002 - Valid Login
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_AUTH},
            description = "Valid credentials should log in and navigate to dashboard"
    )
    @Story("Successful Login")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Login with valid email/password and verify dashboard is displayed.")
    public void TC_AUTH_002_validLoginNavigatesToDashboard() {
        loginPage.open(config.getBaseUrl());
        loginPage.login(config.getValidEmail(), config.getValidPassword());

        dashboardPage.waitForDashboard();

        Assert.assertTrue(dashboardPage.isPageLoaded(),
                "Dashboard should be visible after successful login.");

        String currentUrl = dashboardPage.getDashboardUrl();
        Assert.assertFalse(currentUrl.isEmpty(),
                "URL after login should not be empty.");

        dashboardPage.takeScreenshot("TC_AUTH_002_Dashboard_After_Login");
        logger.info("TC_AUTH_002 PASSED: Successfully logged in. URL: {}", currentUrl);
    }

    // ----------------------------------------------------------------
    // TC_AUTH_003 - Invalid Password
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_AUTH},
            description = "Invalid password should show an error message"
    )
    @Story("Invalid Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Login with correct email but wrong password — expect error message.")
    public void TC_AUTH_003_invalidPasswordShowsError() {
        loginPage.open(config.getBaseUrl());
        loginPage.login(config.getValidEmail(), AppConstants.INVALID_PASSWORD);

        waitForMs(2000);

        Assert.assertTrue(loginPage.isErrorMessageDisplayed(),
                "Error message should be shown for invalid password.");

        String errorMsg = loginPage.getErrorMessage();
        Assert.assertFalse(errorMsg.isEmpty(),
                "Error message text should not be empty.");

        loginPage.takeScreenshot("TC_AUTH_003_Invalid_Password_Error");
        logger.info("TC_AUTH_003 PASSED: Error message shown: '{}'", errorMsg);
    }

    // ----------------------------------------------------------------
    // TC_AUTH_004 - Invalid Email
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_AUTH},
            description = "Non-existent email should show an error message"
    )
    @Story("Invalid Login")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Login with a non-existent email address — expect error message.")
    public void TC_AUTH_004_invalidEmailShowsError() {
        loginPage.open(config.getBaseUrl());
        loginPage.login(AppConstants.INVALID_EMAIL, config.getValidPassword());

        waitForMs(2000);

        Assert.assertTrue(loginPage.isErrorMessageDisplayed(),
                "Error message should be shown for non-existent email.");

        loginPage.takeScreenshot("TC_AUTH_004_Invalid_Email_Error");
        logger.info("TC_AUTH_004 PASSED: Error displayed for invalid email.");
    }

    // ----------------------------------------------------------------
    // TC_AUTH_005 - Empty Email
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_AUTH},
            description = "Empty email field should show validation message"
    )
    @Story("Form Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submit login form with empty email — expect validation message.")
    public void TC_AUTH_005_emptyEmailShowsValidation() {
        loginPage.open(config.getBaseUrl());
        loginPage.enterEmail(AppConstants.EMPTY_STRING);
        loginPage.enterPassword(config.getValidPassword());
        loginPage.clickSignIn();

        waitForMs(1500);

        boolean hasError = loginPage.isErrorMessageDisplayed()
                || !loginPage.getEmailValidationMessage().isEmpty();

        Assert.assertTrue(hasError,
                "Validation should appear for empty email.");

        loginPage.takeScreenshot("TC_AUTH_005_Empty_Email_Validation");
        logger.info("TC_AUTH_005 PASSED: Validation shown for empty email.");
    }

    // ----------------------------------------------------------------
    // TC_AUTH_006 - Empty Password
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_AUTH},
            description = "Empty password field should show validation message"
    )
    @Story("Form Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submit login form with empty password — expect validation message.")
    public void TC_AUTH_006_emptyPasswordShowsValidation() {
        loginPage.open(config.getBaseUrl());
        loginPage.enterEmail(config.getValidEmail());
        loginPage.enterPassword(AppConstants.EMPTY_STRING);
        loginPage.clickSignIn();

        waitForMs(1500);

        boolean hasError = loginPage.isErrorMessageDisplayed()
                || !loginPage.getPasswordValidationMessage().isEmpty();

        Assert.assertTrue(hasError,
                "Validation should appear for empty password.");

        loginPage.takeScreenshot("TC_AUTH_006_Empty_Password_Validation");
        logger.info("TC_AUTH_006 PASSED: Validation shown for empty password.");
    }

    // ----------------------------------------------------------------
    // TC_AUTH_007 - Malformed Email Format
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_AUTH},
            description = "Malformed email format should show format validation error"
    )
    @Story("Form Validation")
    @Severity(SeverityLevel.NORMAL)
    @Description("Submit login with an invalid email format — expect client-side format validation.")
    public void TC_AUTH_007_malformedEmailFormatValidation() {
        loginPage.open(config.getBaseUrl());
        loginPage.enterEmail(AppConstants.MALFORMED_EMAIL);
        loginPage.enterPassword(config.getValidPassword());
        loginPage.clickSignIn();

        waitForMs(1500);

        boolean hasFormatError = loginPage.isErrorMessageDisplayed()
                || !loginPage.getEmailValidationMessage().isEmpty();

        Assert.assertTrue(hasFormatError,
                "Format validation should appear for malformed email.");

        loginPage.takeScreenshot("TC_AUTH_007_Malformed_Email_Validation");
        logger.info("TC_AUTH_007 PASSED: Format validation shown for malformed email.");
    }

    // ----------------------------------------------------------------
    // TC_AUTH_009 - Logout Redirects to Login
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_AUTH},
            description = "Logout should redirect the user back to the login page"
    )
    @Story("Logout")
    @Severity(SeverityLevel.CRITICAL)
    @Description("After successful login, logout and verify user is redirected to login page.")
    public void TC_AUTH_009_logoutRedirectsToLogin() {
        // First login
        loginPage.open(config.getBaseUrl());
        loginPage.login(config.getValidEmail(), config.getValidPassword());
        dashboardPage.waitForDashboard();
        Assert.assertTrue(dashboardPage.isPageLoaded(), "Must be on dashboard before logout.");

        // Then logout
        dashboardPage.logout();

        waitForMs(2000);

        // Signing out drops back to the landing page, not a login form.
        Assert.assertTrue(loginPage.isSignedOut(),
                "User should be back on the signed-out landing page after logout.");
        Assert.assertFalse(dashboardPage.isUserLoggedIn(),
                "Workspace avatar should be gone after logout.");

        loginPage.takeScreenshot("TC_AUTH_009_Logout_Redirect");
        logger.info("TC_AUTH_009 PASSED: Logout redirected to login page.");
    }

    // ----------------------------------------------------------------
    // TC_DEF_006 - Logout asks for confirmation first
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_AUTH, AppConstants.GROUP_DEFECTS},
            description = "Signing out raises a confirmation dialog, and cancelling keeps the session"
    )
    @Story("Logout")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Covers DEF-006. Both halves are asserted in one login because staging "
            + "rate-limits sign-ins: the dialog must appear and be dismissable without "
            + "ending the session, and confirming must actually sign the user out.")
    public void TC_DEF_006_logoutAsksForConfirmation() {
        loginPage.open(config.getBaseUrl());
        loginPage.login(config.getValidEmail(), config.getValidPassword());
        dashboardPage.waitForDashboard();
        Assert.assertTrue(dashboardPage.isPageLoaded(), "Must be on dashboard before logout.");

        // Clicking "Sign out" must ask, not act.
        dashboardPage.openSignOutConfirm();

        Assert.assertTrue(dashboardPage.isSignOutConfirmShown(),
                "DEF-006: clicking 'Sign out' should raise a confirmation dialog.");
        Assert.assertEquals(dashboardPage.getSignOutConfirmTitle(), "Sign out?",
                "The confirmation dialog should ask before signing out.");
        Assert.assertEquals(dashboardPage.getSignOutConfirmRole(), "alertdialog",
                "The confirmation should be an alertdialog so assistive tech announces it.");
        Assert.assertTrue(dashboardPage.isStaySignedInVisible(),
                "The dialog must offer a way to back out.");
        dashboardPage.takeScreenshot("TC_DEF_006_Confirm_Dialog");

        // Backing out must leave the session intact.
        dashboardPage.cancelSignOut();
        Assert.assertFalse(dashboardPage.isSignOutConfirmShown(),
                "The dialog should close on 'Stay signed in'.");
        Assert.assertTrue(dashboardPage.isUserLoggedIn(),
                "DEF-006: cancelling the confirmation must not sign the user out.");

        // Confirming must actually sign out.
        dashboardPage.openSignOutConfirm();
        Assert.assertTrue(dashboardPage.isSignOutConfirmShown(),
                "The dialog should be raisable a second time.");
        dashboardPage.confirmSignOut();

        waitForMs(2000);
        Assert.assertTrue(loginPage.isSignedOut(),
                "Confirming should return the user to the signed-out landing page.");
        Assert.assertFalse(dashboardPage.isUserLoggedIn(),
                "Workspace avatar should be gone after a confirmed sign-out.");

        loginPage.takeScreenshot("TC_DEF_006_Signed_Out");
        logger.info("TC_DEF_006 PASSED: sign-out confirmation appears, cancels and confirms.");
    }

    /**
     * Signing out revokes the refresh token server-side, which kills the session
     * that {@link SingleSessionTest} shares via target/state/auth.json. Without
     * this, the next authenticated class restores a dead session and fails on a
     * blank workspace instead of on anything it meant to assert.
     */
    @AfterMethod(alwaysRun = true)
    public void discardSharedSessionAfterLogout(ITestResult result) {
        if (result.getMethod().getMethodName().toLowerCase().contains("logout")) {
            SingleSessionTest.invalidateSavedSession();
        }
    }

    // ---- Helper ----
    private void waitForMs(int ms) {
        PlaywrightManager_waitHelper(ms);
    }

    private void PlaywrightManager_waitHelper(int ms) {
        com.portfolio.selfhealing.utils.PlaywrightManager.getPage().waitForTimeout(ms);
    }
}
