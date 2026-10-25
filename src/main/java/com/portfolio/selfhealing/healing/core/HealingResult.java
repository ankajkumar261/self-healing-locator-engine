package com.portfolio.selfhealing.healing.core;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What happened when a locator stopped matching.
 *
 * @param outcome          the decision
 * @param locator          display name, e.g. {@code LoginPage.SIGN_IN_BUTTON}
 * @param key              history-file key
 * @param intent           what the test was about to do
 * @param originalSelector the selector in the page object
 * @param healedSelector   the replacement used, or for a report-only or rejected
 *                         outcome the one that would have been used, if any
 * @param confidence       score of the chosen element in [0, 1]
 * @param source           where the replacement came from (DOM_SIMILARITY, HISTORY, AI)
 * @param reason           why it was, or was not, accepted
 * @param alternatives     other selectors that also point at the replacement
 * @param candidates       the best-scoring elements, for triage
 * @param test             the test that was running
 * @param location         the URL path or screen
 * @param at               ISO-8601 timestamp
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HealingResult(
        Outcome outcome,
        String locator,
        String key,
        Intent intent,
        String originalSelector,
        String healedSelector,
        Double confidence,
        String source,
        String reason,
        List<String> alternatives,
        List<CandidateReport> candidates,
        String test,
        String location,
        String at) {

    public enum Outcome {
        HEALED,
        HEALED_FROM_HISTORY,
        HEALED_BY_AI,
        REJECTED_LOW_CONFIDENCE,
        REJECTED_AMBIGUOUS,
        REJECTED_UNSAFE,
        REJECTED_NO_SELECTOR,
        REJECTED_REPORT_ONLY,
        STALE_HEAL_DROPPED,
        NOT_ATTEMPTED;

        public boolean healed() {
            return this == HEALED || this == HEALED_FROM_HISTORY || this == HEALED_BY_AI;
        }

        public boolean rejected() {
            return name().startsWith("REJECTED_");
        }
    }

    /**
     * @param rank    1 for the best-scoring element
     * @param score   its score
     * @param element short description, e.g. {@code <button.auth-view__link> "Forgot password?"}
     * @param why     the score breakdown, or why the guard excluded it
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CandidateReport(int rank, double score, String element, String why, Boolean excluded) {
    }

    /** Starts every rejection message, so reports can group rejections apart from other failures. */
    public static final String REJECTION_MARKER = "HEALING_REJECTED";
    private static final int CANDIDATES_SHOWN = 5;

    public HealingResult {
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public boolean healed() {
        return outcome.healed();
    }

    /**
     * The failure message for a rejection: what stopped matching, why nothing
     * replaced it, the suggested selector if any, and the closest elements.
     * Each driver adapter wraps it in its own timeout type.
     */
    public String rejectionMessage(Throwable cause) {
        StringBuilder sb = new StringBuilder(REJECTION_MARKER).append(": ")
                .append(locator).append(" '").append(originalSelector)
                .append("' matched no element, and no replacement was safe to use (")
                .append(outcome).append(").\n")
                .append("Reason: ").append(reason).append('\n');
        if (healedSelector != null) {
            sb.append("Suggested selector: ").append(healedSelector).append(" (").append(percent()).append(")\n");
        }
        if (!candidates.isEmpty()) {
            sb.append("Closest elements on ").append(location).append(":\n");
            candidates.stream().limit(CANDIDATES_SHOWN).forEach(c -> sb
                    .append(String.format("  %d. %3d%%  %s%s%n", c.rank(), Math.round(c.score() * 100),
                            c.element(), Boolean.TRUE.equals(c.excluded())
                                    ? "  [excluded: " + c.why() + "]" : "  " + c.why())));
        }
        if (cause != null && cause.getMessage() != null) {
            sb.append("Original error: ").append(cause.getMessage().split("\n")[0]);
        }
        return sb.toString();
    }

    /** "94%", or "n/a" when nothing was scored. */
    public String percent() {
        return confidence == null ? "n/a" : Math.round(confidence * 100) + "%";
    }
}
