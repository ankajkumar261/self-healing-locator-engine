package com.portfolio.selfhealing.healing.core;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Healing settings, read from {@code healing.*} keys.
 * <p>
 * Takes a plain key lookup rather than ConfigReader so the core has no
 * dependency on how this project loads configuration.
 */
public record HealingConfig(
        Mode mode,
        double threshold,
        double ambiguityMargin,
        double noBaselineCap,
        Set<String> reportOnlyGroups,
        String storePath,
        String reportPath,
        int maxEventsPerLocator,
        int snapshotLimit,
        boolean aiEnabled,
        double aiMinScore,
        int aiMaxCandidates) {

    public enum Mode {
        /** Plain driver behaviour; nothing is recorded or searched. */
        OFF,
        /** Search and log the replacement, but fail anyway. */
        REPORT,
        /** Use the replacement when it clears the threshold. */
        HEAL
    }

    /** @param lookup returns the configured value for a key, or null when unset */
    public static HealingConfig from(Function<String, String> lookup) {
        Mode mode = Mode.valueOf(value(lookup, "healing.mode", "heal").toUpperCase(Locale.ROOT));
        if (!Boolean.parseBoolean(value(lookup, "healing.enabled", "true"))) {
            mode = Mode.OFF;
        }
        return new HealingConfig(
                mode,
                fraction(value(lookup, "healing.confidence.threshold", "0.90")),
                fraction(value(lookup, "healing.ambiguity.margin", "0.05")),
                fraction(value(lookup, "healing.no.baseline.cap", "0.85")),
                Arrays.stream(value(lookup, "healing.report.only.groups", "generation").split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet()),
                value(lookup, "healing.store.path", "healing/locator-history.json"),
                value(lookup, "healing.report.path", "target/healing-report.json"),
                Integer.parseInt(value(lookup, "healing.history.max.events", "20")),
                Integer.parseInt(value(lookup, "healing.snapshot.limit", "4000")),
                Boolean.parseBoolean(value(lookup, "healing.ai.enabled", "false")),
                fraction(value(lookup, "healing.ai.min.score", "0.70")),
                Integer.parseInt(value(lookup, "healing.ai.max.candidates", "5")));
    }

    public static HealingConfig defaults() {
        return from(key -> null);
    }

    public boolean enabled() {
        return mode != Mode.OFF;
    }

    public HealingConfig withMode(Mode newMode) {
        return new HealingConfig(newMode, threshold, ambiguityMargin, noBaselineCap, reportOnlyGroups,
                storePath, reportPath, maxEventsPerLocator, snapshotLimit, aiEnabled, aiMinScore,
                aiMaxCandidates);
    }

    public HealingConfig withPaths(String newStorePath, String newReportPath) {
        return new HealingConfig(mode, threshold, ambiguityMargin, noBaselineCap, reportOnlyGroups,
                newStorePath, newReportPath, maxEventsPerLocator, snapshotLimit, aiEnabled, aiMinScore,
                aiMaxCandidates);
    }

    public HealingConfig withAi(boolean enabled) {
        return new HealingConfig(mode, threshold, ambiguityMargin, noBaselineCap, reportOnlyGroups,
                storePath, reportPath, maxEventsPerLocator, snapshotLimit, enabled, aiMinScore,
                aiMaxCandidates);
    }

    private static String value(Function<String, String> lookup, String key, String fallback) {
        String v = lookup.apply(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    /** Accepts 0.9 or 90 for ninety percent. */
    private static double fraction(String raw) {
        double v = Double.parseDouble(raw);
        return v > 1 ? v / 100.0 : v;
    }
}
