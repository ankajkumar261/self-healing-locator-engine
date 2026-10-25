package com.portfolio.selfhealing.healing.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * History kept in one JSON file, read once and written once at the end of a run.
 * <p>
 * The file is meant to be committed, so it is only rewritten when something a
 * reviewer would care about changed: a new or different element, a heal approved
 * or dropped, another run that needed a heal. A run that only saw elements drawn
 * a few pixels elsewhere writes nothing. Keys are sorted so diffs stay small.
 */
public final class JsonHealingStore implements HealingStore {

    private static final Logger log = LogManager.getLogger(JsonHealingStore.class);
    private static final int FORMAT_VERSION = 1;

    static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private final Path file;
    private final int maxEvents;
    private final Map<String, LocatorRecord> records = new TreeMap<>();
    private boolean dirty;

    /** On-disk shape. */
    public record HistoryFile(int version, Map<String, LocatorRecord> locators) {
    }

    public JsonHealingStore(Path file, int maxEvents) {
        this.file = file;
        this.maxEvents = maxEvents;
        load();
    }

    private void load() {
        if (!Files.exists(file)) {
            log.info("No healing history at {}; it will be created at the end of the run.", file);
            return;
        }
        try {
            HistoryFile history = JSON.readValue(file.toFile(), HistoryFile.class);
            if (history.locators() != null) {
                records.putAll(history.locators());
            }
            log.info("Loaded healing history for {} locators from {}", records.size(), file);
        } catch (IOException e) {
            // A corrupt history must not stop the suite; start clean and keep the old file aside.
            Path aside = file.resolveSibling(file.getFileName() + ".corrupt");
            log.warn("Healing history at {} is unreadable ({}); moving it to {} and starting empty.",
                    file, e.getMessage(), aside);
            try {
                Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailed) {
                log.warn("Could not move it aside: {}", moveFailed.getMessage());
            }
        }
    }

    @Override
    public synchronized Optional<LocatorRecord> find(String key) {
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public synchronized void observeBaseline(LocatorKey key, ElementFingerprint fp, String location, String at) {
        LocatorRecord existing = records.get(key.id());
        LocatorRecord rec = existing == null ? LocatorRecord.of(key) : existing;
        if (key.logicalName() != null && !key.logicalName().equals(rec.logicalName())) {
            rec = rec.withLogicalName(key.logicalName());
            dirty = true;
        }

        ElementFingerprint old = rec.baseline();
        boolean volatileText = Boolean.TRUE.equals(rec.textVolatile());
        ElementFingerprint incoming = volatileText ? fp.withText(null) : fp;

        if (old == null) {
            rec = rec.withBaseline(incoming, location, at, rec.textVolatile());
            dirty = true;
        } else if (!old.sameIdentityAs(incoming)) {
            if (!volatileText && old.differsOnlyInText(incoming)) {
                // Same element, new words: a title or a balance. Text stops being evidence for it.
                rec = rec.withBaseline(incoming.withText(null), location, at, true);
            } else {
                rec = rec.withBaseline(incoming, location, at, rec.textVolatile());
            }
            dirty = true;
        }
        records.put(key.id(), rec);
    }

    @Override
    public synchronized void saveHeal(LocatorKey key, ActiveHeal heal) {
        LocatorRecord rec = records.getOrDefault(key.id(), LocatorRecord.of(key));
        records.put(key.id(), rec.withHeal(heal));
        dirty = true;
    }

    @Override
    public synchronized void markHealUsed(String key, String runId, String at) {
        LocatorRecord rec = records.get(key);
        if (rec == null || rec.heal() == null || runId.equals(rec.heal().lastRunId())) {
            return;
        }
        records.put(key, rec.withHeal(rec.heal().usedIn(runId, at)));
        dirty = true;
    }

    @Override
    public synchronized void dropHeal(LocatorKey key, HealingEvent why) {
        LocatorRecord rec = records.get(key.id());
        if (rec == null || rec.heal() == null) {
            return;
        }
        records.put(key.id(), rec.withHeal(null).withEvent(why, maxEvents));
        dirty = true;
    }

    @Override
    public synchronized void addEvent(LocatorKey key, HealingEvent event) {
        LocatorRecord rec = records.getOrDefault(key.id(), LocatorRecord.of(key));
        records.put(key.id(), rec.withEvent(event, maxEvents));
        dirty = true;
    }

    @Override
    public synchronized boolean flush() {
        if (!dirty) {
            return false;
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            // Write beside the target and swap, so a run killed mid-write
            // never leaves a half-written history behind.
            Path tmp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            JSON.writeValue(tmp.toFile(), new HistoryFile(FORMAT_VERSION, new TreeMap<>(records)));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            log.info("Healing history written: {} locators -> {}", records.size(), file);
            return true;
        } catch (IOException e) {
            log.warn("Could not write healing history to {}: {}", file, e.getMessage());
            return false;
        }
    }
}
