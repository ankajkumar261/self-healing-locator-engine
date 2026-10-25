package com.portfolio.selfhealing.pages;

import com.portfolio.selfhealing.base.BasePage;
import com.portfolio.selfhealing.constants.AiTool;
import com.portfolio.selfhealing.utils.ConfigReader;
import io.qameta.allure.Step;

/**
 * AiToolPage - one page object for all seven AI tools.
 *
 * Each tool is its own route with its own BEM-prefixed classes, but the
 * interaction model is identical, so the tool is passed in rather than
 * duplicated across seven near-identical classes.
 */
public class AiToolPage extends BasePage {

    // Shared chrome, identical across tools.
    private static final String SIDEBAR        = "aside.sidebar";
    private static final String PROFILE_MENU   = ".profile-popup";
    private static final String USER_AVATAR    = ".sidebar__avatar";
    private static final String CREDIT_VALUE   = ".profile-popup__credit-value";
    private static final String ERROR_TOAST    = "[class*='error'], [role='alert']";
    private static final String NEW_TRIGGER     = "button.sidebar-item[aria-label='New']";
    private static final String AGENT_PANEL_OPEN = ".agent-panel--visible";

    /** Upgrade dialog the app raises instead of generating. */
    private static final String PAYWALL          = ".pricing-modal";
    /**
     * Charge confirmation. The app raises this before any single generation
     * costing 100 credits or more, so Video (125) and Slides (112) stop here
     * while Image (12) and Document (14) never do.
     */
    private static final String CONFIRM_CHARGE       = ".confirm-charge__card";
    private static final String CONFIRM_CHARGE_COST  = ".confirm-charge__cost";
    private static final String CONFIRM_CHARGE_YES   = ".confirm-charge__btn--primary";
    /** Credit cost at or above which the app demands confirmation. */
    public static final int CONFIRM_CHARGE_THRESHOLD = 100;
    /**
     * How long to let the wallet load before trusting a balance of 0. The store
     * starts at 0 and is hydrated from /api/payments/me/ after every navigation.
     */
    private static final int WALLET_LOAD_TIMEOUT_MS = 15_000;
    /**
     * Same story for the button price, which is billing-API driven — but worse:
     * the button does not show a placeholder 0, it shows a static FALLBACK price
     * from the app's own config until /api/payments/me/ answers, then swaps in
     * the server's real figure. Taking the first non-zero reading therefore
     * captures whichever of the two happened to win the race, which is how the
     * same tool reported 40 credits in one run and 12 in the next. The price is
     * only trustworthy once it has stopped changing.
     */
    private static final int PRICE_LOAD_TIMEOUT_MS  = 10_000;
    private static final int PRICE_POLL_MS          = 600;
    /** Consecutive matching reads before a price is considered settled. */
    private static final int PRICE_STABLE_READS     = 2;
    /**
     * How long to let a signed-out tool route settle into render-or-gate.
     * <p>
     * Takes the navigation budget rather than a shorter fixed value: deciding
     * "render or redirect" is the app booting, and on a slow environment that
     * routinely outruns a 45s guess — which then reads as a stranded blank page
     * rather than as a slow one.
     */
    private static int routeGateTimeoutMs() {
        return (int) ConfigReader.getInstance().getNavigationTimeout();
    }
    /** Grace period for a route guard that fires just after first paint. */
    private static final int PROMPT_SETTLE_MS       = 2_500;
    /** Sign-in dialog the app raises when the session has lapsed. */
    private static final String AUTH_MODAL       = ".auth-modal";
    private static final String CHAT_ASSISTANT   = ".ai-chat-page__message--assistant";
    private static final String CHAT_USER        = ".ai-chat-page__message--user";
    /** Media a completed generation renders, excluding static workspace icons. */
    private static final String RESULT_MEDIA     =
            "img[src^='http']:not(.feature-pill__icon), video, audio";
    /**
     * Document results render a dedicated PDF viewer. The viewer root is the
     * signal rather than its Download control: the control is a {@code <button>}
     * here but an {@code <a>} on the slides page, so a shared
     * "button:has-text('Download')" silently never matched slides at all.
     */
    private static final String RESULT_DOCUMENT  =
            ".ai-doc-page--viewer, .ai-doc-page__viewer-header";
    /** Slides render an Office Online viewer plus an anchor-based download. */
    private static final String RESULT_SLIDES    =
            "a:has-text('Download'), iframe[src*='officeapps'], iframe[src*='view.officeapps']";
    private static final String DOC_LOADING      = ":text('Loading document')";
    /**
     * The failure state a tool renders when the backend refuses the job, e.g.
     * ".ai-doc-page__error" carrying "Failed to generate: ...". Distinct from a
     * timeout: the app knows it failed and is saying so.
     */
    private static final String GENERATION_ERROR = "[class*='-page__error']";

    /** Message from the app's own failure state, when the last wait saw one. */
    private String lastGenerationError = "";

    private final AiTool tool;

    public AiToolPage(AiTool tool) {
        this.tool = tool;
    }

    public AiTool tool() {
        return tool;
    }

    // ---- Navigation ----

    @Step("Open {this.tool} tool page")
    public AiToolPage open() {
        String baseUrl = ConfigReader.getInstance().getBaseUrl().replaceAll("/$", "");
        navigateTo(baseUrl + tool.route());
        waitForVisible(tool.promptSelector(),
                ConfigReader.getInstance().getNavigationTimeout());
        logger.info("Opened {} at {}", tool.displayName(), tool.route());
        return this;
    }

    /**
     * Opens the tool route without assuming a session, and reports whether the
     * tool page actually rendered.
     * <p>
     * The environments differ here, and both are legitimate gates:
     * <ul>
     *   <li>staging renders the whole tool page signed out, and only blocks on
     *       the Generate click;</li>
     *   <li>production guards the route itself — /document bounces to / and
     *       raises the sign-in modal before anything renders.</li>
     * </ul>
     * {@link #open()} insists on the prompt appearing, so it cannot be used to
     * test the signed-out path on an environment that guards the route.
     *
     * @return true if the tool page rendered (the gate is still ahead of us),
     *         false if the route itself was gated.
     */
    @Step("Open {this.tool} without a session")
    public boolean openWhileSignedOut() {
        String baseUrl = ConfigReader.getInstance().getBaseUrl().replaceAll("/$", "");
        navigateTo(baseUrl + tool.route());

        long deadline = System.currentTimeMillis() + routeGateTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (isAuthModalShown()) {
                logger.info("{} gated the route and raised the sign-in modal.",
                        tool.displayName());
                return false;
            }

            if (isVisible(tool.promptSelector())) {
                // The guard can redirect a beat AFTER the page paints, so a prompt
                // seen once is not proof the page stayed. Reporting "rendered" here
                // sends the caller on to type into a textarea that is about to be
                // torn out from under it.
                page.waitForTimeout(PROMPT_SETTLE_MS);
                if (isAuthModalShown() || isRouteGated()
                        || !isVisible(tool.promptSelector())) {
                    logger.info("{} painted briefly, then the route guard took over.",
                            tool.displayName());
                    return false;
                }
                logger.info("{} rendered its page while signed out.", tool.displayName());
                return true;
            }

            if (isRouteGated()) {
                // Bounced off its own route. The modal may still be arriving.
                page.waitForTimeout(PROMPT_SETTLE_MS);
                logger.info("{} was redirected off its route while signed out.",
                        tool.displayName());
                return false;
            }
            page.waitForTimeout(500);
        }
        logger.warn("{} neither rendered nor gated within {}ms.",
                tool.displayName(), routeGateTimeoutMs());
        return isVisible(tool.promptSelector());
    }

    /** True once the app has navigated away from this tool's own route. */
    public boolean isRouteGated() {
        return !getCurrentUrl().contains(tool.route());
    }

    /**
     * Opens the tool from the agent grid.
     * <p>
     * The grid lives in a flyout panel, not the main area. Its cards are laid
     * out even while the panel is closed — Playwright sees them as visible and
     * stable, then the click lands on main-content instead. The panel has to be
     * opened first, and {@code .agent-panel--visible} is the signal that it is.
     */
    @Step("Open {this.tool} from the agent grid")
    public AiToolPage openFromAgentGrid() {
        openAgentPanel();
        click(String.format("%s button.agent-card[aria-label='%s']",
                AGENT_PANEL_OPEN, agentCardLabel()));
        waitForVisible(tool.promptSelector(),
                ConfigReader.getInstance().getNavigationTimeout());
        logger.info("Opened {} from agent grid", tool.displayName());
        return this;
    }

    @Step("Open the agent panel")
    public AiToolPage openAgentPanel() {
        if (!isVisible(AGENT_PANEL_OPEN)) {
            click(NEW_TRIGGER);
            waitForVisible(AGENT_PANEL_OPEN);
        }
        return this;
    }

    public java.util.List<String> getAgentCardLabels() {
        return page.locator(AGENT_PANEL_OPEN + " button.agent-card")
                .all().stream().map(l -> l.getAttribute("aria-label")).toList();
    }

    /** The agent grid labels the image tool "AI Photo" while the route is /image. */
    private String agentCardLabel() {
        return tool == AiTool.IMAGE ? "AI Photo" : tool.displayName();
    }

    // ---- Prompt ----

    @Step("Enter prompt: {prompt}")
    public AiToolPage enterPrompt(String prompt) {
        fill(tool.promptSelector(), prompt);
        logger.info("Entered prompt into {}: {}", tool.displayName(), prompt);
        return this;
    }

    @Step("Clear the prompt")
    public AiToolPage clearPrompt() {
        clearAndFill(tool.promptSelector(), "");
        return this;
    }

    public String getPromptValue() {
        return getInputValue(tool.promptSelector());
    }

    public boolean isPromptVisible() {
        return isVisible(tool.promptSelector());
    }

    public String getPromptPlaceholder() {
        return getAttribute(tool.promptSelector(), "placeholder");
    }

    // ---- Generate ----

    public boolean isGenerateVisible() {
        return isVisible(tool.generateSelector());
    }

    public boolean isGenerateEnabled() {
        return isEnabled(tool.generateSelector());
    }

    /** e.g. "Generate · 12 credits". Empty for tools with no price on the button. */
    public String getGenerateLabel() {
        return isVisible(tool.generateSelector())
                ? getText(tool.generateSelector()).trim()
                : "";
    }

    /**
     * Reads the credit price off the generate button, waiting for it to resolve.
     * <p>
     * The price is not static: it comes from the billing API (and, on the image
     * and video pages, from the selected model), with a placeholder until
     * /api/payments/me/ answers. Read too early the button genuinely says
     * "Generate · 0 credits" on a tool that costs 12 — which reads as a pricing
     * regression when it is only a slow first paint. Chat is legitimately free,
     * so a settled 0 is returned rather than waited on forever.
     *
     * @return the advertised cost, or {@link AiTool#NO_CREDIT_LABEL} when the
     *         button carries no price at all.
     */
    public int getAdvertisedCreditCost() {
        return getAdvertisedCreditCost(PRICE_LOAD_TIMEOUT_MS);
    }

    public int getAdvertisedCreditCost(int timeoutMs) {
        java.util.regex.Pattern pricePattern =
                java.util.regex.Pattern.compile("(\\d+)\\s*(?:credits?|cr)\\b");

        long deadline = System.currentTimeMillis() + timeoutMs;
        int cost = AiTool.NO_CREDIT_LABEL;
        int stableFor = 0;

        while (System.currentTimeMillis() < deadline) {
            java.util.regex.Matcher m = pricePattern.matcher(getGenerateLabel());
            int reading = m.find() ? Integer.parseInt(m.group(1)) : AiTool.NO_CREDIT_LABEL;

            if (reading > 0 && reading == cost) {
                // Two agreeing reads a beat apart mean the billing API has landed
                // and the button is no longer showing the static placeholder.
                if (++stableFor >= PRICE_STABLE_READS) {
                    return cost;
                }
            } else {
                stableFor = 0;
            }
            cost = reading;
            page.waitForTimeout(PRICE_POLL_MS);
        }

        if (cost == 0) {
            logger.info("{} still advertises 0 credits after {}ms — treating it as free.",
                    tool.displayName(), timeoutMs);
        } else if (cost > 0) {
            logger.warn("{} price never settled within {}ms; last read {}.",
                    tool.displayName(), timeoutMs, cost);
        }
        return cost;
    }

    @Step("Click Generate on {this.tool}")
    public AiToolPage clickGenerate() {
        click(tool.generateSelector());
        logger.info("Clicked Generate on {} ({} credits)",
                tool.displayName(), tool.creditCost());
        return this;
    }

    // ---- Charge confirmation (cost >= 100) ----

    public boolean isConfirmChargeShown() {
        return isVisible(CONFIRM_CHARGE);
    }

    /** The cost the confirm dialog advertises, e.g. "−125 cr" -> 125, or -1. */
    public int getConfirmChargeCost() {
        if (!isConfirmChargeShown()) {
            return AiTool.NO_CREDIT_LABEL;
        }
        String raw = getText(CONFIRM_CHARGE_COST).replaceAll("[^0-9]", "");
        return raw.isEmpty() ? AiTool.NO_CREDIT_LABEL : Integer.parseInt(raw);
    }

    /**
     * Accepts the charge dialog if it appeared. Cheap tools never raise it, so
     * this is a no-op for them rather than a failure.
     *
     * @return true if a dialog was actually confirmed.
     */
    @Step("Confirm the credit charge if the dialog appeared")
    public boolean confirmChargeIfShown() {
        // The dialog is raised on click, not instantly; give it a beat to mount
        // so a fast machine cannot skip past it and mistake it for absent.
        if (!isConfirmChargeShown()) {
            page.waitForTimeout(1500);
        }
        if (!isConfirmChargeShown()) {
            return false;
        }
        logger.info("Confirming charge of {} credits on {}",
                getConfirmChargeCost(), tool.displayName());
        click(CONFIRM_CHARGE_YES);
        waitForHidden(CONFIRM_CHARGE);
        return true;
    }

    /**
     * Waits for the charge dialog to appear, since it is raised on click rather
     * than synchronously.
     */
    @Step("Wait for the charge confirmation dialog")
    public boolean waitForConfirmCharge(int timeoutMs) {
        try {
            waitForVisible(CONFIRM_CHARGE, timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Backs out of the charge dialog. Escape is the app's own dismissal path
     * and maps to the same handler as the Cancel button.
     */
    @Step("Cancel the charge confirmation")
    public AiToolPage cancelCharge() {
        if (isConfirmChargeShown()) {
            page.keyboard().press("Escape");
            waitForHidden(CONFIRM_CHARGE);
            logger.info("Cancelled the charge dialog on {}", tool.displayName());
        }
        return this;
    }

    // ---- Generation results ----

    /** True when the app raised its upgrade dialog instead of generating. */
    public boolean isPaywallShown() {
        return isVisible(PAYWALL);
    }

    /**
     * True when the app is asking us to sign in.
     * <p>
     * Distinct from the paywall on purpose: a lapsed session and a plan limit
     * look similar from the outside but need opposite fixes.
     */
    public boolean isAuthModalShown() {
        return isVisible(AUTH_MODAL);
    }

    /**
     * Waits for the sign-in modal the app raises when Generate is clicked without
     * a session. Returns false if it never appears within the timeout.
     */
    @Step("Wait for the sign-in modal on {this.tool}")
    public boolean waitForAuthModal(int timeoutMs) {
        try {
            waitForVisible(AUTH_MODAL, timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True once any generated result (media, or a document/slide viewer) is on the page. */
    public boolean hasGeneratedResult() {
        return isVisible(resultSelector());
    }

    public String getPaywallHeading() {
        return isPaywallShown() ? getText(PAYWALL).trim().split("\n")[0] : "";
    }

    /** Dismisses the upgrade dialog without touching any of its purchase buttons. */
    @Step("Dismiss the upgrade dialog")
    public AiToolPage dismissPaywall() {
        if (isPaywallShown()) {
            page.keyboard().press("Escape");
            logger.warn("Dismissed the upgrade paywall on {}", tool.displayName());
        }
        return this;
    }

    /**
     * Waits for generated media to render.
     * Returns false if the wait expires or the app raised the paywall instead.
     */
    @Step("Wait for {this.tool} to finish generating")
    public boolean waitForGeneratedMedia(int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isAuthModalShown()) {
                logger.warn("{} asked us to sign in — the session lapsed mid-run.",
                        tool.displayName());
                return false;
            }
            if (isPaywallShown()) {
                logger.warn("{} raised the upgrade paywall instead of generating.",
                        tool.displayName());
                return false;
            }
            if (isVisible(resultSelector()) && !isVisible(DOC_LOADING)) {
                logger.info("{} produced a result.", tool.displayName());
                return true;
            }
            // The app's own error state is a definite answer — waiting out the
            // remaining minutes after it appears only delays the report, and
            // "timed out" would describe it far less usefully than what the
            // backend actually said.
            if (isGenerationErrorShown()) {
                lastGenerationError = getGenerationError();
                logger.warn("{} reported a failure: {}",
                        tool.displayName(), lastGenerationError);
                return false;
            }
            page.waitForTimeout(2000);
        }
        logger.warn("{} produced no result within {}ms.", tool.displayName(), timeoutMs);
        return false;
    }

    /** True when the tool is showing its own "failed to generate" state. */
    public boolean isGenerationErrorShown() {
        return isVisible(GENERATION_ERROR);
    }

    /** The text of that failure state, or "" when there is none. */
    public String getGenerationError() {
        return isGenerationErrorShown() ? getText(GENERATION_ERROR).trim() : "";
    }

    /**
     * What the app said about the last generation, for assertion messages:
     * its error text, or a note that it simply never finished.
     */
    public String lastGenerationOutcome() {
        if (!lastGenerationError.isEmpty()) {
            return "the app reported: \"" + lastGenerationError + "\"";
        }
        String live = getGenerationError();
        return live.isEmpty()
                ? "the app showed no error — it simply never produced a result"
                : "the app reported: \"" + live + "\"";
    }

    /** Documents and slides render a viewer; the rest render media. */
    private String resultSelector() {
        return switch (tool) {
            case DOCUMENT -> RESULT_DOCUMENT;
            case SLIDES   -> RESULT_SLIDES;
            default       -> RESULT_MEDIA;
        };
    }

    // ---- AI Chat (unmetered, so it is verified end to end) ----

    /**
     * Waits for the assistant's reply to finish arriving.
     * <p>
     * The bubble is inserted empty and then streamed into, so waiting only for
     * the element yields "". This waits for non-empty text and then for it to
     * stop growing.
     */
    @Step("Wait for the assistant reply")
    public String waitForAssistantReply(int timeoutMs) {
        waitForVisible(CHAT_ASSISTANT, timeoutMs);

        long deadline = System.currentTimeMillis() + timeoutMs;
        String previous = "";
        int stableReads = 0;

        while (System.currentTimeMillis() < deadline) {
            String current = getText(CHAT_ASSISTANT).trim();
            if (!current.isEmpty() && current.equals(previous)) {
                if (++stableReads >= 2) {
                    return current;
                }
            } else {
                stableReads = 0;
            }
            previous = current;
            page.waitForTimeout(1000);
        }
        logger.warn("Assistant reply did not settle within {}ms; last value: '{}'",
                timeoutMs, previous);
        return previous;
    }

    public String getLastUserMessage() {
        return isVisible(CHAT_USER) ? getText(CHAT_USER).trim() : "";
    }

    // ---- Option pills (aspect ratio, model, size, ...) ----

    /** Returns the visible labels of this tool's option pills, in DOM order. */
    public java.util.List<String> getOptionPillLabels() {
        return page.locator(optionPillSelector()).allTextContents()
                .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String getActiveOptionPill() {
        String active = optionPillSelector() + "--active";
        return isVisible(active) ? getText(active).trim() : "";
    }

    private String optionPillSelector() {
        return switch (tool) {
            case IMAGE, VIDEO, MUSIC -> tool.promptSelector()
                    .replace("__textarea", "__option-pill");
            case DESIGN -> ".ai-design-page__size-pill";
            case SLIDES -> ".slides-page__pill";
            default -> tool.promptSelector().replace("__textarea", "__option-pill");
        };
    }

    // ---- Shared workspace chrome ----

    public boolean isSidebarVisible() {
        return isVisible(SIDEBAR);
    }

    public boolean isErrorShown() {
        return isVisible(ERROR_TOAST);
    }

    /** Opens the profile popup and reads the remaining credit balance. */
    @Step("Read remaining credit balance")
    public int getCreditBalance() {
        return getCreditBalance(WALLET_LOAD_TIMEOUT_MS);
    }

    /**
     * Reads the credit balance, waiting for the wallet to actually load.
     * <p>
     * The credits store starts at 0 and is hydrated from /api/payments/me/ after
     * the page settles, so the popup genuinely renders "0" for a second or two
     * after every navigation. Reading it immediately yields 0 on a funded
     * account — which, in a credit test, looks exactly like "the balance was
     * drained" and is completely wrong.
     * <p>
     * A balance that is still 0 when the budget expires is reported as 0: a
     * genuinely empty wallet and an unresolved fetch are indistinguishable from
     * the DOM, so the ambiguity is logged rather than hidden.
     */
    @Step("Read remaining credit balance")
    public int getCreditBalance(int timeoutMs) {
        // The avatar is a toggle, so clicking it while the popup is already open
        // dismisses the very thing the next line waits for.
        if (!isVisible(PROFILE_MENU)) {
            click(USER_AVATAR);
            waitForVisible(PROFILE_MENU);
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        int balance = 0;
        while (System.currentTimeMillis() < deadline) {
            String raw = getText(CREDIT_VALUE).replaceAll("[^0-9]", "");
            balance = raw.isEmpty() ? -1 : Integer.parseInt(raw);
            if (balance > 0) {
                break;
            }
            page.waitForTimeout(1000);
        }

        if (balance <= 0) {
            logger.warn("Credit balance still reads {} after {}ms — the wallet fetch "
                    + "may not have resolved, or the account really is empty.",
                    balance, timeoutMs);
        }

        // Close the popup so it does not overlay later interactions.
        page.keyboard().press("Escape");
        logger.info("Credit balance: {}", balance);
        return balance;
    }

    /**
     * Waits for the balance to move away from {@code before}, then returns the
     * settled value.
     * <p>
     * The debit is applied server-side and pushed back into the store, so the
     * popup can still show the pre-generation number for a moment after the
     * result renders. Reading once races that update and reports "no credits
     * deducted" on a system that merely hadn't refreshed yet.
     * <p>
     * Returns {@code before} unchanged if the balance never moves — which is a
     * genuine "nothing was deducted", and the caller's assertion should say so.
     */
    @Step("Wait for the credit balance to settle after generating")
    public int waitForSettledCreditBalance(int before, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int latest = before;
        // The wallet is already hydrated by this point, so each read gets a short
        // budget — the long one would burn this whole timeout on a few polls.
        final int readMs = 5_000;
        while (System.currentTimeMillis() < deadline) {
            latest = getCreditBalance(readMs);
            if (latest != before) {
                // Let any follow-up correction land before trusting the number.
                page.waitForTimeout(2000);
                int confirmed = getCreditBalance(readMs);
                if (confirmed != latest) {
                    logger.warn("Balance moved again ({} -> {}) after settling.",
                            latest, confirmed);
                }
                return confirmed;
            }
            page.waitForTimeout(2000);
        }
        logger.warn("Credit balance never moved from {} within {}ms.", before, timeoutMs);
        return latest;
    }

    @Override
    public boolean isPageLoaded() {
        return isVisible(tool.promptSelector()) && isVisible(tool.generateSelector());
    }
}
