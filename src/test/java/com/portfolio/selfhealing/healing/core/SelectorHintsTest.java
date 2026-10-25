package com.portfolio.selfhealing.healing.core;

import com.portfolio.selfhealing.constants.AppConstants;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * What the project's own selectors say about their targets.
 */
public class SelectorHintsTest {

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "An id selector names the id")
    public void idSelector() {
        List<ElementFingerprint> hints = SelectorHints.fromSelector("#auth-email");
        Assert.assertEquals(hints.size(), 1);
        Assert.assertEquals(hints.get(0).id(), "auth-email");
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "The last compound is the target")
    public void descendantSelectorTargetsLastCompound() {
        ElementFingerprint hint = SelectorHints.fromSelector(".auth-view__forgot-row button.auth-view__link").get(0);
        Assert.assertEquals(hint.tag(), "button");
        Assert.assertEquals(hint.classes(), List.of("auth-view__link"));
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Comma alternatives each give a hint; :has-text gives the text")
    public void alternativesAndText() {
        List<ElementFingerprint> hints = SelectorHints.fromSelector(
                ".auth-modal button.auth-view__secondary-btn, .auth-modal button:has-text('Continue with email')");
        Assert.assertEquals(hints.size(), 2);
        Assert.assertEquals(hints.get(1).tag(), "button");
        Assert.assertEquals(hints.get(1).text(), "Continue with email");
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "Attribute selectors fill the matching fields")
    public void attributes() {
        ElementFingerprint hint = SelectorHints.fromSelector("button.sidebar-item[aria-label='New']").get(0);
        Assert.assertEquals(hint.tag(), "button");
        Assert.assertEquals(hint.ariaLabel(), "New");
        Assert.assertEquals(hint.classes(), List.of("sidebar-item"));
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Classes inside :not() are not mistaken for the target's own")
    public void negationIsIgnored() {
        ElementFingerprint hint =
                SelectorHints.fromSelector("img[src^='http']:not(.feature-pill__icon), video, audio").get(0);
        Assert.assertEquals(hint.tag(), "img");
        Assert.assertTrue(hint.classes().isEmpty(), hint.classes().toString());
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "A substring class match says nothing usable")
    public void partialClassMatchGivesNoHint() {
        Assert.assertTrue(SelectorHints.fromSelector("[class*='-page__error']").isEmpty());
    }

    @Test(groups = {AppConstants.GROUP_HEALING}, description = "Playwright engine selectors are understood")
    public void engineSelectors() {
        ElementFingerprint role = SelectorHints.fromSelector("role=button[name=\"Sign in\"s]").get(0);
        Assert.assertEquals(role.role(), "button");
        Assert.assertEquals(role.text(), "Sign in");
        Assert.assertEquals(SelectorHints.fromSelector("text=Sign in").get(0).text(), "Sign in");
    }
}
