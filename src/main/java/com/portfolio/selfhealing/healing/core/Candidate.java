package com.portfolio.selfhealing.healing.core;

/**
 * One element found on the current screen.
 *
 * @param handle      the driver's reference to the element within the snapshot it
 *                    came from; only meaningful to the adapter that produced it
 * @param fingerprint what the element looks like, already sanitised
 */
public record Candidate(int handle, ElementFingerprint fingerprint) {
}
