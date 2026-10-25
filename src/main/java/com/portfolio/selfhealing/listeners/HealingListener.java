package com.portfolio.selfhealing.listeners;

import com.portfolio.selfhealing.healing.Healing;
import com.portfolio.selfhealing.healing.core.HealingEngine;
import com.portfolio.selfhealing.healing.core.HealingResult;
import com.portfolio.selfhealing.healing.report.AllureHealingReporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.List;

/**
 * HealingListener - suite-level bookkeeping for self-healing locators.
 * <p>
 * Registered through META-INF/services rather than testng.xml, so
 * {@code mvn test -Dtest=SomeTest} (which bypasses the suite file) still writes
 * the healing history and report.
 */
public class HealingListener implements ISuiteListener, ITestListener {

    private static final Logger logger = LogManager.getLogger(HealingListener.class);

    @Override
    public void onStart(ISuite suite) {
        Healing.engine();
        AllureHealingReporter.writeCategories();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        Object healed = result.getAttribute(AllureHealingReporter.HEALED_ATTRIBUTE);
        if (healed != null) {
            logger.warn("==> TEST PASSED WITH HEALING: {} | healed: {}",
                    result.getMethod().getMethodName(), healed);
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        HealingEngine engine = Healing.engine();
        engine.finishRun();
        List<HealingResult> results = engine.runResults();
        long healed = results.stream().filter(HealingResult::healed).count();
        long rejected = results.stream().filter(r -> r.outcome().rejected()).count();
        if (healed + rejected > 0) {
            logger.warn("Self-healing summary: {} heal(s), {} rejection(s). Details: {}",
                    healed, rejected, engine.config().reportPath());
        }
    }
}
