package com.portfolio.selfhealing.healing.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.selfhealing.healing.core.HealingEventSink;
import com.portfolio.selfhealing.healing.core.HealingResult;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.StatusDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestResult;
import org.testng.Reporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Marks tests that passed only because a locator was healed.
 * <p>
 * Allure has a fixed set of statuses, so PASS_WITH_HEALING is expressed as a
 * passed test carrying a {@code PASS_WITH_HEALING} tag, a status message naming
 * each healed locator, a step, and the evidence as a JSON attachment. The
 * categories file written by {@link #writeCategories()} gathers them under
 * "Passed with healing". Allure's TestNG adapter only sets the status when a test
 * passes, so a message written while the test runs survives into the report.
 * <p>
 * A heal during class-level setup (the shared login in SingleSessionTest) has no
 * test to mark yet, so it is held and stamped onto every test of that class.
 */
public final class AllureHealingReporter implements HealingEventSink {

    public static final String TAG = "PASS_WITH_HEALING";
    /** TestNG result attribute holding the locators healed during that test. */
    public static final String HEALED_ATTRIBUTE = "healing.healed";

    private static final Logger log = LogManager.getLogger(AllureHealingReporter.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private static final String CATEGORIES = """
            [
              {
                "name": "Passed with healing",
                "description": "Passed only because a broken locator was healed. Fix the page object.",
                "matchedStatuses": ["passed"],
                "messageRegex": ".*PASS_WITH_HEALING.*"
              },
              {
                "name": "Healing rejected (broken locator)",
                "description": "A locator matched nothing and no replacement was safe to use.",
                "matchedStatuses": ["failed", "broken"],
                "messageRegex": ".*HEALING_REJECTED.*"
              }
            ]
            """;

    /** Heals from class-level setup, by test class, waiting to be stamped onto its tests. */
    private static final Map<String, List<HealingResult>> CLASS_HEALS = new ConcurrentHashMap<>();
    /** Locators already stamped onto each Allure test case, by test-case uuid. */
    private static final Map<String, Map<String, HealingResult>> STAMPED = new ConcurrentHashMap<>();

    @Override
    public void onResult(HealingResult r) {
        ITestResult test = Reporter.getCurrentTestResult();
        if (r.healed()) {
            if (test != null && !test.getMethod().isTest()) {
                List<HealingResult> held = CLASS_HEALS.computeIfAbsent(test.getTestClass().getName(),
                        k -> new CopyOnWriteArrayList<>());
                if (held.stream().noneMatch(h -> h.key().equals(r.key()))) {
                    held.add(r);
                    attach("Healing evidence (setup) - " + r.locator(), r);
                }
                return;
            }
            stamp(test, r);
        }
        // Rejections are not attached: the caller may legitimately expect the
        // timeout, and when it fails the test the message already holds the evidence.
    }

    /** Stamps heals from the class's setup onto the test now running. */
    public static void applyPendingClassHeals() {
        if (CLASS_HEALS.isEmpty()) {
            return;
        }
        ITestResult test = Reporter.getCurrentTestResult();
        if (test == null || !test.getMethod().isTest()) {
            return;
        }
        List<HealingResult> held = CLASS_HEALS.get(test.getTestClass().getName());
        if (held != null) {
            held.forEach(r -> stamp(test, r));
        }
    }

    /** Writes Allure's categories.json so healed passes and rejections get their own buckets. */
    public static void writeCategories() {
        Path dir = Path.of(System.getProperty("allure.results.directory", "allure-results"));
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("categories.json"), CATEGORIES);
        } catch (IOException e) {
            log.warn("Could not write Allure categories to {}: {}", dir, e.getMessage());
        }
    }

    private static void stamp(ITestResult test, HealingResult r) {
        Optional<String> uuid = Allure.getLifecycle().getCurrentTestCase();
        if (uuid.isEmpty()) {
            return;
        }
        Map<String, HealingResult> healed = STAMPED.computeIfAbsent(uuid.get(),
                k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        if (healed.putIfAbsent(r.key(), r) != null) {
            return;
        }
        String message;
        synchronized (healed) {
            message = TAG + ": " + healed.size() + " locator(s) healed - " + healed.values().stream()
                    .map(h -> h.locator() + " -> " + h.healedSelector() + " (" + h.percent() + ")")
                    .collect(Collectors.joining("; "));
        }
        Allure.getLifecycle().updateTestCase(uuid.get(), tc -> {
            if (tc.getLabels().stream().noneMatch(l -> "tag".equals(l.getName()) && TAG.equals(l.getValue()))) {
                tc.getLabels().add(new Label().setName("tag").setValue(TAG));
            }
            if (tc.getStatusDetails() == null) {
                tc.setStatusDetails(new StatusDetails());
            }
            tc.getStatusDetails().setMessage(message);
        });
        Allure.step("Self-healed " + r.locator() + ": '" + r.originalSelector() + "' -> '"
                + r.healedSelector() + "' (" + r.percent() + ")");
        attach("Healing evidence - " + r.locator(), r);
        if (test != null) {
            test.setAttribute(HEALED_ATTRIBUTE, List.copyOf(healed.keySet()));
        }
    }

    private static void attach(String name, HealingResult r) {
        try {
            Allure.addAttachment(name, "application/json", JSON.writeValueAsString(r), ".json");
        } catch (JsonProcessingException | RuntimeException e) {
            log.debug("Could not attach healing evidence: {}", e.getMessage());
        }
    }
}
