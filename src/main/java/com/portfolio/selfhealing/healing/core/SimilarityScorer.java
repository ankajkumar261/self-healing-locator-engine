package com.portfolio.selfhealing.healing.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scores how closely a candidate element matches what the locator used to find.
 * <p>
 * The score is a weighted average over the signals the expected element actually
 * has: an element with no id is not penalised for lacking one, but an element
 * whose id changed is. That makes the score comparable across elements, and is
 * why 90% means "everything we knew about it still holds, bar a detail" rather
 * than a probability.
 */
public final class SimilarityScorer {

    /** Weights from the approved design; they sum to 1.0. */
    public enum Component {
        IDENTITY(0.35, "identity"),
        TEXT(0.25, "text"),
        ROLE_TAG(0.15, "role/tag"),
        STRUCTURE(0.12, "ancestry"),
        CLASSES(0.08, "classes"),
        POSITION(0.05, "position");

        final double weight;
        final String label;

        Component(double weight, String label) {
            this.weight = weight;
            this.label = label;
        }
    }

    /** Identity attributes and their relative weight within the identity component. */
    private static final Map<String, Integer> IDENTITY_WEIGHTS = new LinkedHashMap<>();
    private static final Map<String, Function<ElementFingerprint, String>> IDENTITY_GETTERS =
            new LinkedHashMap<>();

    static {
        identity("testId", 3, ElementFingerprint::testId);
        identity("id", 3, ElementFingerprint::id);
        identity("name", 2, ElementFingerprint::name);
        identity("aria-label", 2, ElementFingerprint::ariaLabel);
        identity("placeholder", 2, ElementFingerprint::placeholder);
        identity("href", 1, ElementFingerprint::href);
        identity("title", 1, ElementFingerprint::title);
        identity("type", 1, ElementFingerprint::type);
    }

    private static void identity(String attr, int weight, Function<ElementFingerprint, String> getter) {
        IDENTITY_WEIGHTS.put(attr, weight);
        IDENTITY_GETTERS.put(attr, getter);
    }

    /** Distance, in CSS pixels, at which the position signal reaches zero. */
    private static final double POSITION_RANGE_PX = 300;

    /**
     * @param total      weighted score in [0, 1]
     * @param components score of each signal the expected element had
     * @param changes    the identity attributes that differ, e.g. "id: auth-email → login-email"
     */
    public record Score(double total, Map<Component, Double> components, List<String> changes) {

        /** "matched text, role/tag, ancestry · differs classes 0%, id: a → b". */
        public String reason() {
            List<String> matched = new ArrayList<>();
            List<String> differs = new ArrayList<>();
            for (Map.Entry<Component, Double> e : components.entrySet()) {
                if (e.getValue() >= 0.95) {
                    matched.add(e.getKey().label);
                } else if (e.getKey() != Component.IDENTITY || changes.isEmpty()) {
                    differs.add(e.getKey().label + " " + Math.round(e.getValue() * 100) + "%");
                }
            }
            differs.addAll(changes);
            StringBuilder sb = new StringBuilder();
            if (!matched.isEmpty()) {
                sb.append("matched ").append(String.join(", ", matched));
            }
            if (!differs.isEmpty()) {
                sb.append(sb.length() == 0 ? "" : " · ").append("differs ").append(String.join(", ", differs));
            }
            return sb.length() == 0 ? "no comparable signals" : sb.toString();
        }
    }

    public Score score(ElementFingerprint expected, ElementFingerprint candidate) {
        Map<Component, Double> parts = new EnumMap<>(Component.class);
        List<String> changes = new ArrayList<>();

        put(parts, Component.IDENTITY, identity(expected, candidate, changes));
        put(parts, Component.TEXT, text(expected, candidate));
        put(parts, Component.ROLE_TAG, roleAndTag(expected, candidate));
        put(parts, Component.STRUCTURE, structure(expected, candidate));
        put(parts, Component.CLASSES, classes(expected, candidate));
        put(parts, Component.POSITION, position(expected, candidate));

        double weighted = 0;
        double weights = 0;
        for (Map.Entry<Component, Double> e : parts.entrySet()) {
            weighted += e.getKey().weight * e.getValue();
            weights += e.getKey().weight;
        }
        double total = weights == 0 ? 0 : weighted / weights;
        return new Score(total, parts, changes);
    }

    private static void put(Map<Component, Double> parts, Component c, Double value) {
        if (value != null) {
            parts.put(c, value);
        }
    }

    // ---- Signals. Each returns null when the expected element gives nothing to compare. ----

    private static Double identity(ElementFingerprint e, ElementFingerprint c, List<String> changes) {
        double sum = 0;
        int weights = 0;
        for (Map.Entry<String, Function<ElementFingerprint, String>> attr : IDENTITY_GETTERS.entrySet()) {
            String expected = attr.getValue().apply(e);
            if (expected == null) {
                continue;
            }
            String actual = attr.getValue().apply(c);
            double s = actual == null ? 0 : attributeSimilarity(expected, actual);
            int w = IDENTITY_WEIGHTS.get(attr.getKey());
            sum += s * w;
            weights += w;
            if (s < 1) {
                changes.add(attr.getKey() + ": " + expected + " → " + (actual == null ? "(none)" : actual));
            }
        }
        return weights == 0 ? null : sum / weights;
    }

    private static Double text(ElementFingerprint e, ElementFingerprint c) {
        if (e.text() == null) {
            return null;
        }
        return c.text() == null ? 0.0 : textSimilarity(e.text(), c.text());
    }

    private static Double roleAndTag(ElementFingerprint e, ElementFingerprint c) {
        if (e.tag() == null && e.role() == null) {
            return null;
        }
        boolean sameRole = Objects.equals(e.role(), c.role());
        if (e.tag() == null) {
            return sameRole ? 1.0 : 0.0;
        }
        if (e.tag().equalsIgnoreCase(c.tag() == null ? "" : c.tag())) {
            return sameRole ? 1.0 : 0.5;
        }
        // A div that became a button keeps half credit only if it kept its role.
        return sameRole && e.role() != null ? 0.5 : 0.0;
    }

    private static Double structure(ElementFingerprint e, ElementFingerprint c) {
        if (e.ancestors().isEmpty()) {
            return null;
        }
        double sum = 0;
        for (int i = 0; i < e.ancestors().size(); i++) {
            if (i < c.ancestors().size()) {
                sum += dice(selectorTokens(e.ancestors().get(i)), selectorTokens(c.ancestors().get(i)));
            }
        }
        double chain = sum / e.ancestors().size();
        if (e.siblingIndex() == null) {
            return chain;
        }
        return chain * 0.85 + (Objects.equals(e.siblingIndex(), c.siblingIndex()) ? 0.15 : 0);
    }

    private static Double classes(ElementFingerprint e, ElementFingerprint c) {
        if (e.classes().isEmpty()) {
            return null;
        }
        return dice(bemTokens(e.classes()), bemTokens(c.classes()));
    }

    private static Double position(ElementFingerprint e, ElementFingerprint c) {
        if (!e.hasGeometry() || !c.hasGeometry()) {
            return null;
        }
        double ex = e.x() + e.width() / 2.0;
        double ey = e.y() + e.height() / 2.0;
        double cx = c.x() + c.width() / 2.0;
        double cy = c.y() + c.height() / 2.0;
        double distance = Math.hypot(ex - cx, ey - cy);
        double near = Math.max(0, 1 - distance / POSITION_RANGE_PX);
        double size = ratio(e.width(), c.width()) * ratio(e.height(), c.height());
        return near * 0.7 + size * 0.3;
    }

    // ---- Similarity primitives ----

    static double attributeSimilarity(String a, String b) {
        if (a.equalsIgnoreCase(b)) {
            return 1;
        }
        return dice(words(a), words(b));
    }

    static double textSimilarity(String a, String b) {
        String x = normalise(a);
        String y = normalise(b);
        if (x.equals(y)) {
            return 1;
        }
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        double edit = 1 - (double) levenshtein(x, y) / Math.max(x.length(), y.length());
        return Math.max(dice(words(x), words(y)), edit);
    }

    /** Sørensen–Dice over two token sets. */
    static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        return 2.0 * common.size() / (a.size() + b.size());
    }

    /**
     * Expands BEM classes so a rename keeps partial credit for what survived:
     * {@code auth-view__submit} contributes itself, its block {@code auth-view}
     * and its element {@code submit}.
     */
    static Set<String> bemTokens(List<String> classes) {
        Set<String> tokens = new HashSet<>();
        for (String c : classes) {
            String lower = c.toLowerCase(Locale.ROOT);
            tokens.add(lower);
            String withoutModifier = lower.split("--", 2)[0];
            tokens.add(withoutModifier);
            String[] blockAndElement = withoutModifier.split("__", 2);
            tokens.add(blockAndElement[0]);
            if (blockAndElement.length > 1) {
                tokens.add(blockAndElement[1]);
            }
        }
        tokens.remove("");
        return tokens;
    }

    /** Tokens of an ancestor descriptor such as {@code div.auth-view__field} or {@code form#login}. */
    static Set<String> selectorTokens(String descriptor) {
        String[] parts = descriptor.split("[.#]");
        Set<String> tokens = new HashSet<>();
        tokens.add("tag:" + parts[0].toLowerCase(Locale.ROOT));
        tokens.addAll(bemTokens(Arrays.asList(parts).subList(1, parts.length)));
        return tokens;
    }

    /** Lower-cased word tokens, splitting camelCase and any punctuation. */
    static Set<String> words(String s) {
        return Arrays.stream(s.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9#]+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String normalise(String s) {
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static double ratio(int a, int b) {
        if (a <= 0 || b <= 0) {
            return a == b ? 1 : 0;
        }
        return (double) Math.min(a, b) / Math.max(a, b);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }
}
