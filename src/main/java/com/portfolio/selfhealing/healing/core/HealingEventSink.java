package com.portfolio.selfhealing.healing.core;

/**
 * Receives every healing decision: logs, test reports, dashboards.
 * <p>
 * Implementations must not throw. Reporting is diagnostics; the engine guards
 * the call anyway so a broken sink cannot fail a test.
 */
public interface HealingEventSink {

    void onResult(HealingResult result);
}
