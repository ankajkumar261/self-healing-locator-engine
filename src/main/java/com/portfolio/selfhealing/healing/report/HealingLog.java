package com.portfolio.selfhealing.healing.report;

import com.portfolio.selfhealing.healing.core.HealingEventSink;
import com.portfolio.selfhealing.healing.core.HealingResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One log line per healing decision: original locator, healed locator,
 * confidence and reason. Goes to the console, the main log and logs/healing.log.
 */
public final class HealingLog implements HealingEventSink {

    private static final Logger log = LogManager.getLogger(HealingLog.class);

    /** A reused heal is announced once per run, then logged at debug. */
    private final Set<String> announced = ConcurrentHashMap.newKeySet();

    @Override
    public void onResult(HealingResult r) {
        switch (r.outcome()) {
            case HEALED, HEALED_BY_AI -> log.warn("[HEAL] {} '{}' -> '{}' | confidence {} | {} | {}",
                    r.locator(), r.originalSelector(), r.healedSelector(), r.percent(), r.source(), r.reason());
            case HEALED_FROM_HISTORY -> {
                if (announced.add(r.key())) {
                    log.warn("[HEAL] {} '{}' -> '{}' | confidence {} | {} | fix the page object; "
                                    + "this heal is reused until then",
                            r.locator(), r.originalSelector(), r.healedSelector(), r.percent(), r.source());
                } else {
                    log.debug("[HEAL] {} reused '{}'", r.locator(), r.healedSelector());
                }
            }
            case STALE_HEAL_DROPPED -> log.info("[HEAL] {}: {}", r.locator(), r.reason());
            case NOT_ATTEMPTED -> log.debug("[HEAL] {} not attempted: {}", r.locator(), r.reason());
            default -> {
                // WARN, not ERROR: the caller may treat the timeout as a normal "not shown".
                // If it does fail the test, the failure message carries all of this.
                log.warn("[HEAL-REJECTED] {} '{}' | {} | best {} | {}",
                        r.locator(), r.originalSelector(), r.outcome(), r.percent(), r.reason());
                r.candidates().forEach(c -> log.debug("    {}. {}% {}{}", c.rank(), Math.round(c.score() * 100),
                        c.element(), Boolean.TRUE.equals(c.excluded()) ? " [excluded: " + c.why() + "]" : ""));
            }
        }
    }
}
