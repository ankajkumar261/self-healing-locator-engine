package com.portfolio.selfhealing.healing.core;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.healing.core.HealingConfig.Mode;
import com.portfolio.selfhealing.healing.core.HealingResult.Outcome;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Engine decisions against a fake screen: no browser involved.
 */
public class HealingEngineTest {

    private static final LocatorKey SUBMIT = new LocatorKey("LoginPage", "button.auth-view__submit", "SIGN_IN_BUTTON");

    private Path dir;

    @BeforeMethod(alwaysRun = true)
    public void freshDir() throws IOException {
        dir = Files.createTempDirectory("healing-engine");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A clear winner above the threshold heals and is remembered")
    public void healsClearWinner() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        recordBaseline(engine, Fingerprints.submit().build());

        FakeScreen screen = new FakeScreen(Fingerprints.navSignIn().build(),
                Fingerprints.submit().classes("auth-form__cta").build(), Fingerprints.forgotPassword().build());
        HealingResult r = engine.heal(SUBMIT, Intent.CLICK, screen);

        Assert.assertEquals(r.outcome(), Outcome.HEALED, r.reason());
        Assert.assertEquals(r.healedSelector(), "fake:1");
        Assert.assertTrue(r.confidence() >= 0.90);
        Assert.assertEquals(engine.store().find(SUBMIT.id()).orElseThrow().heal().selector(), "fake:1");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Two equally good matches are ambiguous, so neither is used")
    public void rejectsTwins() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        recordBaseline(engine, Fingerprints.submit().build());

        ElementFingerprint twin = Fingerprints.submit().classes("auth-form__cta").build();
        HealingResult r = engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(twin, twin));

        Assert.assertEquals(r.outcome(), Outcome.REJECTED_AMBIGUOUS, r.reason());
        Assert.assertNull(engine.store().find(SUBMIT.id()).orElseThrow().heal());
        Assert.assertTrue(engine.store().find(SUBMIT.id()).orElseThrow().events().isEmpty(),
                "rejections must not be written to the committed history");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A wait on an element with no baseline is left to time out, unsearched")
    public void waitWithoutBaselineIsNotSearched() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        FakeScreen screen = new FakeScreen(Fingerprints.submit().build());

        HealingResult r = engine.heal(SUBMIT, Intent.WAIT, screen);

        Assert.assertEquals(r.outcome(), Outcome.NOT_ATTEMPTED);
        Assert.assertEquals(screen.snapshots.get(), 0);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A delete control is vetoed even when it is otherwise a perfect match")
    public void vetoesDestructiveWinner() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        ElementFingerprint open = ElementFingerprint.builder().tag("button").role("button").text("Open")
                .classes("history-api__row-btn").ancestors("div.history-api__row").build();
        recordBaseline(engine, open);

        ElementFingerprint deleteLookAlike = ElementFingerprint.builder().tag("button").role("button").text("Open")
                .ariaLabel("Delete entry").classes("history-api__row-btn").ancestors("div.history-api__row").build();
        HealingResult r = engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(deleteLookAlike));

        Assert.assertEquals(r.outcome(), Outcome.REJECTED_UNSAFE, r.reason());
        Assert.assertTrue(r.reason().contains("destructive"), r.reason());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "With no recorded baseline, selector-only evidence is capped below the threshold")
    public void noBaselineNeverHeals() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        ElementFingerprint close = ElementFingerprint.builder().tag("button").role("button")
                .classes("auth-view__submit-v2").build();

        HealingResult r = engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(close));

        Assert.assertEquals(r.outcome(), Outcome.REJECTED_LOW_CONFIDENCE, r.reason());
        Assert.assertTrue(r.confidence() <= 0.85);
        Assert.assertTrue(r.reason().contains("no baseline"), r.reason());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Credit-spending tests only suggest the fix; nothing is healed or stored")
    public void reportOnlyGroupSuggests() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(AppConstants.GROUP_GENERATION), AiLocatorAdvisor.NONE);
        recordBaseline(engine, Fingerprints.submit().build());

        HealingResult r = engine.heal(SUBMIT, Intent.CLICK,
                new FakeScreen(Fingerprints.submit().classes("auth-form__cta").build()));

        Assert.assertEquals(r.outcome(), Outcome.REJECTED_REPORT_ONLY, r.reason());
        Assert.assertEquals(r.healedSelector(), "fake:0", "the suggestion should still be given");
        Assert.assertNull(engine.store().find(SUBMIT.id()).orElseThrow().heal());
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "A boolean probe never searches the screen")
    public void probesNeverSearch() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        recordBaseline(engine, Fingerprints.submit().build());
        FakeScreen screen = new FakeScreen(Fingerprints.submit().classes("auth-form__cta").build());

        HealingResult r = engine.heal(SUBMIT, Intent.PROBE, screen);

        Assert.assertEquals(r.outcome(), Outcome.NOT_ATTEMPTED);
        Assert.assertEquals(screen.snapshots.get(), 0);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An approved heal is reused next run without a search, and dropped once it stops holding")
    public void approvedHealIsReusedThenDropped() {
        Path store = dir.resolve("history.json");
        HealingEngine first = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE, store);
        recordBaseline(first, Fingerprints.submit().build());
        FakeScreen renamed = new FakeScreen(Fingerprints.submit().classes("auth-form__cta").build());
        Assert.assertEquals(first.heal(SUBMIT, Intent.CLICK, renamed).outcome(), Outcome.HEALED);
        first.finishRun();

        HealingEngine next = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE, store);
        FakeScreen again = new FakeScreen(Fingerprints.submit().classes("auth-form__cta").build());
        HealingResult reused = next.heal(SUBMIT, Intent.CLICK, again);
        Assert.assertEquals(reused.outcome(), Outcome.HEALED_FROM_HISTORY, reused.reason());
        Assert.assertEquals(again.snapshots.get(), 0, "a reused heal must not search the screen");

        HealingResult stale = next.useKnownHeal(SUBMIT, Intent.CLICK, new FakeScreen());
        Assert.assertEquals(stale.outcome(), Outcome.STALE_HEAL_DROPPED);
        Assert.assertNull(next.store().find(SUBMIT.id()).orElseThrow().heal());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "When the original selector works again, the stored heal is retired")
    public void originalWorkingRetiresHeal() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), AiLocatorAdvisor.NONE);
        recordBaseline(engine, Fingerprints.submit().build());
        engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(Fingerprints.submit().classes("auth-form__cta").build()));

        engine.originalWorked(SUBMIT, () -> Optional.of(Fingerprints.submit().build()), "/");

        Assert.assertNull(engine.store().find(SUBMIT.id()).orElseThrow().heal());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "AI is asked about a near miss and may approve it at or above the threshold")
    public void aiApprovesNearMiss() {
        FakeAdvisor advisor = new FakeAdvisor(0, 0.95);
        HealingEngine engine = engine(Mode.HEAL, Set.of(), advisor);
        recordBaseline(engine, Fingerprints.submit().build());

        HealingResult r = engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(nearMiss()));

        Assert.assertEquals(advisor.calls.get(), 1);
        Assert.assertEquals(r.outcome(), Outcome.HEALED_BY_AI, r.reason());
        Assert.assertEquals(r.source(), "AI");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An AI answer below the threshold changes nothing")
    public void aiBelowThresholdIsIgnored() {
        HealingEngine engine = engine(Mode.HEAL, Set.of(), new FakeAdvisor(0, 0.80));
        recordBaseline(engine, Fingerprints.submit().build());

        HealingResult r = engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(nearMiss()));

        Assert.assertEquals(r.outcome(), Outcome.REJECTED_LOW_CONFIDENCE);
        Assert.assertTrue(r.reason().contains("AI fallback did not find a safe match"), r.reason());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "AI is not consulted when nothing comes close (below healing.ai.min.score)")
    public void aiIsNotAskedAboutPoorMatches() {
        FakeAdvisor advisor = new FakeAdvisor(0, 0.99);
        HealingEngine engine = engine(Mode.HEAL, Set.of(), advisor);
        recordBaseline(engine, Fingerprints.submit().build());

        engine.heal(SUBMIT, Intent.CLICK, new FakeScreen(Fingerprints.navSignIn().build()));

        Assert.assertEquals(advisor.calls.get(), 0);
    }

    /** Same button with a renamed class and reworded label: plausible, but under 90%. */
    private static ElementFingerprint nearMiss() {
        return Fingerprints.submit().classes("auth-form__cta").text("Log in").build();
    }

    // ---- Fixtures ----

    private HealingEngine engine(Mode mode, Set<String> groups, AiLocatorAdvisor advisor) {
        return engine(mode, groups, advisor, dir.resolve("history.json"));
    }

    private HealingEngine engine(Mode mode, Set<String> groups, AiLocatorAdvisor advisor, Path store) {
        HealingConfig config = HealingConfig.defaults().withMode(mode)
                .withPaths(store.toString(), dir.resolve("report.json").toString())
                .withAi(advisor != AiLocatorAdvisor.NONE);
        TestContextProvider tests = () -> Optional.of(
                new TestContextProvider.TestInfo("FakeTest", "run", true, groups));
        return new HealingEngine(config, new JsonHealingStore(store, 20), advisor, tests, List.of());
    }

    private static void recordBaseline(HealingEngine engine, ElementFingerprint fp) {
        engine.originalWorked(SUBMIT, () -> Optional.of(fp), "/");
    }

    /** A screen whose element at index i is addressed by the selector "fake:i". */
    private static final class FakeScreen implements UiDriverAdapter {
        final List<ElementFingerprint> elements;
        final AtomicInteger snapshots = new AtomicInteger();

        FakeScreen(ElementFingerprint... elements) {
            this.elements = List.of(elements);
        }

        @Override
        public String platform() {
            return "fake";
        }

        @Override
        public String location() {
            return "/fake";
        }

        @Override
        public int count(String selector) {
            return index(selector) >= 0 ? 1 : 0;
        }

        @Override
        public Optional<ElementFingerprint> describe(String selector) {
            int i = index(selector);
            return i >= 0 ? Optional.of(elements.get(i)) : Optional.empty();
        }

        @Override
        public List<Candidate> snapshot(int limit) {
            snapshots.incrementAndGet();
            List<Candidate> out = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                out.add(new Candidate(i, elements.get(i)));
            }
            return out;
        }

        @Override
        public List<String> selectorsFor(Candidate candidate, int max) {
            return List.of("fake:" + candidate.handle());
        }

        private int index(String selector) {
            if (!selector.startsWith("fake:")) {
                return -1;
            }
            int i = Integer.parseInt(selector.substring(5));
            return i < elements.size() ? i : -1;
        }
    }

    private static final class FakeAdvisor implements AiLocatorAdvisor {
        final AtomicInteger calls = new AtomicInteger();
        final int choice;
        final double confidence;

        FakeAdvisor(int choice, double confidence) {
            this.choice = choice;
            this.confidence = confidence;
        }

        @Override
        public Optional<AiVerdict> choose(AiRequest request) {
            calls.incrementAndGet();
            return Optional.of(new AiVerdict(choice, confidence, "same submit button, reworded"));
        }
    }
}
