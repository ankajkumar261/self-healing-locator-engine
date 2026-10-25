package com.portfolio.selfhealing.healing;

import com.portfolio.selfhealing.healing.core.AiLocatorAdvisor;
import com.portfolio.selfhealing.healing.core.HealingConfig;
import com.portfolio.selfhealing.healing.core.HealingEngine;
import com.portfolio.selfhealing.healing.core.JsonHealingStore;
import com.portfolio.selfhealing.healing.report.AllureHealingReporter;
import com.portfolio.selfhealing.healing.report.HealingLog;
import com.portfolio.selfhealing.healing.report.TestNgContextProvider;
import com.portfolio.selfhealing.utils.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Wires the healing engine for this project: config from ConfigReader, history
 * in a JSON file, reports to log4j and Allure, test context from TestNG.
 * <p>
 * The engine is shared by every page object in the JVM, because history and
 * "already recorded this run" bookkeeping must be shared too.
 */
public final class Healing {

    private static final Logger log = LogManager.getLogger(Healing.class);
    private static volatile HealingEngine engine;
    private static volatile boolean shutdownHookAdded;

    private Healing() {
    }

    public static HealingEngine engine() {
        HealingEngine current = engine;
        if (current == null) {
            synchronized (Healing.class) {
                if (engine == null) {
                    engine = createDefault();
                }
                current = engine;
            }
        }
        return current;
    }

    /**
     * Replaces the shared engine, e.g. with one using a temporary history file.
     *
     * @return the engine that was installed before, so it can be put back
     */
    public static synchronized HealingEngine install(HealingEngine replacement) {
        HealingEngine previous = engine;
        engine = replacement;
        return previous;
    }

    /** Called before each action so heals from class-level setup reach the running test's report. */
    public static void beforeAction() {
        AllureHealingReporter.applyPendingClassHeals();
    }

    private static HealingEngine createDefault() {
        ConfigReader cfg = ConfigReader.getInstance();
        HealingConfig config = HealingConfig.from(key -> cfg.getOrDefault(key, null));
        JsonHealingStore store = new JsonHealingStore(Path.of(config.storePath()), config.maxEventsPerLocator());
        HealingEngine created = new HealingEngine(config, store, advisor(config), new TestNgContextProvider(),
                List.of(new HealingLog(), new AllureHealingReporter()));
        if (!shutdownHookAdded) {
            // Normally the suite listener flushes; this covers runs that end without it.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> engine().store().flush(), "healing-flush"));
            shutdownHookAdded = true;
        }
        log.info("Self-healing locators: mode={} threshold={}% store={}", config.mode(),
                Math.round(config.threshold() * 100), config.storePath());
        return created;
    }

    private static AiLocatorAdvisor advisor(HealingConfig config) {
        if (!config.aiEnabled()) {
            return AiLocatorAdvisor.NONE;
        }
        return ServiceLoader.load(AiLocatorAdvisor.class).findFirst().orElseGet(() -> {
            log.warn("healing.ai.enabled=true, but no AI advisor is on the classpath; "
                    + "near misses will fail without a second opinion.");
            return AiLocatorAdvisor.NONE;
        });
    }
}
