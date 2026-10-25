package com.portfolio.selfhealing.healing.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Hard rules a replacement must pass whatever its score.
 * <p>
 * Scoring finds the most similar element; this decides whether acting on it is
 * acceptable at all. A history row's title sits right next to its delete button,
 * and a cheap generate button looks like an expensive one, so similarity alone
 * is not a licence to click.
 */
public final class SafetyGuard {

    /** Controls whose effect cannot be undone. */
    private static final Set<String> DESTRUCTIVE = Set.of(
            "delete", "remove", "trash", "destroy", "erase", "discard",
            "logout", "signout", "unsubscribe", "deactivate", "revoke");

    /** Controls that spend money or credits. */
    private static final Set<String> SPENDING = Set.of(
            "pay", "payment", "purchase", "buy", "checkout", "charge", "subscribe", "upgrade",
            "generate", "regenerate", "credit", "credits");

    /**
     * @return why the candidate must not be used, or empty when it may be
     */
    public Optional<String> veto(ElementFingerprint expected, ElementFingerprint candidate, Intent intent) {
        if (intent.requiresEditable() && !candidate.acceptsInput()) {
            return Optional.of("cannot accept input");
        }
        if (isPassword(expected) != isPassword(candidate) && isField(expected) && isField(candidate)) {
            return Optional.of("input type changed (" + typeOf(expected) + " → " + typeOf(candidate)
                    + "); a password must never be typed into a visible field, or the reverse");
        }
        if (intent.actsOnElement()) {
            Set<String> gained = new LinkedHashSet<>(riskyWords(candidate));
            gained.removeAll(riskyWords(expected));
            if (!gained.isEmpty()) {
                String word = gained.iterator().next();
                String kind = SPENDING.contains(word) ? "spends credits or money" : "is destructive";
                return Optional.of("would act on a control that " + kind + " (\"" + word
                        + "\") where the original did not");
            }
        }
        return Optional.empty();
    }

    /** Destructive and spending words the element carries in its label, text or classes. */
    static Set<String> riskyWords(ElementFingerprint fp) {
        List<String> tokens = new ArrayList<>();
        for (String s : new String[]{fp.text(), fp.ariaLabel(), fp.title(), fp.id(), fp.testId(), fp.name()}) {
            tokens.addAll(tokens(s));
        }
        fp.classes().forEach(c -> tokens.addAll(tokens(c)));

        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (DESTRUCTIVE.contains(t) || SPENDING.contains(t)) {
                found.add(t);
            }
            // "Sign out" / "log out" written as two words
            if (i + 1 < tokens.size() && tokens.get(i + 1).equals("out")
                    && (t.equals("sign") || t.equals("log"))) {
                found.add(t + "out");
            }
        }
        return found;
    }

    private static List<String> tokens(String s) {
        if (s == null) {
            return List.of();
        }
        return Arrays.stream(s.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9]+"))
                .filter(t -> !t.isEmpty())
                .toList();
    }

    private static boolean isField(ElementFingerprint fp) {
        return "input".equalsIgnoreCase(fp.tag()) || "textarea".equalsIgnoreCase(fp.tag());
    }

    private static boolean isPassword(ElementFingerprint fp) {
        return "password".equalsIgnoreCase(fp.type());
    }

    private static String typeOf(ElementFingerprint fp) {
        return fp.type() == null ? fp.tag() : fp.type();
    }
}
