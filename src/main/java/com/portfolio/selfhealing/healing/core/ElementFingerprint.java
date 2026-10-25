package com.portfolio.selfhealing.healing.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A platform-neutral description of one UI element: what it was called, what it
 * said, what it sat inside and where it was.
 * <p>
 * Web elements map directly. For a future Appium adapter the mapping is
 * resource-id to {@code id}, content-desc to {@code ariaLabel}, class to
 * {@code tag}, and the bounds attribute to the geometry fields.
 * <p>
 * Never holds what was typed into a field: the collector does not read input
 * values, and {@link #sanitized()} masks e-mail addresses and digits before a
 * fingerprint is stored, logged or compared.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ElementFingerprint(
        String tag,
        String id,
        String testId,
        String name,
        String type,
        String role,
        String ariaLabel,
        String placeholder,
        String title,
        String href,
        String text,
        List<String> classes,
        List<String> ancestors,
        Integer siblingIndex,
        Integer x,
        Integer y,
        Integer width,
        Integer height,
        Boolean editable) {

    private static final int MAX_TEXT = 80;
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    public ElementFingerprint {
        classes = classes == null ? List.of() : List.copyOf(classes);
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasGeometry() {
        return x != null && y != null && width != null && height != null;
    }

    /** Not named isEditable: Jackson would read that as a second "editable" property. */
    public boolean acceptsInput() {
        return Boolean.TRUE.equals(editable);
    }

    public ElementFingerprint withText(String newText) {
        return new ElementFingerprint(tag, id, testId, name, type, role, ariaLabel, placeholder,
                title, href, newText, classes, ancestors, siblingIndex, x, y, width, height, editable);
    }

    public ElementFingerprint withoutGeometry() {
        return new ElementFingerprint(tag, id, testId, name, type, role, ariaLabel, placeholder,
                title, href, text, classes, ancestors, siblingIndex, null, null, null, null, editable);
    }

    /**
     * Same element identity, ignoring where it was drawn. Geometry shifts with
     * the viewport (headed vs headless), so it must not count as a change worth
     * rewriting the history file for.
     */
    public boolean sameIdentityAs(ElementFingerprint other) {
        return other != null && withoutGeometry().equals(other.withoutGeometry());
    }

    /** True when only the visible text differs. */
    public boolean differsOnlyInText(ElementFingerprint other) {
        return other != null && !Objects.equals(text, other.text)
                && withText(null).sameIdentityAs(other.withText(null));
    }

    /**
     * Masks personal data and volatile numbers before the fingerprint is kept.
     * <p>
     * Account e-mail addresses appear in visible text (the profile popup), and
     * counts and balances change every run, so "12 credits" and "40 credits"
     * both become "# credits". Query strings are dropped from links because
     * they carry ids.
     */
    public ElementFingerprint sanitized() {
        return new ElementFingerprint(tag, mask(id), mask(testId), mask(name), type, role,
                mask(ariaLabel), mask(placeholder), mask(title), stripQuery(mask(href)),
                normaliseText(text), classes, ancestors, siblingIndex, x, y, width, height, editable);
    }

    /** Short human-readable form for logs and reports: {@code <button#id.cls> "Sign in"}. */
    public String summary() {
        StringBuilder sb = new StringBuilder("<").append(tag == null ? "?" : tag);
        if (id != null) {
            sb.append('#').append(id);
        }
        classes.stream().limit(2).forEach(c -> sb.append('.').append(c));
        if (type != null && "input".equals(tag)) {
            sb.append(" type=").append(type);
        }
        sb.append('>');
        String label = text != null ? text : ariaLabel != null ? ariaLabel : placeholder;
        if (label != null) {
            sb.append(" \"").append(label.length() > 40 ? label.substring(0, 40) + "…" : label).append('"');
        }
        return sb.toString();
    }

    private static String mask(String value) {
        return value == null ? null : EMAIL.matcher(value).replaceAll("<email>");
    }

    private static String normaliseText(String value) {
        if (value == null) {
            return null;
        }
        String t = DIGITS.matcher(mask(value).replaceAll("\\s+", " ").trim()).replaceAll("#");
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    private static String stripQuery(String value) {
        if (value == null) {
            return null;
        }
        int cut = value.length();
        int q = value.indexOf('?');
        int h = value.indexOf('#');
        if (q >= 0) {
            cut = Math.min(cut, q);
        }
        if (h > 0) {
            cut = Math.min(cut, h);
        }
        return value.substring(0, cut);
    }

    /** Fluent construction for selector hints and tests. */
    public static final class Builder {
        private String tag, id, testId, name, type, role, ariaLabel, placeholder, title, href, text;
        private final List<String> classes = new ArrayList<>();
        private final List<String> ancestors = new ArrayList<>();
        private Integer siblingIndex, x, y, width, height;
        private Boolean editable;

        public Builder tag(String v) { tag = v; return this; }
        public Builder id(String v) { id = v; return this; }
        public Builder testId(String v) { testId = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder type(String v) { type = v; return this; }
        public Builder role(String v) { role = v; return this; }
        public Builder ariaLabel(String v) { ariaLabel = v; return this; }
        public Builder placeholder(String v) { placeholder = v; return this; }
        public Builder title(String v) { title = v; return this; }
        public Builder href(String v) { href = v; return this; }
        public Builder text(String v) { text = v; return this; }
        public Builder addClass(String v) { classes.add(v); return this; }
        /** Replaces the classes set so far. */
        public Builder classes(String... v) { classes.clear(); classes.addAll(List.of(v)); return this; }
        /** Replaces the ancestors set so far, nearest first. */
        public Builder ancestors(String... v) { ancestors.clear(); ancestors.addAll(List.of(v)); return this; }
        public Builder siblingIndex(Integer v) { siblingIndex = v; return this; }
        public Builder editable(Boolean v) { editable = v; return this; }

        public Builder box(int bx, int by, int bw, int bh) {
            x = bx;
            y = by;
            width = bw;
            height = bh;
            return this;
        }

        public boolean isEmpty() {
            return tag == null && id == null && testId == null && name == null && type == null
                    && role == null && ariaLabel == null && placeholder == null && title == null
                    && href == null && text == null && classes.isEmpty();
        }

        public ElementFingerprint build() {
            return new ElementFingerprint(tag, id, testId, name, type, role, ariaLabel, placeholder,
                    title, href, text, classes, ancestors, siblingIndex, x, y, width, height, editable);
        }
    }
}
