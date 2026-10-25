package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AiTool;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.AiToolPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * AiToolsTest - UI coverage for all seven AI tools. Spends no credits.
 *
 * Every tool is verified to load, accept a prompt, gate its generate button on
 * that prompt, and advertise the credit price the app actually charges. Real
 * generation lives in {@link AiGenerationTest}, which costs credits and is not
 * part of the default suite.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("AI Tools")
public class AiToolsTest extends SingleSessionTest {

    @DataProvider(name = "allTools")
    public Object[][] allTools() {
        AiTool[] tools = AiTool.values();
        Object[][] data = new Object[tools.length][1];
        for (int i = 0; i < tools.length; i++) {
            data[i][0] = tools[i];
        }
        return data;
    }

    @DataProvider(name = "pricedTools")
    public Object[][] pricedTools() {
        return java.util.Arrays.stream(AiTool.values())
                .filter(AiTool::hasCreditLabel)
                .map(t -> new Object[]{t})
                .toArray(Object[][]::new);
    }

    // ----------------------------------------------------------------
    // TC_TOOL_001 - Every tool page loads with prompt + generate button
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "allTools",
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_TOOLS},
            description = "Each AI tool page loads with its prompt field and generate button"
    )
    @Story("Tool Page Load")
    @Severity(SeverityLevel.BLOCKER)
    public void TC_TOOL_001_toolPageLoads(AiTool tool) {
        AiToolPage page = new AiToolPage(tool).open();

        Assert.assertTrue(page.isPageLoaded(),
                tool.displayName() + " should load with prompt and generate button.");
        Assert.assertTrue(page.isSidebarVisible(),
                "Workspace sidebar should stay visible on " + tool.displayName() + ".");

        page.takeScreenshot("TC_TOOL_001_" + tool.name() + "_Loaded");
        logger.info("TC_TOOL_001 PASSED: {} loaded at {}", tool.displayName(), tool.route());
    }

    // ----------------------------------------------------------------
    // TC_TOOL_002 - Generate is disabled until a prompt is entered
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "allTools",
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_TOOLS},
            description = "Generate button is gated on a non-empty prompt"
    )
    @Story("Prompt Validation")
    @Severity(SeverityLevel.CRITICAL)
    @Description("An empty prompt must leave the generate button disabled so no credits can be spent by accident.")
    public void TC_TOOL_002_generateDisabledWithoutPrompt(AiTool tool) {
        AiToolPage page = new AiToolPage(tool).open();

        Assert.assertFalse(page.isGenerateEnabled(),
                tool.displayName() + " generate button should be disabled with an empty prompt.");

        page.enterPrompt(tool.samplePrompt());
        Assert.assertTrue(page.isGenerateEnabled(),
                tool.displayName() + " generate button should enable once a prompt is entered.");

        page.takeScreenshot("TC_TOOL_002_" + tool.name() + "_Generate_Gating");
        logger.info("TC_TOOL_002 PASSED: {} gates generate on prompt.", tool.displayName());
    }

    // ----------------------------------------------------------------
    // TC_TOOL_003 - Prompt text is accepted and retained
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "allTools",
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_TOOLS},
            description = "Prompt textarea accepts and retains typed text"
    )
    @Story("Prompt Input")
    @Severity(SeverityLevel.NORMAL)
    public void TC_TOOL_003_promptAcceptsText(AiTool tool) {
        AiToolPage page = new AiToolPage(tool).open();

        String prompt = tool.samplePrompt();
        page.enterPrompt(prompt);

        Assert.assertEquals(page.getPromptValue(), prompt,
                tool.displayName() + " should retain the entered prompt.");
        Assert.assertFalse(page.getPromptPlaceholder().isEmpty(),
                tool.displayName() + " prompt field should have placeholder guidance.");

        logger.info("TC_TOOL_003 PASSED: {} accepted prompt, placeholder: '{}'",
                tool.displayName(), page.getPromptPlaceholder());
    }

    // ----------------------------------------------------------------
    // TC_TOOL_004 - Advertised credit cost matches the expected price
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "pricedTools",
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_TOOLS},
            description = "Generate button advertises the expected credit price"
    )
    @Story("Credit Pricing")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Guards against a silent pricing change: the button must show the credit cost the suite expects.")
    public void TC_TOOL_004_creditCostIsAdvertised(AiTool tool) {
        AiToolPage page = new AiToolPage(tool).open()
                .enterPrompt(tool.samplePrompt());

        int advertised = page.getAdvertisedCreditCost();

        Assert.assertNotEquals(advertised, AiTool.NO_CREDIT_LABEL,
                tool.displayName() + " should advertise a credit cost. Label was: '"
                        + page.getGenerateLabel() + "'");
        Assert.assertEquals(advertised, tool.creditCost(),
                tool.displayName() + " credit price changed. Button reads: '"
                        + page.getGenerateLabel() + "'");

        logger.info("TC_TOOL_004 PASSED: {} advertises {} credits.",
                tool.displayName(), advertised);
    }

    // ----------------------------------------------------------------
    // TC_TOOL_005 - Tools are reachable from the agent grid
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_SMOKE, AppConstants.GROUP_TOOLS},
            description = "Agent cards on the workspace home open their tool"
    )
    @Story("Tool Navigation")
    @Severity(SeverityLevel.CRITICAL)
    public void TC_TOOL_005_toolsReachableFromAgentGrid() {
        // Return to the workspace home, then open a tool by its card.
        AiToolPage image = new AiToolPage(AiTool.IMAGE);
        image.navigateTo(config.getBaseUrl());
        image.openAgentPanel();

        // Every tool should be represented in the grid.
        java.util.List<String> cards = image.getAgentCardLabels();
        for (AiTool tool : AiTool.values()) {
            String expected = tool == AiTool.IMAGE ? "AI Photo" : tool.displayName();
            Assert.assertTrue(cards.contains(expected),
                    "Agent grid should offer '" + expected + "'. Found: " + cards);
        }

        image.openFromAgentGrid();
        Assert.assertTrue(image.isPageLoaded(),
                "AI Photo card should open the image tool.");
        Assert.assertTrue(image.getCurrentUrl().contains(AiTool.IMAGE.route()),
                "URL should be " + AiTool.IMAGE.route() + " after opening from the grid. Actual: "
                        + image.getCurrentUrl());

        image.takeScreenshot("TC_TOOL_005_Agent_Grid_Navigation");
        logger.info("TC_TOOL_005 PASSED: agent grid lists {} and opens the image tool.", cards);
    }

    // ----------------------------------------------------------------
    // TC_TOOL_006 - Option pills render for tools that expose them
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_REGRESSION, AppConstants.GROUP_TOOLS},
            description = "Tools with option pills render them with one active by default"
    )
    @Story("Tool Options")
    @Severity(SeverityLevel.NORMAL)
    public void TC_TOOL_006_optionPillsRender() {
        AiTool[] withPills = {AiTool.IMAGE, AiTool.VIDEO, AiTool.MUSIC, AiTool.DESIGN};

        for (AiTool tool : withPills) {
            AiToolPage page = new AiToolPage(tool).open();

            Assert.assertFalse(page.getOptionPillLabels().isEmpty(),
                    tool.displayName() + " should render option pills.");
            Assert.assertFalse(page.getActiveOptionPill().isEmpty(),
                    tool.displayName() + " should have one option pill active by default.");

            logger.info("{} pills: {} | active: '{}'", tool.displayName(),
                    page.getOptionPillLabels(), page.getActiveOptionPill());
        }
        logger.info("TC_TOOL_006 PASSED: option pills render across image/video/music/design.");
    }
}
