package com.portfolio.selfhealing.healing.playwright;

import com.microsoft.playwright.TimeoutError;
import com.portfolio.selfhealing.healing.core.HealingResult;

/**
 * A selector matched nothing and no replacement was safe to use.
 * <p>
 * Deliberately a Playwright {@link TimeoutError}, with the original timeout as
 * its cause. Page objects that catch the timeout to retry, or to read an absent
 * element as "not shown" (HelpCenterPage.open reloads once on it;
 * AiToolPage.waitForConfirmCharge returns false on it), behave exactly as they
 * did before healing existed.
 */
public class HealingRejectedError extends TimeoutError {

    private final transient HealingResult result;

    public HealingRejectedError(HealingResult result, TimeoutError cause) {
        super(result.rejectionMessage(cause), cause);
        this.result = result;
    }

    public HealingResult result() {
        return result;
    }
}
