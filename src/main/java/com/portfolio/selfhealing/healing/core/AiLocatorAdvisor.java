package com.portfolio.selfhealing.healing.core;

import java.util.List;
import java.util.Optional;

/**
 * Last-resort second opinion when the best candidate scored below the healing
 * threshold but above {@code healing.ai.min.score}.
 * <p>
 * An advisor may only choose among the candidates it is shown; it cannot invent
 * selectors. Its choice still has to pass the {@link SafetyGuard}, and its own
 * confidence has to clear the threshold. Implementations are discovered with
 * {@link java.util.ServiceLoader}, so adding one needs no change to the engine.
 */
public interface AiLocatorAdvisor {

    /** Used when AI is disabled or no advisor is on the classpath. */
    AiLocatorAdvisor NONE = new AiLocatorAdvisor() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Optional<AiVerdict> choose(AiRequest request) {
            return Optional.empty();
        }
    };

    default boolean available() {
        return true;
    }

    /** @return the chosen option, or empty when the advisor declines or fails */
    Optional<AiVerdict> choose(AiRequest request);

    /**
     * @param expected the sanitised baseline of the element the locator used to find
     * @param options  the best-scoring candidates, sanitised; indexes refer to this list
     */
    record AiRequest(LocatorKey locator, Intent intent, ElementFingerprint expected,
                     List<Option> options) {
    }

    record Option(int index, double score, ElementFingerprint fingerprint) {
    }

    /**
     * @param optionIndex index into {@link AiRequest#options()}, or null for "none of these"
     * @param confidence  the advisor's own confidence in [0, 1]
     * @param reason      one sentence for the log and report
     */
    record AiVerdict(Integer optionIndex, double confidence, String reason) {
    }
}
