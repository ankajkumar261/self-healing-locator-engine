package com.portfolio.selfhealing.healing.core;

/**
 * What the caller is about to do with an element.
 * <p>
 * Decides whether a broken locator may be searched for at all, and which
 * replacement elements are acceptable for the action.
 */
public enum Intent {

    /** Click or otherwise activate the element. */
    CLICK(true, false, true),

    /** Fill, clear or press keys: the replacement must accept input. */
    INPUT(true, true, true),

    /** Read text, a value or an attribute. */
    READ(true, false, false),

    /** Wait for the element to appear. */
    WAIT(true, false, false),

    /**
     * A boolean check such as isVisible or isEnabled.
     * <p>
     * Probes double as negative assertions ("no error banner", "no paywall"),
     * where an absent element is the correct answer. Searching for a look-alike
     * there would turn a correct "not shown" into a wrong "shown", so a probe
     * may reuse a heal that was already approved but never starts a search.
     */
    PROBE(false, false, false);

    private final boolean discovery;
    private final boolean editable;
    private final boolean acts;

    Intent(boolean discovery, boolean editable, boolean acts) {
        this.discovery = discovery;
        this.editable = editable;
        this.acts = acts;
    }

    /** True when a missing element may be searched for. */
    public boolean allowsDiscovery() {
        return discovery;
    }

    /** True when the replacement must accept typed input. */
    public boolean requiresEditable() {
        return editable;
    }

    /** True when the action changes application state (and so can do damage). */
    public boolean actsOnElement() {
        return acts;
    }
}
