package com.portfolio.selfhealing.healing.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.selfhealing.healing.core.HealingConfig.Mode;
import com.portfolio.selfhealing.healing.core.HealingResult.CandidateReport;
import com.portfolio.selfhealing.healing.core.HealingResult.Outcome;
import com.portfolio.selfhealing.healing.core.HealingStore.ActiveHeal;
import com.portfolio.selfhealing.healing.core.HealingStore.HealingEvent;
import com.portfolio.selfhealing.healing.core.HealingStore.LocatorRecord;
import com.portfolio.selfhealing.healing.core.SimilarityScorer.Score;
import com.portfolio.selfhealing.healing.core.TestContextProvider.TestInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Decides what to do when a locator stops matching.
 * <p>
 * The caller (a driver-specific locator such as the Playwright one) always tries
 * the original selector first and only asks the engine once it has matched
 * nothing. The engine then, in order:
 * <ol>
 *   <li>re-verifies a heal approved on an earlier run, if there is one;</li>
 *   <li>scores every visible element against the element's recorded baseline and
 *       heals only when the best one clears the threshold, is clearly ahead of the
 *       runner-up, and passes the {@link SafetyGuard};</li>
 *   <li>asks the AI advisor, when enabled, about near misses;</li>
 *   <li>otherwise rejects, with the closest candidates in the message.</li>
 * </ol>
 * No Playwright, TestNG or Allure types appear here, so a mobile driver can reuse it.
 */
public final class HealingEngine {

    private static final Logger log = LogManager.getLogger(HealingEngine.class);
    /** Unlike the history file, the run report reads top-down, so keys keep their order. */
    private static final ObjectMapper REPORT_JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    private static final int CANDIDATES_REPORTED = 5;
    private static final int SELECTORS_WANTED = 4;

    private final HealingConfig config;
    private final HealingStore store;
    private final AiLocatorAdvisor ai;
    private final TestContextProvider tests;
    private final List<HealingEventSink> sinks;
    private final SimilarityScorer scorer = new SimilarityScorer();
    private final SafetyGuard guard = new SafetyGuard();

    private final String runId = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    private final Set<String> observedThisRun = ConcurrentHashMap.newKeySet();
    private final List<HealingResult> runResults = new CopyOnWriteArrayList<>();
    private final Set<String> reportedThisRun = ConcurrentHashMap.newKeySet();
    private final AtomicInteger discoveries = new AtomicInteger();

    public HealingEngine(HealingConfig config, HealingStore store, AiLocatorAdvisor ai,
                         TestContextProvider tests, List<HealingEventSink> sinks) {
        this.config = config;
        this.store = store;
        this.ai = ai;
        this.tests = tests;
        this.sinks = List.copyOf(sinks);
    }

    public HealingConfig config() {
        return config;
    }

    public HealingStore store() {
        return store;
    }

    public boolean enabled() {
        return config.enabled();
    }

    /** How many times this engine searched a screen for a replacement. */
    public int discoveries() {
        return discoveries.get();
    }

    /** Every heal and rejection this run, once per locator, outcome and test. */
    public List<HealingResult> runResults() {
        return List.copyOf(runResults);
    }

    // ---- Called by the driver-specific locator ----

    /**
     * The selector of a heal approved on an earlier run, if there is one and the
     * running test may use it.
     */
    public Optional<String> knownHeal(LocatorKey key) {
        if (effectiveMode() != Mode.HEAL) {
            return Optional.empty();
        }
        return store.find(key.id()).map(LocatorRecord::heal).map(ActiveHeal::selector);
    }

    /**
     * The original selector worked. Records the element's baseline (once per run)
     * and retires any stored heal, since the page object no longer needs it.
     */
    public void originalWorked(LocatorKey key, Supplier<Optional<ElementFingerprint>> describe,
                               String location) {
        if (!enabled()) {
            return;
        }
        store.find(key.id()).filter(r -> r.heal() != null).ifPresent(r -> {
            store.dropHeal(key, event(Outcome.STALE_HEAL_DROPPED, null, null,
                    "original selector works again; stored heal to '" + r.heal().selector() + "' retired"));
            log.info("[HEAL] {} works again through its original selector; retired the stored heal.",
                    key.displayName());
        });
        if (observedThisRun.add(key.id())) {
            try {
                describe.get().ifPresent(fp -> store.observeBaseline(key, fp, location, now()));
            } catch (RuntimeException e) {
                log.debug("Could not record a baseline for {}: {}", key.displayName(), e.getMessage());
            }
        }
    }

    /**
     * Re-checks a heal approved on an earlier run before it is used: it must still
     * match exactly one element, that element must still score above the threshold
     * against the baseline, and it must pass the guard. A heal that fails is dropped.
     */
    public HealingResult useKnownHeal(LocatorKey key, Intent intent, UiDriverAdapter driver) {
        Optional<LocatorRecord> rec = store.find(key.id());
        if (effectiveMode() != Mode.HEAL || rec.isEmpty() || rec.get().heal() == null) {
            return notAttempted(key, intent, "no approved heal");
        }
        ActiveHeal heal = rec.get().heal();
        ElementFingerprint baseline = rec.get().baseline();

        String why;
        int matches = driver.count(heal.selector());
        Optional<ElementFingerprint> now = matches == 1 ? driver.describe(heal.selector()) : Optional.empty();
        if (now.isPresent() && baseline != null) {
            Score score = scorer.score(baseline, now.get());
            Optional<String> veto = guard.veto(baseline, now.get(), intent);
            if (score.total() >= config.threshold() && veto.isEmpty()) {
                store.markHealUsed(key.id(), runId, now());
                return publish(new HealingResult(Outcome.HEALED_FROM_HISTORY, key.displayName(), key.id(),
                        intent, key.selector(), heal.selector(), score.total(), "HISTORY (" + heal.source() + ")",
                        "heal approved " + heal.firstHealedAt() + " re-verified: " + score.reason(),
                        heal.alternatives(), List.of(), testName(), driver.location(), now()));
            }
            why = veto.orElse("now scores " + percent(score.total()) + ", below the threshold");
        } else {
            why = matches == 0 ? "it matches nothing" : matches > 1 ? "it matches " + matches + " elements"
                    : baseline == null ? "there is no baseline to verify it against" : "it could not be read";
        }

        String reason = "stored heal '" + heal.selector() + "' no longer holds: " + why;
        store.dropHeal(key, event(Outcome.STALE_HEAL_DROPPED, heal.selector(), null, reason));
        return publish(new HealingResult(Outcome.STALE_HEAL_DROPPED, key.displayName(), key.id(), intent,
                key.selector(), heal.selector(), null, "HISTORY", reason, List.of(), List.of(),
                testName(), driver.location(), now()));
    }

    /**
     * The original selector matched nothing within its timeout. Returns a healed
     * selector, or a rejection the caller turns into a test failure.
     */
    public HealingResult heal(LocatorKey key, Intent intent, UiDriverAdapter driver) {
        Mode mode = effectiveMode();
        if (mode == Mode.OFF) {
            return notAttempted(key, intent, "healing is off");
        }
        if (mode == Mode.HEAL && knownHeal(key).isPresent()) {
            HealingResult known = useKnownHeal(key, intent, driver);
            if (known.healed()) {
                return known;
            }
        }
        if (!intent.allowsDiscovery()) {
            return notAttempted(key, intent, intent + " never searches for a replacement");
        }
        if (intent == Intent.WAIT && store.find(key.id()).map(LocatorRecord::baseline).isEmpty()) {
            // A wait on an element never seen is usually a presence check that is
            // allowed to time out (an optional dialog). Without a baseline nothing
            // could heal anyway, so searching would only add noise to a normal "no".
            return notAttempted(key, intent, "no baseline recorded; a wait is left to time out as before");
        }
        try {
            return discover(key, intent, driver, mode);
        } finally {
            driver.release();
        }
    }

    /** Flushes history and writes the run report. Safe to call more than once. */
    public void finishRun() {
        store.flush();
        writeRunReport();
    }

    // ---- Discovery ----

    private record Ranked(Candidate candidate, Score score, ElementFingerprint expected, String veto) {
        double total() {
            return score.total();
        }
    }

    private HealingResult discover(LocatorKey key, Intent intent, UiDriverAdapter driver, Mode mode) {
        discoveries.incrementAndGet();
        ElementFingerprint baseline = store.find(key.id()).map(LocatorRecord::baseline).orElse(null);
        boolean hintsOnly = baseline == null;
        List<ElementFingerprint> expected = hintsOnly ? SelectorHints.fromSelector(key.selector()) : List.of(baseline);
        String location = driver.location();

        if (expected.isEmpty()) {
            return conclude(key, intent, mode, Outcome.REJECTED_LOW_CONFIDENCE, null, null,
                    "no baseline has been recorded for this locator and its selector names nothing to look for",
                    List.of(), location, null);
        }

        List<Ranked> ranked = new ArrayList<>();
        for (Candidate c : driver.snapshot(config.snapshotLimit())) {
            Ranked best = null;
            for (ElementFingerprint e : expected) {
                Score s = scorer.score(e, c.fingerprint());
                if (best == null || s.total() > best.total()) {
                    best = new Ranked(c, s, e, null);
                }
            }
            if (best != null) {
                String veto = guard.veto(best.expected(), c.fingerprint(), intent).orElse(null);
                ranked.add(new Ranked(c, best.score(), best.expected(), veto));
            }
        }
        ranked.sort(Comparator.comparingDouble(Ranked::total).reversed());
        List<CandidateReport> report = report(ranked);
        List<Ranked> safe = ranked.stream().filter(r -> r.veto() == null).toList();

        Ranked best = safe.isEmpty() ? null : safe.get(0);
        Ranked runnerUp = safe.size() > 1 ? safe.get(1) : null;
        double confidence = best == null ? 0 : best.total();
        if (hintsOnly) {
            confidence = Math.min(confidence, config.noBaselineCap());
        }
        String noBaseline = hintsOnly ? "; no baseline was ever recorded for this locator, so selector-only "
                + "evidence is capped at " + percent(config.noBaselineCap()) : "";

        // An excluded element that would have won is the most important thing to say.
        Optional<Ranked> blocked = ranked.stream().filter(r -> r.veto() != null)
                .filter(r -> r.total() >= config.threshold())
                .filter(r -> best == null || r.total() > best.total()).findFirst();
        if (blocked.isPresent()) {
            return conclude(key, intent, mode, Outcome.REJECTED_UNSAFE, null, blocked.get().total(),
                    "the closest element " + blocked.get().candidate().fingerprint().summary() + " scored "
                            + percent(blocked.get().total()) + " but " + blocked.get().veto(),
                    report, location, null);
        }
        if (best == null) {
            return conclude(key, intent, mode, Outcome.REJECTED_LOW_CONFIDENCE, null, null,
                    "no visible element could stand in for it" + noBaseline, report, location, null);
        }

        Outcome outcome;
        String reason;
        if (confidence < config.threshold()) {
            outcome = Outcome.REJECTED_LOW_CONFIDENCE;
            reason = "best candidate " + best.candidate().fingerprint().summary() + " scored " + percent(confidence)
                    + ", below the " + percent(config.threshold()) + " threshold (" + best.score().reason() + ")"
                    + noBaseline;
        } else if (runnerUp != null && best.total() - runnerUp.total() < config.ambiguityMargin()) {
            outcome = Outcome.REJECTED_AMBIGUOUS;
            reason = "two elements match almost equally: " + best.candidate().fingerprint().summary() + " at "
                    + percent(best.total()) + " and " + runnerUp.candidate().fingerprint().summary() + " at "
                    + percent(runnerUp.total()) + " (needs a lead of " + percent(config.ambiguityMargin()) + ")";
        } else {
            outcome = Outcome.HEALED;
            reason = best.score().reason();
        }

        if (outcome != Outcome.HEALED) {
            if (mode == Mode.HEAL && !hintsOnly && config.aiEnabled() && ai.available()
                    && best.total() >= config.aiMinScore()) {
                Optional<HealingResult> byAi = askAi(key, intent, driver, baseline, safe, report, location);
                if (byAi.isPresent()) {
                    return byAi.get();
                }
                reason += "; the AI fallback did not find a safe match";
            }
            return conclude(key, intent, mode, outcome, null, confidence, reason, report, location, null);
        }

        List<String> selectors = driver.selectorsFor(best.candidate(), SELECTORS_WANTED);
        if (selectors.isEmpty()) {
            return conclude(key, intent, mode, Outcome.REJECTED_NO_SELECTOR, null, confidence,
                    best.candidate().fingerprint().summary() + " matches (" + reason
                            + "), but no selector points at it alone", report, location, null);
        }
        return conclude(key, intent, mode, Outcome.HEALED, selectors, confidence, reason, report, location,
                "DOM_SIMILARITY");
    }

    private Optional<HealingResult> askAi(LocatorKey key, Intent intent, UiDriverAdapter driver,
                                          ElementFingerprint baseline, List<Ranked> safe,
                                          List<CandidateReport> report, String location) {
        List<Ranked> shown = safe.subList(0, Math.min(config.aiMaxCandidates(), safe.size()));
        List<AiLocatorAdvisor.Option> options = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            options.add(new AiLocatorAdvisor.Option(i, shown.get(i).total(), shown.get(i).candidate().fingerprint()));
        }
        Optional<AiLocatorAdvisor.AiVerdict> verdict;
        try {
            verdict = ai.choose(new AiLocatorAdvisor.AiRequest(key, intent, baseline, options));
        } catch (RuntimeException e) {
            log.warn("[HEAL] AI advisor failed for {}: {}", key.displayName(), e.getMessage());
            return Optional.empty();
        }
        if (verdict.isEmpty() || verdict.get().optionIndex() == null) {
            return Optional.empty();
        }
        int index = verdict.get().optionIndex();
        if (index < 0 || index >= shown.size()) {
            log.warn("[HEAL] AI advisor chose option {} of {} for {}; ignored.", index, shown.size(), key.displayName());
            return Optional.empty();
        }
        Ranked chosen = shown.get(index);
        if (verdict.get().confidence() < config.threshold() || chosen.total() < config.aiMinScore()) {
            return Optional.empty();
        }
        List<String> selectors = driver.selectorsFor(chosen.candidate(), SELECTORS_WANTED);
        if (selectors.isEmpty()) {
            return Optional.empty();
        }
        String reason = "AI chose " + chosen.candidate().fingerprint().summary() + " (similarity "
                + percent(chosen.total()) + "): " + verdict.get().reason();
        return Optional.of(conclude(key, intent, Mode.HEAL, Outcome.HEALED_BY_AI, selectors,
                verdict.get().confidence(), reason, report, location, "AI"));
    }

    /** Applies report-only mode, records the decision and tells the sinks. */
    private HealingResult conclude(LocatorKey key, Intent intent, Mode mode, Outcome outcome,
                                   List<String> selectors, Double confidence, String reason,
                                   List<CandidateReport> candidates, String location, String source) {
        String healed = selectors == null || selectors.isEmpty() ? null : selectors.get(0);
        List<String> alternatives = selectors == null || selectors.size() < 2 ? List.of()
                : selectors.subList(1, selectors.size());

        if (outcome.healed() && mode == Mode.REPORT) {
            outcome = Outcome.REJECTED_REPORT_ONLY;
            reason = "report-only for this test (" + reportOnlyWhy() + "); it would have healed: " + reason;
        }
        HealingResult result = new HealingResult(outcome, key.displayName(), key.id(), intent, key.selector(),
                healed, confidence, source, reason, alternatives, candidates, testName(), location, now());

        if (outcome.healed()) {
            store.saveHeal(key, new ActiveHeal(healed, confidence, source, reason, alternatives,
                    now(), now(), runId, 1));
            store.addEvent(key, event(outcome, healed, confidence, reason));
        }
        // Rejections stay out of the committed history: a wait that is allowed to
        // time out would otherwise rewrite the file on every run. They are in the
        // log, the run report and the failure message.
        return publish(result);
    }

    private HealingResult publish(HealingResult result) {
        if (reportedThisRun.add(result.key() + "|" + result.outcome() + "|" + result.test())) {
            runResults.add(result);
        }
        for (HealingEventSink sink : sinks) {
            try {
                sink.onResult(result);
            } catch (RuntimeException e) {
                log.warn("Healing sink {} failed: {}", sink.getClass().getSimpleName(), e.getMessage());
            }
        }
        return result;
    }

    // ---- Helpers ----

    /** Tests in report-only groups (credit-spending ones) never heal, they only suggest. */
    private Mode effectiveMode() {
        if (config.mode() != Mode.HEAL) {
            return config.mode();
        }
        boolean reportOnly = tests.current().map(TestInfo::groups)
                .map(groups -> groups.stream().anyMatch(config.reportOnlyGroups()::contains))
                .orElse(false);
        return reportOnly ? Mode.REPORT : Mode.HEAL;
    }

    private String reportOnlyWhy() {
        if (config.mode() == Mode.REPORT) {
            return "healing.mode=report";
        }
        return "group " + tests.current().map(TestInfo::groups).orElse(Set.of()).stream()
                .filter(config.reportOnlyGroups()::contains).findFirst().orElse("?");
    }

    private List<CandidateReport> report(List<Ranked> ranked) {
        List<CandidateReport> out = new ArrayList<>();
        for (int i = 0; i < Math.min(CANDIDATES_REPORTED, ranked.size()); i++) {
            Ranked r = ranked.get(i);
            out.add(new CandidateReport(i + 1, round(r.total()), r.candidate().fingerprint().summary(),
                    r.veto() != null ? r.veto() : r.score().reason(), r.veto() != null ? true : null));
        }
        return out;
    }

    private HealingResult notAttempted(LocatorKey key, Intent intent, String reason) {
        return new HealingResult(Outcome.NOT_ATTEMPTED, key.displayName(), key.id(), intent, key.selector(),
                null, null, null, reason, List.of(), List.of(), testName(), null, now());
    }

    private HealingEvent event(Outcome outcome, String healed, Double confidence, String reason) {
        return new HealingEvent(now(), runId, testName(), outcome.name(), healed,
                confidence == null ? null : round(confidence), reason);
    }

    private String testName() {
        return tests.current().map(TestInfo::displayName).orElse(null);
    }

    private void writeRunReport() {
        Path path = Path.of(config.reportPath());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", runId);
        report.put("mode", config.mode());
        report.put("threshold", config.threshold());
        report.put("healed", runResults.stream().filter(HealingResult::healed).count());
        report.put("rejected", runResults.stream().filter(r -> r.outcome().rejected()).count());
        report.put("suggestedFixes", runResults.stream()
                .filter(r -> r.healedSelector() != null && (r.healed() || r.outcome() == Outcome.REJECTED_REPORT_ONLY))
                .map(r -> r.locator() + ": replace '" + r.originalSelector() + "' with '" + r.healedSelector() + "'")
                .distinct().toList());
        report.put("results", runResults);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            REPORT_JSON.writeValue(path.toFile(), report);
        } catch (IOException e) {
            log.warn("Could not write the healing run report to {}: {}", path, e.getMessage());
        }
    }

    private static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }
}
