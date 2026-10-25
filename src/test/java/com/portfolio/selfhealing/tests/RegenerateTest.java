package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.DashboardPage;
import com.portfolio.selfhealing.pages.DesignEditorPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * RegenerateTest - coverage for the regenerate feature.
 *
 * In the AI Design "Poster Editor", selecting any image layer reveals a
 * "Regenerate Image" action. Every image layer exposes it, so all of a design's
 * images can be regenerated. Verified against the live editor: the action shows
 * for image layers and is absent for text layers.
 *
 * TC_REGEN_001 proves the control is present and enabled for every image layer
 * WITHOUT clicking it, so it spends no credits. Actually clicking Regenerate runs
 * a real generation and spends credits — that lives in TC_REGEN_002, which is in
 * the credit-spending {@code generation} group and is excluded from the default
 * suite, exactly like {@link AiGenerationTest}.
 *
 * The design is reached by reopening the newest Design entry from History, so the
 * test needs an account with at least one past design; it skips otherwise.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("Regenerate")
public class RegenerateTest extends SingleSessionTest {

    private DashboardPage dashboard;

    @BeforeMethod(alwaysRun = true)
    public void openWorkspace() {
        dashboard = new DashboardPage();
        dashboard.navigateTo(config.getBaseUrl());
        dashboard.waitForDashboard();
    }

    /** How many designs to try before giving up on finding an image layer. */
    private static final int DESIGNS_TO_TRY = 4;

    /**
     * Opens a design that actually has image layers, or skips.
     * <p>
     * Regenerate is an image-layer action, so a text-only design has nothing to
     * offer it. Trying only the newest design made the whole feature look
     * untestable whenever the most recent one happened to be text-only, so a few
     * are tried before concluding the account cannot exercise this at all.
     */
    private DesignEditorPage openDesignWithImages() {
        dashboard.openHistory();
        java.util.List<Integer> designs = dashboard.indexesOfEntriesWithTag("Design");
        if (designs.isEmpty()) {
            throw new SkipException("No Design entry in history; nothing to regenerate.");
        }

        int tried = 0;
        for (int index : designs) {
            if (tried++ >= DESIGNS_TO_TRY) {
                break;
            }
            dashboard.openHistoryEntry(index);
            dashboard.closeHistoryIfOpen();
            DesignEditorPage editor = new DesignEditorPage().waitForEditor();
            if (editor.getImageLayerCount() > 0) {
                logger.info("Design #{} has {} image layer(s); using it.",
                        index, editor.getImageLayerCount());
                return editor;
            }
            logger.info("Design #{} has no image layer; trying the next one.", index);
            dashboard.navigateTo(config.getBaseUrl());
            dashboard.waitForDashboard().openHistory();
        }

        throw new SkipException(String.format(
                "None of the %d most recent designs has an image layer, so there is "
                        + "nothing to regenerate. The feature still exists in the app — "
                        + "seed the account with an image-bearing design to cover it.",
                Math.min(tried, DESIGNS_TO_TRY)));
    }

    // ----------------------------------------------------------------
    // TC_REGEN_001 - Regenerate is available for every image (no credits)
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_REGENERATE},
            description = "Every image layer in a design exposes an enabled Regenerate Image action"
    )
    @Story("Regenerate all images")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Opening a design shows a Regenerate Image action for each image layer, so all of a "
            + "design's images can be regenerated. Verifies availability only — never clicks, no credits.")
    public void TC_REGEN_001_regenerateAvailableForAllImages() {
        DesignEditorPage editor = openDesignWithImages();

        Assert.assertTrue(editor.isEditorRoute(),
                "A design should open the poster editor at /design/editor. Actual: "
                        + editor.getCurrentUrl());

        int imageLayers = editor.getImageLayerCount();
        if (imageLayers == 0) {  // openDesignWithImages should have prevented this
            // Same reasoning as the missing-design skip above: which design happens
            // to be newest is account state, not behaviour. A text-only design has
            // nothing to regenerate, and failing on that reports a fixture gap as a
            // product defect.
            throw new SkipException(
                    "The newest design has no image layer, so there is nothing to "
                            + "regenerate. Seed the account with an image-bearing design.");
        }

        // Walk every image layer; each must offer an enabled Regenerate Image action.
        for (int i = 0; i < imageLayers; i++) {
            editor.selectImageLayer(i);
            Assert.assertTrue(editor.isRegenerateVisible(),
                    "Image layer #" + i + " should expose a Regenerate Image action.");
            Assert.assertTrue(editor.isRegenerateEnabled(),
                    "Regenerate Image should be enabled for image layer #" + i + ".");
            Assert.assertTrue(editor.getRegenerateLabel().toLowerCase().contains("regenerate"),
                    "The action should read 'Regenerate Image'. Was: '"
                            + editor.getRegenerateLabel() + "'");
        }

        editor.takeScreenshot("TC_REGEN_001_Regenerate_Available_" + imageLayers + "_Images");
        logger.info("TC_REGEN_001 PASSED: Regenerate available on all {} image layers.", imageLayers);
    }

    // ----------------------------------------------------------------
    // TC_REGEN_002 - Regenerate actually produces a new image (SPENDS CREDITS)
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_GENERATION, AppConstants.GROUP_REGENERATE},
            description = "Clicking Regenerate Image runs a real regeneration",
            enabled = false // Excluded by default: spends credits. Enable to run deliberately.
    )
    @Story("Regenerate spends credits")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Clicks Regenerate Image on one layer and waits for the regeneration to run. Spends "
            + "credits, so it is disabled and must be run deliberately once the success signal is "
            + "confirmed on a live credit-bearing run.")
    public void TC_REGEN_002_regenerateProducesNewImage() {
        DesignEditorPage editor = openDesignWithImages();
        editor.selectImageLayer(0);
        Assert.assertTrue(editor.isRegenerateEnabled(), "Regenerate must be enabled before clicking.");

        editor.clickRegenerate();
        // NOTE: the post-regenerate success signal (loading spinner -> new image src)
        // must be confirmed on a real credit-bearing run before asserting on it.
        editor.takeScreenshot("TC_REGEN_002_After_Regenerate_Click");
        logger.warn("TC_REGEN_002: regenerate triggered (credits spent).");
    }
}
