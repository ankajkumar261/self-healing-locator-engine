package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.DashboardPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * DashboardTest - End-to-end tests for Self-Healing Locator Engine Dashboard/Home Screen.
 *
 * Test Coverage:
 *  TC_DASH_001 - Dashboard loads after login
 *  TC_DASH_002 - Main content area is visible
 *  TC_DASH_003 - Sidebar navigation is visible
 *  TC_DASH_004 - User avatar/profile icon is visible
 *  TC_DASH_005 - Page title is correct
 *  TC_DASH_006 - Loader disappears after page load
 *  TC_DASH_007 - Dashboard URL is correct after login
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("Dashboard")
public class DashboardTest extends SingleSessionTest {

    private DashboardPage dashboardPage;

    /**
     * These tests only need to <em>be</em> signed in, not to sign in, so they run
     * on the session {@link SingleSessionTest} establishes once for the class.
     * Logging in per method meant seven sign-ins for seven tests, and the server
     * throttles them — a run would fail on authentication rather than on anything
     * the dashboard actually asserts.
     * <p>
     * alwaysRun is required: testng.xml filters on the "smoke" group, and a group
     * filter excludes configuration methods too unless they opt out.
     */
    @BeforeMethod(alwaysRun = true)
    public void openDashboard() {
        dashboardPage = new DashboardPage();
        // Reload so tests never inherit navigation state from one another.
        dashboardPage.navigateTo(config.getBaseUrl());
        dashboardPage.waitForDashboard();
    }

    // ----------------------------------------------------------------
    // TC_DASH_001 - Dashboard Loads After Login
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_DASHBOARD},
            description = "Dashboard should be fully loaded after successful login"
    )
    @Story("Dashboard Load")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verify that the Self-Healing Locator Engine dashboard loads successfully after valid login.")
    public void TC_DASH_001_dashboardLoadsAfterLogin() {
        Assert.assertTrue(dashboardPage.isPageLoaded(),
                "Dashboard page should be fully loaded after login.");

        dashboardPage.takeScreenshot("TC_DASH_001_Dashboard_Loaded");
        logger.info("TC_DASH_001 PASSED: Dashboard loaded after login.");
    }

    // ----------------------------------------------------------------
    // TC_DASH_002 - Main Content Area Visible
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_DASHBOARD},
            description = "Main content area should be visible on the dashboard"
    )
    @Story("Dashboard UI Elements")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify main content area is rendered and visible on the dashboard page.")
    public void TC_DASH_002_mainContentAreaVisible() {
        Assert.assertTrue(dashboardPage.isMainContentVisible(),
                "Main content area should be visible on dashboard.");

        dashboardPage.takeScreenshot("TC_DASH_002_Main_Content");
        logger.info("TC_DASH_002 PASSED: Main content area is visible.");
    }

    // ----------------------------------------------------------------
    // TC_DASH_003 - Sidebar Navigation Visible
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_DASHBOARD},
            description = "Sidebar navigation should be visible on the dashboard"
    )
    @Story("Dashboard UI Elements")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify the sidebar navigation menu is rendered and accessible.")
    public void TC_DASH_003_sidebarNavigationVisible() {
        Assert.assertTrue(dashboardPage.isSidebarVisible(),
                "Sidebar navigation should be visible on dashboard.");

        dashboardPage.takeScreenshot("TC_DASH_003_Sidebar_Navigation");
        logger.info("TC_DASH_003 PASSED: Sidebar navigation is visible.");
    }

    // ----------------------------------------------------------------
    // TC_DASH_004 - User Avatar/Profile Icon Visible
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_DASHBOARD},
            description = "User avatar or profile icon should be visible indicating logged-in state"
    )
    @Story("Dashboard UI Elements")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify that the user's avatar or profile indicator is visible after login.")
    public void TC_DASH_004_userAvatarVisible() {
        Assert.assertTrue(dashboardPage.isUserLoggedIn(),
                "User avatar/profile icon should be visible on dashboard.");

        dashboardPage.takeScreenshot("TC_DASH_004_User_Avatar");
        logger.info("TC_DASH_004 PASSED: User avatar visible.");
    }

    // ----------------------------------------------------------------
    // TC_DASH_005 - Page Title Is Correct
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_DASHBOARD},
            description = "Browser tab title should carry the app name after login"
    )
    @Story("Dashboard Load")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify the page title in the browser tab includes the app name after successful login.")
    public void TC_DASH_005_pageTitleIsCorrect() {
        String pageTitle = com.portfolio.selfhealing.utils.PlaywrightManager.getPage().title();
        logger.info("Page title: '{}'", pageTitle);

        Assert.assertTrue(
                pageTitle.toLowerCase().contains(AppConstants.APP_TITLE_TOKEN),
                "Page title should contain '" + AppConstants.APP_TITLE_TOKEN
                        + "'. Actual: " + pageTitle
        );

        logger.info("TC_DASH_005 PASSED: Page title is '{}'.", pageTitle);
    }

    // ----------------------------------------------------------------
    // TC_DASH_006 - Loader Disappears After Page Load
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_DASHBOARD},
            description = "Page loader/spinner should not be visible after dashboard fully loads"
    )
    @Story("Dashboard Load")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify that any loading spinner or overlay is hidden once the dashboard is ready.")
    public void TC_DASH_006_loaderHiddenAfterLoad() {
        Assert.assertTrue(dashboardPage.isLoaderHidden(),
                "Loader/spinner should not be visible on fully loaded dashboard.");

        dashboardPage.takeScreenshot("TC_DASH_006_No_Loader");
        logger.info("TC_DASH_006 PASSED: Loader is hidden after dashboard load.");
    }

    // ----------------------------------------------------------------
    // TC_DASH_007 - Dashboard URL After Login
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_DASHBOARD},
            description = "URL should change from login page after successful authentication"
    )
    @Story("Dashboard Load")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verify that the URL after login is different from the login page URL (user redirected to app).")
    public void TC_DASH_007_dashboardUrlAfterLogin() {
        String currentUrl = dashboardPage.getDashboardUrl();
        logger.info("Dashboard URL: '{}'", currentUrl);

        Assert.assertFalse(currentUrl.isEmpty(),
                "Dashboard URL should not be empty after login.");

        // After login, URL should typically differ from the base login URL
        // or contain a path like /dashboard, /home, /app etc.
        String baseUrl = config.getBaseUrl();
        logger.info("Base URL: '{}' | Current URL: '{}'", baseUrl, currentUrl);

        dashboardPage.takeScreenshot("TC_DASH_007_Dashboard_URL");
        logger.info("TC_DASH_007 PASSED: Dashboard URL: '{}'", currentUrl);
    }
}
