package com.portfolio.selfhealing.healing.core;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.healing.core.HealingStore.ActiveHeal;
import com.portfolio.selfhealing.healing.core.HealingStore.HealingEvent;
import com.portfolio.selfhealing.healing.core.HealingStore.LocatorRecord;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The history file: survives runs, stays quiet when nothing changed.
 */
public class JsonHealingStoreTest {

    private static final LocatorKey SUBMIT = new LocatorKey("LoginPage", "button.auth-view__submit", "SIGN_IN_BUTTON");

    private Path file;

    @BeforeMethod(alwaysRun = true)
    public void freshFile() throws IOException {
        file = Files.createTempDirectory("healing-store").resolve("history.json");
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "Baselines and heals survive into the next run")
    public void roundTrip() {
        JsonHealingStore store = new JsonHealingStore(file, 20);
        store.observeBaseline(SUBMIT, Fingerprints.submit().build(), "/", "t0");
        store.saveHeal(SUBMIT, heal("role=button[name=\"Sign in\"s]"));
        Assert.assertTrue(store.flush());

        LocatorRecord rec = new JsonHealingStore(file, 20).find(SUBMIT.id()).orElseThrow();
        Assert.assertEquals(rec.logicalName(), "SIGN_IN_BUTTON");
        Assert.assertEquals(rec.baseline(), Fingerprints.submit().build());
        Assert.assertEquals(rec.heal().selector(), "role=button[name=\"Sign in\"s]");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An element drawn elsewhere (headed vs headless) does not rewrite the committed file")
    public void geometryAloneIsNotAChange() {
        JsonHealingStore store = new JsonHealingStore(file, 20);
        store.observeBaseline(SUBMIT, Fingerprints.submit().build(), "/", "t0");
        Assert.assertTrue(store.flush());

        store.observeBaseline(SUBMIT, Fingerprints.submit().box(10, 10, 370, 40).build(), "/", "t1");
        Assert.assertFalse(store.flush(), "a position-only change must not dirty the history");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Text that changes between runs (a title, a balance) stops counting as evidence")
    public void changingTextBecomesVolatile() {
        JsonHealingStore store = new JsonHealingStore(file, 20);
        store.observeBaseline(SUBMIT, Fingerprints.submit().text("Quarterly report").build(), "/", "t0");
        store.observeBaseline(SUBMIT, Fingerprints.submit().text("Weekly digest").build(), "/", "t1");

        LocatorRecord rec = store.find(SUBMIT.id()).orElseThrow();
        Assert.assertEquals(rec.textVolatile(), Boolean.TRUE);
        Assert.assertNull(rec.baseline().text());

        store.flush();
        store.observeBaseline(SUBMIT, Fingerprints.submit().text("Something else").build(), "/", "t2");
        Assert.assertFalse(store.flush(), "volatile text must not rewrite the file on every run");
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "A heal used many times in one run counts that run once")
    public void healUseCountsOncePerRun() {
        JsonHealingStore store = new JsonHealingStore(file, 20);
        store.saveHeal(SUBMIT, heal("button:text-is(\"Sign in\")"));
        store.markHealUsed(SUBMIT.id(), "run-2", "t1");
        store.markHealUsed(SUBMIT.id(), "run-2", "t2");
        store.markHealUsed(SUBMIT.id(), "run-3", "t3");
        Assert.assertEquals(store.find(SUBMIT.id()).orElseThrow().heal().runsUsed(), 3);
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "Only the most recent events are kept")
    public void eventsAreCapped() {
        JsonHealingStore store = new JsonHealingStore(file, 3);
        for (int i = 0; i < 5; i++) {
            store.addEvent(SUBMIT, new HealingEvent("t" + i, "run", null, "HEALED", null, null, "event " + i));
        }
        List<HealingEvent> events = store.find(SUBMIT.id()).orElseThrow().events();
        Assert.assertEquals(events.size(), 3);
        Assert.assertEquals(events.get(0).reason(), "event 2");
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An unreadable history is set aside rather than failing the suite")
    public void corruptFileIsSetAside() throws IOException {
        Files.writeString(file, "{ not json");
        JsonHealingStore store = new JsonHealingStore(file, 20);
        Assert.assertTrue(store.find(SUBMIT.id()).isEmpty());
        Assert.assertTrue(Files.exists(file.resolveSibling("history.json.corrupt")));
    }

    private static ActiveHeal heal(String selector) {
        return new ActiveHeal(selector, 0.93, "DOM_SIMILARITY", "test", List.of(), "t0", "t0", "run-1", 1);
    }
}
