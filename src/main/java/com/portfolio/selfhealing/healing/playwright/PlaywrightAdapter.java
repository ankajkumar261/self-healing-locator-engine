package com.portfolio.selfhealing.healing.playwright;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.portfolio.selfhealing.healing.core.Candidate;
import com.portfolio.selfhealing.healing.core.ElementFingerprint;
import com.portfolio.selfhealing.healing.core.UiDriverAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Playwright implementation of the healing driver interface.
 * <p>
 * Everything about the page is read by one in-page script
 * ({@code healing/dom-snapshot.js}), so a full snapshot is a single round trip.
 * Only the main frame is searched.
 */
public final class PlaywrightAdapter implements UiDriverAdapter {

    private static final String SCRIPT = loadScript();
    /** Calls the collector from page.evaluate, which passes no element. */
    private static final String PAGE_CALL = "arg => (" + SCRIPT + ")(null, arg)";
    private static final String SAME_ELEMENT =
            "(el, i) => !!window.__tsHeal && window.__tsHeal.els[i] === el";
    /** Describing an element that is known to exist should be instant. */
    private static final double DESCRIBE_TIMEOUT_MS = 2_000;

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Page page;

    public PlaywrightAdapter(Page page) {
        this.page = page;
    }

    @Override
    public String platform() {
        return "playwright";
    }

    @Override
    public String location() {
        String url = page.url();
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isEmpty() ? url : path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    @Override
    public int count(String selector) {
        try {
            return page.locator(selector).count();
        } catch (PlaywrightException e) {
            return -1;
        }
    }

    @Override
    public Optional<ElementFingerprint> describe(String selector) {
        return describe(page.locator(selector).first());
    }

    public Optional<ElementFingerprint> describe(Locator locator) {
        try {
            Object json = locator.evaluate(SCRIPT, Map.of("mode", "describe"),
                    new Locator.EvaluateOptions().setTimeout(DESCRIBE_TIMEOUT_MS));
            return Optional.of(JSON.readValue((String) json, ElementFingerprint.class).sanitized());
        } catch (PlaywrightException | IOException | ClassCastException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Candidate> snapshot(int limit) {
        Object json = page.evaluate(PAGE_CALL, Map.of("mode", "snapshot", "limit", limit));
        List<ElementFingerprint> elements = parse((String) json, new TypeReference<>() {
        });
        List<Candidate> candidates = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            candidates.add(new Candidate(i, elements.get(i).sanitized()));
        }
        return candidates;
    }

    /**
     * Asks the page for selectors that single out the candidate, then confirms
     * each through Playwright itself: it must match exactly one element, and that
     * element must be the candidate. Playwright-only syntax (role=, :text-is)
     * can only be checked this way.
     */
    @Override
    public List<String> selectorsFor(Candidate candidate, int max) {
        Object json = page.evaluate(PAGE_CALL, Map.of("mode", "selectors", "index", candidate.handle()));
        List<String> proposals = parse((String) json, new TypeReference<>() {
        });
        List<String> verified = new ArrayList<>();
        for (String selector : proposals) {
            if (verified.size() >= max) {
                break;
            }
            if (pointsAt(selector, candidate.handle())) {
                verified.add(selector);
            }
        }
        return verified;
    }

    @Override
    public void release() {
        try {
            page.evaluate(PAGE_CALL, Map.of("mode", "release"));
        } catch (PlaywrightException ignored) {
            // The page may have navigated away; nothing is left to release then.
        }
    }

    private boolean pointsAt(String selector, int handle) {
        try {
            Locator matches = page.locator(selector);
            return matches.count() == 1 && Boolean.TRUE.equals(matches.evaluate(SAME_ELEMENT, handle,
                    new Locator.EvaluateOptions().setTimeout(DESCRIBE_TIMEOUT_MS)));
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private static <T> T parse(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Unreadable output from the healing page script", e);
        }
    }

    private static String loadScript() {
        try (InputStream in = PlaywrightAdapter.class.getResourceAsStream("/healing/dom-snapshot.js")) {
            if (in == null) {
                throw new IllegalStateException("healing/dom-snapshot.js is missing from the classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
