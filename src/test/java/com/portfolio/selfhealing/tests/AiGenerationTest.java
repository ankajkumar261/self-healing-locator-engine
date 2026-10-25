package com.portfolio.selfhealing.tests;

import com.portfolio.selfhealing.constants.AiTool;
import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.pages.AiToolPage;
import io.qameta.allure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * AiGenerationTest - real end-to-end generation. SPENDS CREDITS.
 *
 * Not part of the default suite. Run deliberately:
 *
 *   mvn test -Dtest=AiGenerationTest                        # Chat (free) + Image + Document (26 credits)
 *   mvn test -Dtest=AiGenerationTest -Dgeneration.all=true  # all seven tools (~343 credits)
 *
 * The RetryListener deliberately skips this group so a flake cannot double the
 * spend. AI Chat is unmetered and always runs.
 */
@Epic("Self-Healing Locator Engine AI Platform")
@Feature("AI Generation")
public class AiGenerationTest extends SingleSessionTest {

    /** Cheapest tools that still exercise the full generate path. */
    private static final AiTool[] DEFAULT_TOOLS = {AiTool.IMAGE, AiTool.DOCUMENT};

    private static final int GENERATION_TIMEOUT_MS = 180_000;

    @DataProvider(name = "generationTools")
    public Object[][] generationTools() {
        boolean all = Boolean.getBoolean("generation.all");
        AiTool[] tools = all
                ? java.util.Arrays.stream(AiTool.values())
                    .filter(AiTool::hasCreditLabel)   // Chat is covered separately
                    .toArray(AiTool[]::new)
                : DEFAULT_TOOLS;

        int cost = java.util.Arrays.stream(tools).mapToInt(AiTool::creditCost).sum();
        logger.warn("Generation run will spend approximately {} credits across {}",
                cost, java.util.Arrays.toString(tools));

        return java.util.Arrays.stream(tools).map(t -> new Object[]{t}).toArray(Object[][]::new);
    }

    // ----------------------------------------------------------------
    // TC_GEN_001 - AI Chat returns an assistant reply (unmetered)
    // ----------------------------------------------------------------
    @Test(
            groups = {AppConstants.GROUP_GENERATION},
            description = "AI Chat returns an assistant reply to a prompt"
    )
    @Story("Text Generation")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Chat is unmetered, so this verifies the generation path end to end at no credit cost.")
    public void TC_GEN_001_chatReturnsReply() {
        AiToolPage chat = new AiToolPage(AiTool.CHAT).open();

        String prompt = "Reply with exactly the word PONG and nothing else.";
        chat.enterPrompt(prompt).clickGenerate();

        String reply = chat.waitForAssistantReply(GENERATION_TIMEOUT_MS);

        Assert.assertEquals(chat.getLastUserMessage(), prompt,
                "The sent prompt should appear as the user message.");
        Assert.assertFalse(reply.isEmpty(), "Assistant reply should not be empty.");
        Assert.assertFalse(chat.isPaywallShown(),
                "Chat is advertised as unmetered and must not raise the upgrade dialog.");

        chat.takeScreenshot("TC_GEN_001_Chat_Reply");
        logger.info("TC_GEN_001 PASSED: assistant replied '{}'", reply);
    }

    // ----------------------------------------------------------------
    // TC_GEN_002 - Credit-priced tools actually generate
    // ----------------------------------------------------------------
    @Test(
            dataProvider = "generationTools",
            groups = {AppConstants.GROUP_GENERATION},
            description = "Each credit-priced tool produces media for a prompt"
    )
    @Story("Media Generation")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Submits a real prompt and waits for generated media. Spends the tool's advertised credit cost.")
    public void TC_GEN_002_toolGeneratesMedia(AiTool tool) {
        AiToolPage page = new AiToolPage(tool).open();

        page.enterPrompt(tool.samplePrompt());
        Assert.assertTrue(page.isGenerateEnabled(),
                tool.displayName() + " generate button should be enabled before submitting.");

        page.clickGenerate();
        boolean produced = page.waitForGeneratedMedia(GENERATION_TIMEOUT_MS);

        // Separate the three ways this fails, because they need different fixes:
        // lapsed session, plan limit, or a genuine generation failure.
        Assert.assertFalse(page.isAuthModalShown(),
                tool.displayName() + " asked us to sign in — the session lapsed. "
                        + "Delete target/state/auth.json and re-run.");
        Assert.assertFalse(page.isPaywallShown(),
                tool.displayName() + " raised the upgrade dialog ('" + page.getPaywallHeading()
                        + "') instead of generating, despite an advertised cost of "
                        + tool.creditCost() + " credits.");
        Assert.assertTrue(produced,
                tool.displayName() + " produced no media within "
                        + (GENERATION_TIMEOUT_MS / 1000) + "s.");

        page.takeScreenshot("TC_GEN_002_" + tool.name() + "_Generated");
        logger.info("TC_GEN_002 PASSED: {} generated media.", tool.displayName());
    }
}
