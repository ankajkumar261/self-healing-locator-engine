package com.portfolio.selfhealing.listeners;

import com.portfolio.selfhealing.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * AllureListener - TestNG listener that captures screenshots on failure
 * and attaches them to Allure reports.
 */
public class AllureListener implements ITestListener {

    private static final Logger logger = LogManager.getLogger(AllureListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        logger.info("==> TEST STARTED: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        logger.info("==> TEST PASSED: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        logger.error("==> TEST FAILED: {} | Reason: {}", testName,
                result.getThrowable() != null ? result.getThrowable().getMessage() : "Unknown");

        // Attach screenshot to Allure
        ScreenshotUtils.takeFailureScreenshot(testName);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        logger.warn("==> TEST SKIPPED: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        logger.warn("==> TEST FAILED WITHIN SUCCESS PERCENTAGE: {}", result.getMethod().getMethodName());
    }
}
