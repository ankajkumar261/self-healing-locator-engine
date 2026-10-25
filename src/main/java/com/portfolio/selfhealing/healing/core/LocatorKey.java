package com.portfolio.selfhealing.healing.core;

/**
 * Identifies one locator across runs.
 *
 * @param owner       the page object that declares it, e.g. {@code LoginPage}
 * @param selector    the selector exactly as written in the page object
 * @param logicalName the constant it is stored in, e.g. {@code SIGN_IN_BUTTON},
 *                    or null when the selector is built inline
 */
public record LocatorKey(String owner, String selector, String logicalName) {

    /** Stable key for the history file. Same selector on two pages = two locators. */
    public String id() {
        return owner + "::" + selector;
    }

    /** {@code LoginPage.SIGN_IN_BUTTON}, or the owner plus selector when unnamed. */
    public String displayName() {
        return logicalName != null ? owner + "." + logicalName : owner + " '" + selector + "'";
    }
}
