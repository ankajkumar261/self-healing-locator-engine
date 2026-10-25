package com.portfolio.selfhealing.healing.core;

import com.portfolio.selfhealing.constants.AppConstants;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Optional;

/**
 * Rules a replacement must pass whatever its score.
 */
public class SafetyGuardTest {

    private final SafetyGuard guard = new SafetyGuard();

    private static ElementFingerprint rowTitle() {
        return ElementFingerprint.builder().tag("span").text("Quarterly report")
                .classes("history-api__row-title").ancestors("div.history-api__row").build();
    }

    private static ElementFingerprint rowDelete() {
        return ElementFingerprint.builder().tag("button").role("button").ariaLabel("Delete")
                .classes("history-api__row-delete").ancestors("div.history-api__row").build();
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A click never heals onto a delete control the original was not")
    public void destructiveControlIsVetoedForClicks() {
        Optional<String> veto = guard.veto(rowTitle(), rowDelete(), Intent.CLICK);
        Assert.assertTrue(veto.isPresent());
        Assert.assertTrue(veto.get().contains("destructive"), veto.get());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Reading a delete control's label is harmless and allowed")
    public void destructiveControlIsAllowedForReads() {
        Assert.assertTrue(guard.veto(rowTitle(), rowDelete(), Intent.READ).isEmpty());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A sign-out button may heal onto another sign-out button")
    public void destructiveToDestructiveIsAllowed() {
        ElementFingerprint signOut = ElementFingerprint.builder().tag("div").text("Sign out")
                .classes("profile-popup__menu-item").build();
        ElementFingerprint renamed = ElementFingerprint.builder().tag("button").text("Sign out")
                .classes("profile-menu__item").build();
        Assert.assertTrue(guard.veto(signOut, renamed, Intent.CLICK).isEmpty());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "'Sign out' written as two words is still recognised")
    public void twoWordSignOutIsRecognised() {
        ElementFingerprint stay = ElementFingerprint.builder().tag("button").text("Stay signed in").build();
        ElementFingerprint leave = ElementFingerprint.builder().tag("button").text("Sign out").build();
        Assert.assertTrue(guard.veto(stay, leave, Intent.CLICK).isPresent());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A click never heals onto a control that spends credits")
    public void spendingControlIsVetoed() {
        ElementFingerprint close = ElementFingerprint.builder().tag("button").text("Close").build();
        ElementFingerprint generate = ElementFingerprint.builder().tag("button").text("Generate # credits").build();
        Optional<String> veto = guard.veto(close, generate, Intent.CLICK);
        Assert.assertTrue(veto.isPresent());
        Assert.assertTrue(veto.get().contains("spends"), veto.get());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Typing requires a replacement that accepts input")
    public void inputRequiresEditable() {
        ElementFingerprint label = ElementFingerprint.builder().tag("label").text("Email").build();
        Assert.assertTrue(guard.veto(Fingerprints.emailInput().build(), label, Intent.INPUT).isPresent());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A password field never heals onto a plain text field, or the reverse")
    public void passwordAndTextNeverSwap() {
        ElementFingerprint password = Fingerprints.passwordInput().build();
        ElementFingerprint plain = Fingerprints.passwordInput().type("text").build();
        Assert.assertTrue(guard.veto(password, plain, Intent.INPUT).isPresent());
        Assert.assertTrue(guard.veto(plain, password, Intent.INPUT).isPresent());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An ordinary renamed button passes")
    public void ordinaryButtonPasses() {
        Assert.assertTrue(guard.veto(Fingerprints.submit().build(),
                Fingerprints.submit().classes("auth-form__cta").build(), Intent.CLICK).isEmpty());
    }
}
