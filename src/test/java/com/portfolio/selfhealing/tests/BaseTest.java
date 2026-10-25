package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.utils.ConfigReader;
import com.portfolio.selfhealing.utils.PlaywrightManager;
import com.portfolio.selfhealing.utils.ScreenshotUtils;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * BaseTest - Parent class for all test classes.
 * Handles Playwright lifecycle: init before suite, teardown after suite.
 * Each test method gets a fresh browser context and page.
 */
public abstract class BaseTest {

    protected final Logger logger = LogManager.getLogger(getClass());
    protected final ConfigReader config = ConfigReader.getInstance();

    @BeforeSuite(alwaysRun = true)
    public void suiteSetup() {
        logger.info("========================================");
        logger.info("  Self-Healing Locator Engine Automation Suite - START  ");
        logger.info("  Environment : {}", config.get("environment"));
        logger.info("  Base URL    : {}", config.getBaseUrl());
        logger.info("  Browser     : {}", config.getBrowser());
        logger.info("========================================");
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        logger.info("---- Setting up test: {} ----", testName);

        PlaywrightManager.initPlaywright();
        PlaywrightManager.initContext();
        PlaywrightManager.initPage();

        logger.info("Browser & Page ready for: {}", testName);
    }

    /**
     * Teardown must never fail the build. A test that failed on a hung page can
     * leave the browser in a state where even a screenshot throws, and an
     * exception escaping here is reported as a configuration failure — which
     * looks like a broken suite even when the test itself passed on retry.
     */
    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        String status = result.isSuccess() ? "PASSED" : "FAILED";

        if (!result.isSuccess()) {
            try {
                ScreenshotUtils.takeFailureScreenshot(testName);
            } catch (Exception e) {
                logger.warn("Failure screenshot for {} could not be captured: {}",
                        testName, e.getMessage());
            }
        }

        logger.info("---- Test {} | Status: {} ----", testName, status);
        PlaywrightManager.teardown();
    }

    @AfterSuite(alwaysRun = true)
    public void suiteTeardown() {
        logger.info("==========================================");
        logger.info("  Self-Healing Locator Engine Automation Suite - COMPLETE ");
        logger.info("==========================================");
    }

    // ---- Helper: Navigate to base URL ----

    @Step("Open application base URL")
    protected void openBaseUrl() {
        PlaywrightManager.getPage().navigate(config.getBaseUrl());
        PlaywrightManager.getPage().waitForLoadState();
        logger.info("Opened: {}", config.getBaseUrl());
    }
}
