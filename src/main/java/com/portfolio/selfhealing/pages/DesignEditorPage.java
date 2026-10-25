package com.portfolio.selfhealing.pages;

import com.microsoft.playwright.Locator;
import com.portfolio.selfhealing.base.BasePage;
import com.portfolio.selfhealing.utils.ConfigReader;
import io.qameta.allure.Step;

/**
 * DesignEditorPage - the AI Design "Poster Editor" (route {@code /design/editor}).
 *
 * A generated design opens on a Fabric canvas with a layers panel. Selecting an
 * image layer reveals an Image Properties panel whose actions include
 * "Regenerate Image" — the app's regenerate feature. Every image layer exposes
 * it, so an entire design's images can be regenerated one by one.
 *
 * Selectors verified against the live editor.
 */
public class DesignEditorPage extends BasePage {

    private static final String EDITOR_CANVAS = "canvas";
    private static final String LAYERS_PANEL  = ".layers-panel";
    private static final String LAYERS_LIST   = ".layers-panel__list";
    private static final String LAYER_ITEM    = ".layers-panel__item";
    private static final String LAYER_NAME    = ".layers-panel__item-name";
    private static final String ACTION_BTN    = ".props-panel__action-btn";
    /** The image-only regenerate action in the properties panel. */
    private static final String REGENERATE_BTN =
            ".props-panel__action-btn:has-text('Regenerate Image')";

    // ---- Load state ----

    @Step("Wait for the poster editor to load")
    public DesignEditorPage waitForEditor() {
        waitForVisible(LAYERS_PANEL, ConfigReader.getInstance().getNavigationTimeout());
        waitForVisible(EDITOR_CANVAS);
        // The editor frame renders before the design's layers do — the panel shows
        // "No elements yet" until the design loads onto the canvas. Wait for the
        // layers to populate so a count taken here is not zero on a real design.
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (page.locator(LAYER_ITEM).count() > 0) {
                break;
            }
            page.waitForTimeout(500);
        }
        waitForNetworkIdle();
        logger.info("Poster editor loaded at {} with {} layers",
                getCurrentUrl(), page.locator(LAYER_ITEM).count());
        return this;
    }

    @Override
    public boolean isPageLoaded() {
        return isVisible(LAYERS_PANEL) && isVisible(EDITOR_CANVAS);
    }

    public boolean isEditorRoute() {
        return getCurrentUrl().contains("/design/editor");
    }

    // ---- Layers ----

    /**
     * Layer-name labels for image layers (e.g. "Image 38").
     * <p>
     * Selection is driven off the name label, not the row container: clicking the
     * container does not register a canvas selection, so the properties panel never
     * updates. Clicking the label does.
     */
    private Locator imageLayerNames() {
        return page.locator(LAYER_NAME).filter(new Locator.FilterOptions()
                .setHasText(java.util.regex.Pattern.compile("^Image\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE)));
    }

    public int getImageLayerCount() {
        return imageLayerNames().count();
    }

    @Step("Select image layer #{index}")
    public DesignEditorPage selectImageLayer(int index) {
        imageLayerNames().nth(index).click();
        // Selecting an element swaps the "Select an element…" hint for its actions.
        waitForVisible(ACTION_BTN);
        logger.info("Selected image layer #{}", index);
        return this;
    }

    // ---- Regenerate ----

    public boolean isRegenerateVisible() {
        return isVisible(REGENERATE_BTN);
    }

    public boolean isRegenerateEnabled() {
        return isVisible(REGENERATE_BTN) && isEnabled(REGENERATE_BTN);
    }

    public String getRegenerateLabel() {
        return isVisible(REGENERATE_BTN) ? getText(REGENERATE_BTN).trim() : "";
    }

    /**
     * Clicks Regenerate Image on the selected layer.
     * <p>
     * SPENDS CREDITS — this triggers a real image generation. Only call from a
     * deliberately-run, credit-aware test, never the default suite.
     */
    @Step("Click Regenerate Image (spends credits)")
    public DesignEditorPage clickRegenerate() {
        click(REGENERATE_BTN);
        logger.warn("Clicked Regenerate Image — this spends credits.");
        return this;
    }
}
