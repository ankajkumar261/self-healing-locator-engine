package com.portfolio.selfhealing.utils;

import com.microsoft.playwright.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * PlaywrightManager - Thread-safe manager for Playwright browser instances.
 * Uses ThreadLocal to support parallel test execution.
 */
public class PlaywrightManager {

    private static final Logger logger = LogManager.getLogger(PlaywrightManager.class);
    private static final ConfigReader config = ConfigReader.getInstance();

    private static final ThreadLocal<Playwright> playwrightThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<Browser> browserThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> contextThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<Page> pageThreadLocal = new ThreadLocal<>();

    // ---- Initialization ----

    public static void initPlaywright() {
        logger.info("Initializing Playwright...");
        Playwright playwright = Playwright.create();
        playwrightThreadLocal.set(playwright);

        String browserType = config.getBrowser().toLowerCase();
        boolean headless = config.isHeadless();
        double slowMo = Double.parseDouble(config.getOrDefault("slow.mo", "0"));

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(slowMo)
                .setArgs(List.of("--start-maximized"));

        Browser browser;
        switch (browserType) {
            case "firefox":
                browser = playwright.firefox().launch(launchOptions);
                break;
            case "webkit":
                browser = playwright.webkit().launch(launchOptions);
                break;
            default:
                browser = playwright.chromium().launch(launchOptions);
        }
        browserThreadLocal.set(browser);
        logger.info("Browser launched: {} | Headless: {}", browserType, headless);
    }

    public static void initContext() {
        initContext(null);
    }

    /**
     * @param storageStatePath a previously saved signed-in session, or null for a
     *                         clean context. Reusing state avoids re-authenticating,
     *                         which the server rate-limits.
     */
    public static void initContext(Path storageStatePath) {
        Browser browser = browserThreadLocal.get();
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setIgnoreHTTPSErrors(true);

        // Headless ignores --start-maximized, so the window falls back to 800x600
        // and the app renders its mobile layout — where the full-screen nav
        // overlay sits above the content and intercepts every click. Headless
        // runs therefore get an explicit desktop viewport; headed runs keep null
        // so the maximised window drives the size.
        if (config.isHeadless()) {
            int width  = Integer.parseInt(config.getOrDefault("viewport.width", "1920"));
            int height = Integer.parseInt(config.getOrDefault("viewport.height", "1080"));
            contextOptions.setViewportSize(width, height);
            logger.info("Headless run: viewport pinned to {}x{}", width, height);
        } else {
            contextOptions.setViewportSize(null);
        }

        if (config.isVideoRecord()) {
            contextOptions.setRecordVideoDir(Paths.get(config.getOrDefault("video.path", "target/videos/")));
        }

        if (storageStatePath != null && Files.exists(storageStatePath)) {
            contextOptions.setStorageStatePath(storageStatePath);
            logger.info("Reusing saved session from {}", storageStatePath);
        }

        BrowserContext context = browser.newContext(contextOptions);
        blockThirdPartyNoise(context);
        context.setDefaultTimeout(config.getDefaultTimeout());
        context.setDefaultNavigationTimeout(config.getNavigationTimeout());
        contextThreadLocal.set(context);
        logger.info("Browser context created.");
    }

    /**
     * Hosts whose scripts the suite never asserts on, but regularly waits for.
     * Google Tag Manager, GA and the Google sign-in client are pulled by every
     * page; when they are slow the whole run stalls on them.
     */
    private static final List<String> THIRD_PARTY_HOSTS = List.of(
            "googletagmanager.com",
            "google-analytics.com",
            "analytics.google.com",
            "accounts.google.com/gsi",
            "doubleclick.net");

    /**
     * Optionally drops third-party analytics/widget requests. OFF by default.
     * <p>
     * Blocking these looks like free stability — they are not under test and
     * their latency lands on every navigation. In practice it broke the app:
     * with the Google Tag Manager and sign-in scripts aborted, tool routes
     * stopped bootstrapping altogether and rendered neither their page nor the
     * sign-in modal, leaving a blank page that tests correctly flagged as a
     * stranded user. The real fix for the navigation stalls was switching
     * navigation to COMMIT (see BasePage#navigateTo), which does not depend on
     * third-party scripts at all.
     * <p>
     * Kept behind {@code block.third.party=true} for diagnosing whether a given
     * failure is the app or someone else's CDN — never as a default.
     */
    private static void blockThirdPartyNoise(BrowserContext context) {
        if (!Boolean.parseBoolean(config.getOrDefault("block.third.party", "false"))) {
            return;
        }
        context.route("**/*", route -> {
            String url = route.request().url();
            boolean blocked = THIRD_PARTY_HOSTS.stream().anyMatch(url::contains);
            if (blocked) {
                route.abort();
            } else {
                route.resume();
            }
        });
        logger.info("Blocking third-party analytics/sign-in noise: {}", THIRD_PARTY_HOSTS);
    }

    public static void initPage() {
        Page page = contextThreadLocal.get().newPage();
        page.setDefaultTimeout(config.getActionTimeout());
        pageThreadLocal.set(page);
        logger.info("New page created.");
    }

    // ---- Getters ----

    public static Playwright getPlaywright() {
        return playwrightThreadLocal.get();
    }

    public static Browser getBrowser() {
        return browserThreadLocal.get();
    }

    public static BrowserContext getContext() {
        return contextThreadLocal.get();
    }

    public static Page getPage() {
        return pageThreadLocal.get();
    }

    /** Persists the current signed-in session so later runs can skip logging in. */
    public static void saveStorageState(Path target) {
        try {
            Files.createDirectories(target.getParent());
            contextThreadLocal.get().storageState(
                    new BrowserContext.StorageStateOptions().setPath(target));
            logger.info("Saved session state to {}", target);
        } catch (Exception e) {
            logger.warn("Could not save session state: {}", e.getMessage());
        }
    }

    // ---- Teardown ----

    public static void teardown() {
        try {
            Page page = pageThreadLocal.get();
            if (page != null && !page.isClosed()) {
                page.close();
                pageThreadLocal.remove();
            }

            BrowserContext context = contextThreadLocal.get();
            if (context != null) {
                context.close();
                contextThreadLocal.remove();
            }

            Browser browser = browserThreadLocal.get();
            if (browser != null && browser.isConnected()) {
                browser.close();
                browserThreadLocal.remove();
            }

            Playwright playwright = playwrightThreadLocal.get();
            if (playwright != null) {
                playwright.close();
                playwrightThreadLocal.remove();
            }

            logger.info("Playwright resources closed successfully.");
        } catch (Exception e) {
            logger.error("Error during teardown: {}", e.getMessage());
        }
    }
}
