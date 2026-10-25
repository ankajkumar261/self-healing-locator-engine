package com.portfolio.selfhealing.healing.core;

/**
 * Fingerprints shaped like the sign-in modal, shared by the core tests.
 */
final class Fingerprints {

    private Fingerprints() {
    }

    static ElementFingerprint.Builder submit() {
        return ElementFingerprint.builder().tag("button").type("submit").role("button").text("Sign in")
                .classes("auth-view__submit")
                .ancestors("form.auth-view__form", "div.auth-view", "div.auth-modal")
                .siblingIndex(0).box(625, 400, 370, 40);
    }

    static ElementFingerprint.Builder navSignIn() {
        return ElementFingerprint.builder().tag("a").role("link").href("#sign-in").text("Sign in")
                .classes("nav-login").ancestors("nav.landing-nav")
                .siblingIndex(0).box(1800, 18, 60, 20);
    }

    static ElementFingerprint.Builder forgotPassword() {
        return ElementFingerprint.builder().tag("button").type("button").role("button").text("Forgot password?")
                .classes("auth-view__link")
                .ancestors("div.auth-view__forgot-row", "form.auth-view__form", "div.auth-view", "div.auth-modal")
                .siblingIndex(0).box(625, 350, 140, 20);
    }

    static ElementFingerprint.Builder emailInput() {
        return ElementFingerprint.builder().tag("input").id("auth-email").type("email").role("textbox")
                .name("email").placeholder("Email address").text("Email").classes("auth-view__input")
                .ancestors("div.auth-view__field", "form.auth-view__form", "div.auth-view", "div.auth-modal")
                .siblingIndex(0).box(625, 200, 370, 30).editable(true);
    }

    static ElementFingerprint.Builder passwordInput() {
        return ElementFingerprint.builder().tag("input").id("auth-password").type("password").role("textbox")
                .name("password").placeholder("Password").text("Password").classes("auth-view__input")
                .ancestors("div.auth-view__field", "form.auth-view__form", "div.auth-view", "div.auth-modal")
                .siblingIndex(0).box(625, 270, 370, 30).editable(true);
    }
}
