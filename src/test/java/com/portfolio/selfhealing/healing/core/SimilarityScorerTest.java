package com.portfolio.selfhealing.healing.core;

import com.portfolio.selfhealing.constants.AppConstants;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * The scores behind the 90% threshold: what must clear it and what must not.
 */
public class SimilarityScorerTest {

    private final SimilarityScorer scorer = new SimilarityScorer();

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An unchanged element scores 100%")
    public void identicalElementScoresOne() {
        ElementFingerprint submit = Fingerprints.submit().build();
        Assert.assertEquals(scorer.score(submit, submit).total(), 1.0, 1e-9);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A button whose class was renamed still clears 90% on text, role, ancestry and position")
    public void renamedClassClearsThreshold() {
        ElementFingerprint before = Fingerprints.submit().build();
        ElementFingerprint renamed = Fingerprints.submit().classes("auth-form__cta").build();

        double score = scorer.score(before, renamed).total();
        Assert.assertTrue(score >= 0.90, "renamed-class score was " + score);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "An input whose id changed but kept name, placeholder and label clears 90%")
    public void renamedInputIdClearsThreshold() {
        ElementFingerprint before = Fingerprints.emailInput().build();
        ElementFingerprint after = Fingerprints.emailInput().id("login-email").build();

        SimilarityScorer.Score score = scorer.score(before, after);
        Assert.assertTrue(score.total() >= 0.90, "renamed-id score was " + score.total());
        Assert.assertTrue(score.reason().contains("id: auth-email → login-email"),
                "The reason should name the changed id: " + score.reason());
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "The nav 'Sign in' link shares the submit button's text but nothing else")
    public void lookAlikeLinkScoresLow() {
        double score = scorer.score(Fingerprints.submit().build(), Fingerprints.navSignIn().build()).total();
        Assert.assertTrue(score < 0.5, "look-alike scored " + score);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "A neighbouring button in the same form does not come close")
    public void neighbouringButtonScoresLow() {
        double score = scorer.score(Fingerprints.submit().build(), Fingerprints.forgotPassword().build()).total();
        Assert.assertTrue(score < 0.6, "neighbour scored " + score);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "The password field is not a stand-in for the e-mail field")
    public void otherFieldInSameFormScoresLow() {
        double score = scorer.score(Fingerprints.emailInput().build(), Fingerprints.passwordInput().build()).total();
        Assert.assertTrue(score < 0.6, "password-for-email scored " + score);
    }

    @Test(groups = {AppConstants.GROUP_HEALING},
            description = "Attributes the original never had are not held against a candidate")
    public void absentSignalsAreNotPenalised() {
        ElementFingerprint withoutId = Fingerprints.submit().build();
        ElementFingerprint gainedId = Fingerprints.submit().id("sign-in-submit").build();
        Assert.assertEquals(scorer.score(withoutId, gainedId).total(), 1.0, 1e-9);
    }
}
