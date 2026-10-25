package com.portfolio.selfhealing.constants;

/**
 * The seven AI tools in the Self-Healing Locator Engine workspace.
 *
 * Every tool follows the same shape — a prompt textarea plus a generate button
 * that stays disabled until the prompt is non-empty — so one page object drives
 * all of them. Routes, selectors and credit costs were read off the live app.
 *
 * NO_CREDIT_LABEL marks a tool whose button carries no price (AI Chat bills per
 * token rather than a flat rate).
 */
public enum AiTool {

    // -1 is NO_CREDIT_LABEL; the constant cannot be named here (forward reference).
    CHAT    ("AI Chat",     "/chat",     ".ai-chat-page__textarea",   ".ai-chat-page__submit-btn",   -1),
    // Priced off the selected model: 12 for the standard Flux variants, 20 for
    // Flux 1.1 Pro. 12 is the default the page opens with.
    IMAGE   ("AI Image",    "/image",    ".ai-image-page__textarea",  ".ai-image-page__submit-btn",  12),
    VIDEO   ("AI Video",    "/video",    ".ai-video-page__textarea",  ".ai-video-page__submit-btn",  125),
    MUSIC   ("AI Music",    "/music",    ".ai-music-page__textarea",  ".ai-music-page__generate-btn", 40),
    // 12 on live. The app's own static fallback says 40 and paints first, so a
    // price read before /api/payments/me/ lands sees 40 — that is the placeholder,
    // not the charge. Read only after the button stops changing.
    DESIGN  ("AI Design",   "/design",   ".ai-design-page__textarea", ".ai-design-page__generate-btn", 12),
    SLIDES  ("AI Slides",   "/slides",   ".slides-page__textarea",    ".slides-page__submit-btn",    112),
    // The /document page sends no model, so the backend applies its serializer
    // default (moonshot-v1-8k) and charges ai_document_moonshot_v1_8k = 3. The
    // old 14 was the retired generate_pdf endpoint's price.
    DOCUMENT("AI Document", "/document", ".ai-doc-page__textarea",    ".ai-doc-page__generate-btn",    3);

    public static final int NO_CREDIT_LABEL = -1;

    private final String displayName;
    private final String route;
    private final String promptSelector;
    private final String generateSelector;
    private final int creditCost;

    AiTool(String displayName, String route, String promptSelector,
           String generateSelector, int creditCost) {
        this.displayName = displayName;
        this.route = route;
        this.promptSelector = promptSelector;
        this.generateSelector = generateSelector;
        this.creditCost = creditCost;
    }

    public String displayName()      { return displayName; }
    public String route()            { return route; }
    public String promptSelector()   { return promptSelector; }
    public String generateSelector() { return generateSelector; }
    public int creditCost()          { return creditCost; }

    public boolean hasCreditLabel() {
        return creditCost != NO_CREDIT_LABEL;
    }

    /**
     * A realistic prompt for this tool.
     * <p>
     * Deliberately not a generic "Automated test prompt for X". The models
     * behind these tools reject input they cannot do anything with, and a
     * placeholder string produced "Failed to generate: Something went wrong"
     * on Document — which reads as a broken feature when it is really a
     * meaningless request. These mirror the app's own template prompts, so a
     * failure here is the tool failing, not the prompt.
     */
    public String samplePrompt() {
        return switch (this) {
            case DOCUMENT -> "Create a one-page project status report for a website "
                    + "redesign, with an executive summary, current progress and next steps.";
            case SLIDES   -> "Create a short deck introducing a new mobile banking app, "
                    + "covering the problem, the solution and three key features.";
            case IMAGE    -> "A calm mountain lake at sunrise, mist on the water, "
                    + "photographic, soft natural light.";
            case VIDEO    -> "A slow aerial shot drifting over a forest at sunrise, "
                    + "light mist between the trees.";
            case MUSIC    -> "A gentle acoustic guitar instrumental, warm and unhurried.";
            case DESIGN   -> "A clean event poster for a weekend coffee festival, "
                    + "warm colours, bold heading, plenty of white space.";
            case CHAT     -> "Reply with exactly the word PONG and nothing else.";
        };
    }
}
