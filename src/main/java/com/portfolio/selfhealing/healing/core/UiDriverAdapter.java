package com.portfolio.selfhealing.healing.core;

import java.util.List;
import java.util.Optional;

/**
 * What the engine needs from a UI driver. Playwright implements it today; an
 * Appium adapter would read the page-source XML into the same fingerprints and
 * build accessibility-id, UiAutomator or XPath selectors instead of CSS.
 */
public interface UiDriverAdapter {

    /** E.g. "playwright". */
    String platform();

    /** The current URL path or screen name, for reports. */
    String location();

    /** Elements the selector matches right now, or -1 when the selector is invalid. */
    int count(String selector);

    /** Fingerprint of the first element the selector matches, if any. */
    Optional<ElementFingerprint> describe(String selector);

    /** Every visible element on the current screen, up to {@code limit}. */
    List<Candidate> snapshot(int limit);

    /**
     * Selectors that match exactly this candidate and nothing else, most robust
     * first. Each must already be verified against the live screen.
     */
    List<String> selectorsFor(Candidate candidate, int max);

    /** Lets the driver drop anything it kept for the last snapshot. */
    default void release() {
    }
}
