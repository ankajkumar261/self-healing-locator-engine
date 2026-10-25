package com.portfolio.selfhealing.healing;

import com.microsoft.playwright.TimeoutError;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.healing.core.AiLocatorAdvisor;
import com.portfolio.selfhealing.healing.core.HealingConfig;
import com.portfolio.selfhealing.healing.core.HealingConfig.Mode;
import com.portfolio.selfhealing.healing.core.HealingEngine;
import com.portfolio.selfhealing.healing.core.HealingResult;
import com.portfolio.selfhealing.healing.core.HealingResult.Outcome;
import com.portfolio.selfhealing.healing.core.JsonHealingStore;
import com.portfolio.selfhealing.healing.playwright.HealingRejectedError;
import com.portfolio.selfhealing.healing.report.AllureHealingReporter;
import com.portfolio.selfhealing.healing.report.HealingLog;
import com.portfolio.selfhealing.healing.report.TestNgContextProvider;
import com.portfolio.selfhealing.tests.BaseTest;
import com.portfolio.selfhealing.utils.PlaywrightManager;
import io.qameta.allure.Allure;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Self-healing end to end in a real browser: BasePage helpers, the in-page
 * collector, selector verification, history and the Allure marker.
 * <p>
 * Each scenario renders the same sign-in fixture with page.setContent(), first
 * as written (so the baseline is recorded the way a normal passing run records
 * it), then with one deliberate change. No live site, no login, no credits.
 */
public class SelfHealingFixtureTest extends BaseTest {

    private static final String LOGIN = fixture("/healing-fixtures/login.html");
    private static final String SUBMIT_HTML =
            "<button type=\"submit\" class=\"auth-view__submit\" data-k=\"submit\">Sign in</button>";
    /** Healing runs only after the original times out, so keep that short here. */
    private static final double FIXTURE_TIMEOUT_MS = 2_000;

    private Path dir;
    private HealingEngine engine;
    private HealingEngine previous;

    @BeforeMethod(alwaysRun = true)
    public void isolateHealing() throws IOException {
        dir = Files.createTempDirectory("healing-fixture");
        engine = newEngine(Mode.HEAL);
        previous = Healing.install(engine);
        PlaywrightManager.getPage().setDefaultTimeout(FIXTURE_TIMEOUT_MS);
    }

    @AfterMethod(alwaysRun = true)
    public void restoreHealing() {
        Healing.install(previous);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A renamed class is healed onto the same button and marked PASS_WITH_HEALING")
    public void renamedClassIsHealed() {
        FixturePage page = new FixturePage().show(LOGIN);
        page.click(FixturePage.SUBMIT);

        page.show(change(LOGIN, "class=\"auth-view__submit\"", "class=\"auth-form__cta\""));
        page.click(FixturePage.SUBMIT);

        Assert.assertEquals(page.lastClicked(), "submit", "the heal must click the same button");
        HealingResult r = only(Outcome.HEALED);
        Assert.assertEquals(r.locator(), "FixturePage.SUBMIT");
        Assert.assertTrue(r.confidence() >= 0.90, "confidence " + r.confidence());
        logger.info("Healed to '{}' (alternatives {}) because {}", r.healedSelector(), r.alternatives(), r.reason());

        String message = currentAllureMessage();
        Assert.assertNotNull(message, "the running test should carry an Allure status message");
        Assert.assertTrue(message.startsWith(AllureHealingReporter.TAG), message);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An input whose id changed is healed by its name, placeholder and label")
    public void renamedInputIdIsHealed() {
        FixturePage page = new FixturePage().show(LOGIN);
        page.fill(FixturePage.EMAIL, "first@example.test");

        page.show(change(LOGIN, "auth-email", "login-email"));
        page.fill(FixturePage.EMAIL, "second@example.test");

        Assert.assertEquals(page.valueOf("input[name='email']"), "second@example.test");
        Assert.assertEquals(page.valueOf("input[name='password']"), "", "nothing may land in the password field");
        only(Outcome.HEALED);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A removed button is not replaced by look-alikes (nav 'Sign in', 'Forgot password?')")
    public void removedButtonIsRejected() {
        FixturePage page = new FixturePage().show(LOGIN);
        page.click(FixturePage.SUBMIT);

        page.show(change(LOGIN, SUBMIT_HTML, ""));
        TimeoutError e = Assert.expectThrows(TimeoutError.class, () -> page.click(FixturePage.SUBMIT));

        Assert.assertTrue(e instanceof HealingRejectedError, "expected a rejection, got " + e);
        Assert.assertTrue(e.getMessage().startsWith(HealingResult.REJECTION_MARKER), e.getMessage());
        Assert.assertTrue(e.getCause() instanceof TimeoutError, "the original timeout is kept as the cause");
        Assert.assertNull(page.lastClicked(), "nothing may be clicked");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A password field is never healed onto a plain text field, however similar")
    public void passwordNeverHealsIntoTextField() {
        FixturePage page = new FixturePage().show(LOGIN);
        page.fill(FixturePage.PASSWORD, "secret-one");

        String plain = change(LOGIN, "id=\"auth-password\" class=\"auth-view__input\" type=\"password\"",
                "id=\"auth-password-plain\" class=\"auth-view__input\" type=\"text\"")
                .replace("for=\"auth-password\"", "for=\"auth-password-plain\"");
        page.show(plain);
        HealingRejectedError e = Assert.expectThrows(HealingRejectedError.class,
                () -> page.fill(FixturePage.PASSWORD, "secret-two"));

        Assert.assertEquals(e.result().outcome(), Outcome.REJECTED_UNSAFE, e.getMessage());
        Assert.assertEquals(page.valueOf("#auth-password-plain"), "");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "isVisible on an absent banner returns false without searching for a look-alike")
    public void negativeCheckNeverSearches() {
        String banner = "<p class=\"auth-view__error\" role=\"alert\">Invalid credentials</p>";
        FixturePage page = new FixturePage().show(change(LOGIN, "<!-- banner -->", banner));
        Assert.assertTrue(page.isVisible(FixturePage.ERROR));

        String lookAlike = "<p class=\"auth-view__notice\" role=\"alert\">Invalid credentials</p>";
        page.show(change(LOGIN, "<!-- banner -->", lookAlike));

        Assert.assertFalse(page.isVisible(FixturePage.ERROR));
        Assert.assertEquals(engine.discoveries(), 0, "a negative check must never search");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A hidden original is the app's state: it fails as before and is not swapped")
    public void hiddenOriginalIsNotSwapped() {
        FixturePage page = new FixturePage().show(LOGIN);
        page.click(FixturePage.SUBMIT);

        String hidden = SUBMIT_HTML.replace("<button ", "<button style=\"display:none\" ")
                + "<button type=\"submit\" class=\"auth-view__submit-alt\" data-k=\"alt\">Sign in</button>";
        page.show(change(LOGIN, SUBMIT_HTML, hidden));

        TimeoutError e = Assert.expectThrows(TimeoutError.class, () -> page.click(FixturePage.SUBMIT));
        Assert.assertFalse(e instanceof HealingRejectedError, "the original error must surface unchanged");
        Assert.assertEquals(engine.discoveries(), 0);
        Assert.assertNull(page.lastClicked());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An approved heal is reused next run without searching, then retired when the original returns")
    public void approvedHealIsReusedThenRetired() {
        String renamed = change(LOGIN, "class=\"auth-view__submit\"", "class=\"auth-form__cta\"");
        FixturePage page = new FixturePage().show(LOGIN);
        page.click(FixturePage.SUBMIT);
        page.show(renamed);
        page.click(FixturePage.SUBMIT);
        engine.finishRun();

        HealingEngine nextRun = newEngine(Mode.HEAL);
        Healing.install(nextRun);
        FixturePage again = new FixturePage().show(renamed);
        again.click(FixturePage.SUBMIT);

        Assert.assertEquals(again.lastClicked(), "submit");
        Assert.assertEquals(nextRun.discoveries(), 0, "a stored heal must not trigger a search");
        Assert.assertEquals(nextRun.runResults().get(0).outcome(), Outcome.HEALED_FROM_HISTORY);

        again.show(LOGIN);
        again.click(FixturePage.SUBMIT);
        Assert.assertNull(nextRun.store().find("FixturePage::" + FixturePage.SUBMIT).orElseThrow().heal(),
                "the heal must be retired once the original selector works again");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Report-only mode names the fix but still fails, and clicks nothing")
    public void reportOnlySuggestsAndFails() {
        Healing.install(newEngine(Mode.REPORT));
        FixturePage page = new FixturePage().show(LOGIN);
        page.click(FixturePage.SUBMIT);

        page.show(change(LOGIN, "class=\"auth-view__submit\"", "class=\"auth-form__cta\""));
        HealingRejectedError e = Assert.expectThrows(HealingRejectedError.class,
                () -> page.click(FixturePage.SUBMIT));

        Assert.assertEquals(e.result().outcome(), Outcome.REJECTED_REPORT_ONLY);
        Assert.assertTrue(e.getMessage().contains("Suggested selector"), e.getMessage());
        Assert.assertNull(page.lastClicked());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A page object that catches TimeoutError to retry (HelpCenterPage.open) still retries")
    public void timeoutRetryPatternStillWorks() {
        FixturePage page = new FixturePage().show(LOGIN);
        page.waitForVisible(FixturePage.SUBMIT);

        page.show(change(LOGIN, SUBMIT_HTML, ""));
        Assert.assertTrue(page.waitForSubmitOrRetryWith(LOGIN), "the rejection must reach the TimeoutError catch");
        only(Outcome.REJECTED_LOW_CONFIDENCE);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A wait on an element never seen (an optional dialog) times out as before, unsearched")
    public void waitWithoutBaselineIsNotSearched() {
        FixturePage page = new FixturePage().show(LOGIN);

        TimeoutError e = Assert.expectThrows(TimeoutError.class,
                () -> page.waitForVisible(".confirm-charge__card"));

        Assert.assertFalse(e instanceof HealingRejectedError, "the original timeout must surface unchanged");
        Assert.assertEquals(engine.discoveries(), 0);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Allure categories give healed passes and rejections their own buckets")
    public void allureCategoriesAreWritten() throws IOException {
        AllureHealingReporter.writeCategories();
        Path categories = Path.of(System.getProperty("allure.results.directory", "allure-results"))
                .resolve("categories.json");
        String json = Files.readString(categories);
        Assert.assertTrue(json.contains("Passed with healing") && json.contains("HEALING_REJECTED"), json);
    }

    // ---- Helpers ----

    private HealingEngine newEngine(Mode mode) {
        Path store = dir.resolve("locator-history.json");
        HealingConfig config = HealingConfig.defaults().withMode(mode)
                .withPaths(store.toString(), dir.resolve("healing-report.json").toString());
        return new HealingEngine(config, new JsonHealingStore(store, 20), AiLocatorAdvisor.NONE,
                new TestNgContextProvider(), List.of(new HealingLog(), new AllureHealingReporter()));
    }

    private HealingResult only(Outcome outcome) {
        List<HealingResult> matching = engine.runResults().stream().filter(r -> r.outcome() == outcome).toList();
        Assert.assertEquals(matching.size(), 1, "expected one " + outcome + " in " + engine.runResults());
        return matching.get(0);
    }

    /** Applies one deliberate change to the fixture, failing if the change did not apply. */
    private static String change(String html, String from, String to) {
        Assert.assertTrue(html.contains(from), "fixture does not contain: " + from);
        return html.replace(from, to);
    }

    private static String currentAllureMessage() {
        AtomicReference<String> message = new AtomicReference<>();
        Allure.getLifecycle().getCurrentTestCase().ifPresent(uuid ->
                Allure.getLifecycle().updateTestCase(uuid, tc -> message.set(
                        tc.getStatusDetails() == null ? null : tc.getStatusDetails().getMessage())));
        return message.get();
    }

    private static String fixture(String resource) {
        try (InputStream in = SelfHealingFixtureTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
