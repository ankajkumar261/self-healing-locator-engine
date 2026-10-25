package com.portfolio.selfhealing.healing.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.selfhealing.healing.Healing;
import com.portfolio.selfhealing.healing.core.HealingEngine;
import com.portfolio.selfhealing.healing.core.HealingResult;
import com.portfolio.selfhealing.healing.core.Intent;
import com.portfolio.selfhealing.healing.core.LocatorKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Resolves a page object's selector to a Locator, healing it when it has
 * stopped matching. This is the only class BasePage talks to.
 * <p>
 * Rules, in order:
 * <ul>
 *   <li>The original selector is always tried first, with the caller's timeout.
 *       When an approved heal exists, both are waited for together and the
 *       original wins whenever it is present, so no time is spent on a probe.</li>
 *   <li>If the original matches an element that is merely hidden or covered,
 *       nothing is healed: that is the app's state, not a broken locator.</li>
 *   <li>Only when it matches nothing does the engine search for a replacement.</li>
 *   <li>Every failure is still a Playwright TimeoutError (see {@link HealingRejectedError}),
 *       so existing catch blocks keep working.</li>
 * </ul>
 */
public final class HealingLocator {

    private static final Logger log = LogManager.getLogger(HealingLocator.class);
    /** A healed element was visible a moment ago; it should not need long. */
    private static final double HEALED_SETTLE_MS = 5_000;
    private static final Map<Class<?>, Map<String, String>> CONSTANT_NAMES = new ConcurrentHashMap<>();

    private final Page page;
    private final Class<?> owner;
    private final PlaywrightAdapter driver;

    public HealingLocator(Page page, Class<?> owner) {
        this.page = page;
        this.owner = owner;
        this.driver = new PlaywrightAdapter(page);
    }

    /** Waits for the element to be visible, healing the selector if needed. */
    public Locator resolve(String selector, Intent intent) {
        return resolve(selector, intent, WaitForSelectorState.VISIBLE, null);
    }

    /**
     * @param state     what to wait for before returning
     * @param timeoutMs null for the page's default timeout
     */
    public Locator resolve(String selector, Intent intent, WaitForSelectorState state, Double timeoutMs) {
        Locator original = page.locator(selector).first();
        HealingEngine engine = Healing.engine();
        if (!engine.enabled()) {
            waitFor(original, state, timeoutMs);
            return original;
        }
        Healing.beforeAction();
        LocatorKey key = key(selector);
        Optional<String> known = engine.knownHeal(key);

        if (known.isEmpty()) {
            try {
                waitFor(original, state, timeoutMs);
            } catch (TimeoutError e) {
                return heal(engine, key, intent, state, e);
            }
            engine.originalWorked(key, () -> driver.describe(original), driver.location());
            return original;
        }

        // An approved heal exists: wait for whichever appears, preferring the original.
        try {
            waitFor(original.or(page.locator(known.get())).first(), state, timeoutMs);
        } catch (TimeoutError e) {
            return heal(engine, key, intent, state, e);
        }
        if (inState(original, state) || driver.count(selector) > 0) {
            // Present (if not yet visible) beats any heal: wait on it exactly as before.
            waitFor(original, state, timeoutMs);
            engine.originalWorked(key, () -> driver.describe(original), driver.location());
            return original;
        }
        HealingResult reused = engine.useKnownHeal(key, intent, driver);
        if (reused.healed()) {
            return page.locator(reused.healedSelector()).first();
        }
        return heal(engine, key, intent, state, new TimeoutError("'" + selector
                + "' matched nothing; only its stored heal did, and that heal no longer holds"));
    }

    /**
     * isVisible never waits and never searches: an absent element is often the
     * right answer. It only reuses a heal that was already approved.
     */
    public boolean isVisible(String selector) {
        Locator original = page.locator(selector).first();
        HealingEngine engine = Healing.engine();
        LocatorKey key = engine.enabled() ? key(selector) : null;
        if (original.isVisible()) {
            if (key != null) {
                engine.originalWorked(key, () -> driver.describe(original), driver.location());
            }
            return true;
        }
        if (key == null) {
            return false;
        }
        Optional<String> known = engine.knownHeal(key);
        if (known.isEmpty() || driver.count(selector) > 0 || !page.locator(known.get()).first().isVisible()) {
            return false;
        }
        Healing.beforeAction();
        return engine.useKnownHeal(key, Intent.PROBE, driver).healed();
    }

    /**
     * A waiting boolean check such as isEnabled: like the original, it fails if
     * the element never appears, and like isVisible it only reuses approved heals.
     */
    public <T> T probe(String selector, Function<Locator, T> read) {
        Locator original = page.locator(selector).first();
        HealingEngine engine = Healing.engine();
        if (!engine.enabled()) {
            return read.apply(original);
        }
        LocatorKey key = key(selector);
        Optional<String> known = engine.knownHeal(key);
        if (known.isPresent()) {
            try {
                original.or(page.locator(known.get())).first().waitFor(
                        new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
            } catch (TimeoutError ignored) {
                // Neither appeared: reading the original below fails exactly as it always did.
            }
            if (driver.count(selector) == 0 && driver.count(known.get()) == 1) {
                Healing.beforeAction();
                HealingResult reused = engine.useKnownHeal(key, Intent.PROBE, driver);
                if (reused.healed()) {
                    return read.apply(page.locator(reused.healedSelector()).first());
                }
            }
        }
        T value = read.apply(original);
        engine.originalWorked(key, () -> driver.describe(original), driver.location());
        return value;
    }

    private Locator heal(HealingEngine engine, LocatorKey key, Intent intent, WaitForSelectorState state,
                         TimeoutError timeout) {
        // Present but hidden, covered or disabled is the app's state. Healing it
        // onto some other visible element would hide a real bug.
        if (driver.count(key.selector()) > 0) {
            throw timeout;
        }
        HealingResult result;
        try {
            result = engine.heal(key, intent, driver);
        } catch (RuntimeException engineFailure) {
            // Healing must never make a failure worse: report the original error.
            log.warn("[HEAL] Healing {} failed internally: {}", key.displayName(), engineFailure.getMessage());
            timeout.addSuppressed(engineFailure);
            throw timeout;
        }
        if (result.healed()) {
            Locator healed = page.locator(result.healedSelector()).first();
            waitFor(healed, state, HEALED_SETTLE_MS);
            return healed;
        }
        if (result.outcome() == HealingResult.Outcome.NOT_ATTEMPTED) {
            throw timeout;
        }
        throw new HealingRejectedError(result, timeout);
    }

    private LocatorKey key(String selector) {
        return new LocatorKey(owner.getSimpleName(), selector, constantName(owner, selector));
    }

    private static void waitFor(Locator locator, WaitForSelectorState state, Double timeoutMs) {
        Locator.WaitForOptions options = new Locator.WaitForOptions().setState(state);
        if (timeoutMs != null) {
            options.setTimeout(timeoutMs);
        }
        locator.waitFor(options);
    }

    private static boolean inState(Locator locator, WaitForSelectorState state) {
        try {
            return state == WaitForSelectorState.VISIBLE ? locator.isVisible() : locator.count() > 0;
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /**
     * The constant a selector is stored in, so logs say {@code LoginPage.SIGN_IN_BUTTON}
     * rather than repeating the selector. Read once per page class.
     */
    static String constantName(Class<?> owner, String selector) {
        return CONSTANT_NAMES.computeIfAbsent(owner, HealingLocator::stringConstants).get(selector);
    }

    private static Map<String, String> stringConstants(Class<?> type) {
        Map<String, String> names = new HashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int m = f.getModifiers();
                if (Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == String.class) {
                    try {
                        f.setAccessible(true);
                        names.putIfAbsent((String) f.get(null), f.getName());
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // An unreadable constant only costs a nicer log name.
                    }
                }
            }
        }
        return names;
    }
}
