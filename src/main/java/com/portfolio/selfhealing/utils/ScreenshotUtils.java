package com.portfolio.selfhealing.utils;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ScreenshotUtils - Captures screenshots and attaches them to Allure reports.
 */
public class ScreenshotUtils {

    private static final Logger logger = LogManager.getLogger(ScreenshotUtils.class);
    private static final ConfigReader config = ConfigReader.getInstance();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final double SCREENSHOT_TIMEOUT_MS = 10_000;

    /**
     * Takes a screenshot and attaches it to the Allure report.
     */
    public static void takeScreenshot(String screenshotName) {
        Page page = PlaywrightManager.getPage();
        if (page == null || page.isClosed()) {
            logger.warn("Cannot take screenshot - page is null or closed.");
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String fileName = screenshotName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + timestamp + ".png";
            String screenshotPath = config.getScreenshotPath() + fileName;

            // Ensure directory exists
            Path directory = Paths.get(config.getScreenshotPath());
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            byte[] screenshotBytes = page.screenshot(
                    new Page.ScreenshotOptions()
                            .setFullPage(true)
                            .setPath(Paths.get(screenshotPath))
                            // A page stuck mid-navigation would otherwise hold the
                            // run open for the full default timeout.
                            .setTimeout(SCREENSHOT_TIMEOUT_MS)
            );

            // Attach to Allure
            Allure.addAttachment(screenshotName, "image/png", new ByteArrayInputStream(screenshotBytes), "png");
            logger.info("Screenshot saved: {}", screenshotPath);

        } catch (IOException e) {
            logger.error("Failed to save screenshot: {}", e.getMessage());
        } catch (Exception e) {
            // Screenshots are diagnostics. Playwright throws unchecked exceptions
            // when the page is hung or closing, and letting one escape fails the
            // @AfterMethod — turning a passing test into a red build.
            logger.warn("Could not capture screenshot '{}': {}",
                    screenshotName, e.getMessage().split("\n")[0]);
        }
    }

    /**
     * Takes a screenshot on test failure.
     */
    public static void takeFailureScreenshot(String testName) {
        if (config.isScreenshotOnFailure()) {
            takeScreenshot("FAILURE_" + testName);
        }
    }
}
