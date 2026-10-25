package com.portfolio.selfhealing.tests;

import com.microsoft.playwright.Page;
import com.portfolio.selfhealing.pages.LoginPage;
import com.portfolio.selfhealing.utils.ConfigReader;
import com.portfolio.selfhealing.utils.PlaywrightManager;
import com.portfolio.selfhealing.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SingleSessionTest - parent for suites that need an authenticated session.
 *
 * Unlike {@link BaseTest}, the browser and login happen once per class rather
 * than once per test method. The staging server rate-limits sign-ins
 * ("Too many requests. Please slow down and try again later."), so a suite that
 * logs in for every test method starts failing on authentication rather than on
 * anything it means to assert.
 *
 * The trade-off is that tests in one class share browser state, so they must not
 * depend on execution order.
 */
public abstract class SingleSessionTest {

    protected final Logger logger = LogManager.getLogger(getClass());
    protected final ConfigReader config = ConfigReader.getInstance();

    /** Signed-in session shared by every class in the run. */
    private static final Path AUTH_STATE = Paths.get("target/state/auth.json");

    /**
     * Discards the shared session file.
     * <p>
     * Any test that deliberately signs out must call this: signing out revokes
     * the refresh token server-side, but the saved cookies still render the
     * workspace for a moment afterwards. The next class then "restores" a dead
     * session, passes its own validity check, and fails later on a blank page —
     * far away from the test that actually caused it.
     */
    public static void invalidateSavedSession() {
        try {
            if (Files.deleteIfExists(AUTH_STATE)) {
                LogManager.getLogger(SingleSessionTest.class)
                        .info("Discarded saved session at {} after a sign-out.", AUTH_STATE);
            }
        } catch (Exception e) {
            LogManager.getLogger(SingleSessionTest.class)
                    .warn("Could not delete {}: {}", AUTH_STATE, e.getMessage());
        }
    }

    private static final String WORKSPACE = "main.main-content";

    @BeforeClass(alwaysRun = true)
    public void startSessionAndLogin() {
        logger.info("---- Starting shared session for: {} ----", getClass().getSimpleName());

        PlaywrightManager.initPlaywright();
        PlaywrightManager.initContext(AUTH_STATE);
        PlaywrightManager.initPage();

        if (restoredSession()) {
            logger.info("Restored existing session for: {}", getClass().getSimpleName());
            return;
        }

        // No usable session — authenticate and persist it for the next class.
        logger.info("No usable saved session; signing in.");
        PlaywrightManager.teardown();
        PlaywrightManager.initPlaywright();
        PlaywrightManager.initContext();
        PlaywrightManager.initPage();

        new LoginPage()
                .open(config.getBaseUrl())
                .login(config.getValidEmail(), config.getValidPassword());

        PlaywrightManager.getPage().waitForSelector(WORKSPACE,
                new Page.WaitForSelectorOptions().setTimeout(config.getNavigationTimeout()));
        new com.portfolio.selfhealing.pages.DashboardPage().dismissPlanModalIfShown();

        PlaywrightManager.saveStorageState(AUTH_STATE);
        logger.info("Shared session authenticated for: {}", getClass().getSimpleName());
    }

    /**
     * Reports whether the saved cookies still sign us in.
     * <p>
     * The workspace shell is NOT proof of a session — tool routes such as
     * /document render their whole page while signed out and only raise the auth
     * modal once you click Generate. A run that trusts the shell silently tests
     * as an anonymous user, which surfaces later as an upgrade paywall rather
     * than as an auth failure. The account email in the profile popup is the
     * cheapest thing only a real session can produce.
     */
    private boolean restoredSession() {
        if (!Files.exists(AUTH_STATE)) {
            return false;
        }
        try {
            Page page = PlaywrightManager.getPage();
            page.navigate(config.getBaseUrl());
            page.waitForSelector(WORKSPACE,
                    new Page.WaitForSelectorOptions().setTimeout(15_000));

            // A free-tier account is met by the plan-activation modal, which
            // covers the avatar. Clear it before touching the workspace.
            new com.portfolio.selfhealing.pages.DashboardPage().dismissPlanModalIfShown();

            page.click(".sidebar__avatar");
            page.waitForSelector(".profile-popup__email",
                    new Page.WaitForSelectorOptions().setTimeout(10_000));
            String email = page.textContent(".profile-popup__email").trim();
            page.keyboard().press("Escape");

            if (!email.equalsIgnoreCase(config.getValidEmail())) {
                logger.info("Saved session belongs to '{}', expected '{}'.",
                        email, config.getValidEmail());
                return false;
            }
            logger.info("Saved session is valid for {}", email);
            return true;
        } catch (Exception e) {
            logger.info("Saved session is stale: {}", e.getMessage().split("\n")[0]);
            return false;
        }
    }

    /** Diagnostics only — never let this fail the build. See BaseTest#tearDown. */
    @AfterMethod(alwaysRun = true)
    public void captureFailure(ITestResult result) {
        if (!result.isSuccess()) {
            try {
                ScreenshotUtils.takeFailureScreenshot(result.getMethod().getMethodName());
            } catch (Exception e) {
                logger.warn("Failure screenshot could not be captured: {}", e.getMessage());
            }
        }
        logger.info("---- Test {} | Status: {} ----",
                result.getMethod().getMethodName(),
                result.isSuccess() ? "PASSED" : "FAILED");
    }

    @AfterClass(alwaysRun = true)
    public void endSession() {
        PlaywrightManager.teardown();
        logger.info("---- Ended shared session for: {} ----", getClass().getSimpleName());
    }
}
