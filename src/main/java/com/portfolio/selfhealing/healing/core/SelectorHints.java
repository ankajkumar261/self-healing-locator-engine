package com.portfolio.selfhealing.healing.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what a selector says about its target when no baseline was ever
 * recorded for it: {@code #auth-email} names an id, {@code button:has-text('Go')}
 * a tag and a text.
 * <p>
 * That is weak evidence (the selector is exactly what stopped working), so the
 * engine caps any score built on it below the healing threshold. Its job is to
 * put a useful suggestion in the failure message, not to heal.
 */
public final class SelectorHints {

    private static final Pattern TAG = Pattern.compile("^([a-zA-Z][\\w-]*)");
    private static final Pattern ID = Pattern.compile("#([\\w-]+)");
    private static final Pattern CLASS = Pattern.compile("\\.([\\w-]+)");
    private static final Pattern ATTRIBUTE =
            Pattern.compile("\\[\\s*([\\w-]+)\\s*([~|^$*]?=)\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\]\\s]+))\\s*[is]?\\s*]");
    private static final Pattern TEXT_PSEUDO =
            Pattern.compile(":(?:has-text|text-is|text)\\(\\s*(?:\"([^\"]*)\"|'([^']*)')\\s*\\)");
    private static final Pattern ENGINE_PREFIX = Pattern.compile("^(?:text=|role=|internal:role=|xpath=|//)");
    private static final Pattern ROLE_ENGINE =
            Pattern.compile("^(?:internal:)?role=([\\w-]+)(?:\\[name=(?:\"([^\"]*)\"|'([^']*)')[is]?])?");

    private SelectorHints() {
    }

    /** One hint per comma-separated alternative that says anything at all. */
    public static List<ElementFingerprint> fromSelector(String selector) {
        List<ElementFingerprint> hints = new ArrayList<>();
        for (String alternative : splitTopLevel(selector, ',')) {
            String trimmed = alternative.trim();
            // Engine selectors (text=Sign in) are one unit; splitting on spaces would mangle them.
            List<String> compounds = ENGINE_PREFIX.matcher(trimmed).find()
                    ? List.of(trimmed) : compounds(trimmed);
            if (compounds.isEmpty()) {
                continue;
            }
            ElementFingerprint.Builder hint = parse(compounds.get(compounds.size() - 1));
            if (!hint.isEmpty()) {
                hints.add(hint.build());
            }
        }
        return hints;
    }

    private static ElementFingerprint.Builder parse(String compound) {
        ElementFingerprint.Builder b = ElementFingerprint.builder();

        Matcher role = ROLE_ENGINE.matcher(compound);
        if (role.find()) {
            return b.role(role.group(1)).text(first(role.group(2), role.group(3)));
        }
        if (compound.startsWith("text=")) {
            return b.text(compound.substring(5).replaceAll("^[\"']|[\"']$", ""));
        }
        if (compound.startsWith("xpath=") || compound.startsWith("//")) {
            return b;
        }

        // Strip :not(...), :nth-*(...) and similar before reading ids and classes,
        // so the classes inside a negation are not mistaken for the target's.
        Matcher text = TEXT_PSEUDO.matcher(compound);
        if (text.find()) {
            b.text(first(text.group(1), text.group(2)));
        }
        String core = TEXT_PSEUDO.matcher(compound).replaceAll("")
                .replaceAll(":[\\w-]+\\([^)]*\\)", "")
                .replaceAll(":[\\w-]+", "");

        Matcher attrs = ATTRIBUTE.matcher(core);
        while (attrs.find()) {
            String value = first(attrs.group(3), first(attrs.group(4), attrs.group(5)));
            boolean exact = "=".equals(attrs.group(2));
            switch (attrs.group(1).toLowerCase()) {
                case "id" -> b.id(value);
                case "name" -> b.name(value);
                case "type" -> b.type(value);
                case "placeholder" -> b.placeholder(value);
                case "aria-label" -> b.ariaLabel(value);
                case "title" -> b.title(value);
                case "role" -> b.role(value);
                case "href" -> b.href(exact ? value : null);
                case "data-testid", "data-test-id", "data-test", "data-qa", "data-cy" -> b.testId(value);
                default -> { /* class*= and similar fragments say too little */ }
            }
        }
        String withoutAttributes = ATTRIBUTE.matcher(core).replaceAll("");

        Matcher tag = TAG.matcher(withoutAttributes);
        if (tag.find()) {
            b.tag(tag.group(1).toLowerCase());
        }
        Matcher id = ID.matcher(withoutAttributes);
        if (id.find()) {
            b.id(id.group(1));
        }
        Matcher cls = CLASS.matcher(withoutAttributes);
        while (cls.find()) {
            b.addClass(cls.group(1));
        }
        return b;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    /** Splits on {@code separator} outside quotes, brackets and parentheses. */
    static List<String> splitTopLevel(String s, char separator) {
        return splitTopLevel(s, String.valueOf(separator));
    }

    /**
     * Splits on any of {@code separators} outside quotes, brackets and
     * parentheses, so {@code [class~=x]} and {@code :nth-child(2n+1)} survive.
     */
    private static List<String> splitTopLevel(String s, String separators) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (quote != 0) {
                if (ch == quote && s.charAt(i - 1) != '\\') {
                    quote = 0;
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '(' || ch == '[') {
                depth++;
            } else if (ch == ')' || ch == ']') {
                depth--;
            } else if (depth == 0 && separators.indexOf(ch) >= 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** The compound selectors of one alternative, outermost first. */
    private static List<String> compounds(String alternative) {
        List<String> out = new ArrayList<>();
        for (String piece : splitTopLevel(alternative, " \t\n>+~")) {
            if (!piece.isBlank()) {
                out.add(piece.trim());
            }
        }
        return out;
    }
}
