package com.portfolio.selfhealing.healing.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Healing memory that outlives a run: what each locator's element looked like
 * when it last worked, the heals approved for it, and what happened recently.
 */
public interface HealingStore {

    Optional<LocatorRecord> find(String key);

    /**
     * Records what the element looked like when its original selector worked.
     * Only a change of identity is written; position alone is not.
     */
    void observeBaseline(LocatorKey key, ElementFingerprint fingerprint, String location, String at);

    void saveHeal(LocatorKey key, ActiveHeal heal);

    /** Counts one more run that needed the heal. Repeated uses within a run count once. */
    void markHealUsed(String key, String runId, String at);

    void dropHeal(LocatorKey key, HealingEvent why);

    void addEvent(LocatorKey key, HealingEvent event);

    /** @return true when anything was written */
    boolean flush();

    /**
     * @param baseline     the element as last seen through the original selector
     * @param textVolatile true once the element's text has changed between runs
     *                     (a title or balance); text then stops counting as evidence
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocatorRecord(
            String owner,
            String selector,
            String logicalName,
            ElementFingerprint baseline,
            String baselineLocation,
            String baselineCapturedAt,
            Boolean textVolatile,
            ActiveHeal heal,
            List<HealingEvent> events) {

        public LocatorRecord {
            events = events == null ? List.of() : List.copyOf(events);
        }

        static LocatorRecord of(LocatorKey key) {
            return new LocatorRecord(key.owner(), key.selector(), key.logicalName(),
                    null, null, null, null, null, List.of());
        }

        LocatorRecord withBaseline(ElementFingerprint fp, String location, String at, Boolean volatileText) {
            return new LocatorRecord(owner, selector, logicalName, fp, location, at, volatileText, heal, events);
        }

        LocatorRecord withHeal(ActiveHeal newHeal) {
            return new LocatorRecord(owner, selector, logicalName, baseline, baselineLocation,
                    baselineCapturedAt, textVolatile, newHeal, events);
        }

        LocatorRecord withLogicalName(String name) {
            return new LocatorRecord(owner, selector, name, baseline, baselineLocation,
                    baselineCapturedAt, textVolatile, heal, events);
        }

        LocatorRecord withEvent(HealingEvent event, int maxEvents) {
            List<HealingEvent> kept = new ArrayList<>(events);
            kept.add(event);
            while (kept.size() > maxEvents) {
                kept.remove(0);
            }
            return new LocatorRecord(owner, selector, logicalName, baseline, baselineLocation,
                    baselineCapturedAt, textVolatile, heal, kept);
        }
    }

    /**
     * An approved replacement, reused (and re-verified) on later runs until the
     * page object is fixed or the original selector starts working again.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActiveHeal(
            String selector,
            double confidence,
            String source,
            String reason,
            List<String> alternatives,
            String firstHealedAt,
            String lastUsedAt,
            String lastRunId,
            int runsUsed) {

        ActiveHeal usedIn(String runId, String at) {
            return new ActiveHeal(selector, confidence, source, reason, alternatives, firstHealedAt,
                    at, runId, runsUsed + 1);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HealingEvent(
            String at,
            String runId,
            String test,
            String outcome,
            String healedSelector,
            Double confidence,
            String reason) {
    }
}
