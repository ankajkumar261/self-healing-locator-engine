package com.portfolio.selfhealing.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * ConfigReader - Reads test configuration from config.properties
 * Singleton pattern ensures properties are loaded once.
 */
public class ConfigReader {

    private static final Logger logger = LogManager.getLogger(ConfigReader.class);
    private static final String CONFIG_FILE = "src/test/resources/config.properties";
    /** Untracked overrides holding credentials. Copy from the .template file. */
    private static final String LOCAL_CONFIG_FILE = "src/test/resources/config.local.properties";

    /** Keys that must never be committed; looked up with a clearer failure message. */
    private static final java.util.Set<String> SECRET_KEYS =
            java.util.Set.of("valid.email", "valid.password");

    private static ConfigReader instance;
    private final Properties properties;

    private ConfigReader() {
        properties = new Properties();
        load(CONFIG_FILE, true);
        // Layered on top so a developer's local credentials win over the defaults.
        load(LOCAL_CONFIG_FILE, false);
    }

    private void load(String path, boolean required) {
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            if (required) {
                throw new RuntimeException("Cannot load configuration file: " + path);
            }
            logger.info("No local overrides at {} (using defaults and environment)", path);
            return;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            properties.load(fis);
            logger.info("Configuration loaded from: {}", path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load configuration file: " + path, e);
        }
    }

    /** {@code valid.password} maps to the {@code VALID_PASSWORD} environment variable. */
    private static String envNameFor(String key) {
        return key.toUpperCase().replace('.', '_');
    }

    public static ConfigReader getInstance() {
        if (instance == null) {
            synchronized (ConfigReader.class) {
                if (instance == null) {
                    instance = new ConfigReader();
                }
            }
        }
        return instance;
    }

    /**
     * Resolves a key, highest precedence first:
     * {@code -Dkey=value}, environment variable, config.local.properties, config.properties.
     */
    public String get(String key) {
        String value = System.getProperty(key);
        if (isSet(value)) {
            return value.trim();
        }
        value = System.getenv(envNameFor(key));
        if (isSet(value)) {
            return value.trim();
        }
        value = properties.getProperty(key);
        if (!isSet(value)) {
            throw new RuntimeException(missingMessage(key));
        }
        return value.trim();
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static String missingMessage(String key) {
        if (SECRET_KEYS.contains(key)) {
            return "Missing credential '" + key + "'. Copy "
                    + LOCAL_CONFIG_FILE + ".template to " + LOCAL_CONFIG_FILE
                    + " and fill it in, or set the " + envNameFor(key)
                    + " environment variable.";
        }
        return "Missing property: " + key;
    }

    public String getOrDefault(String key, String defaultValue) {
        try {
            return get(key);
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    // ---- Convenience Getters ----

    public String getBaseUrl() {
        return get("base.url");
    }

    public String getValidEmail() {
        return get("valid.email");
    }

    public String getValidPassword() {
        return get("valid.password");
    }

    public String getBrowser() {
        return getOrDefault("browser", "chromium");
    }

    public boolean isHeadless() {
        return getBoolean("headless");
    }

    public int getDefaultTimeout() {
        return getInt("default.timeout");
    }

    public int getNavigationTimeout() {
        return getInt("navigation.timeout");
    }

    public int getActionTimeout() {
        return getInt("action.timeout");
    }

    public boolean isScreenshotOnFailure() {
        return getBoolean("screenshot.on.failure");
    }

    public String getScreenshotPath() {
        return getOrDefault("screenshot.path", "target/screenshots/");
    }

    public boolean isVideoRecord() {
        return getBoolean("video.record");
    }

    public int getRetryCount() {
        return getInt("retry.count");
    }
}
