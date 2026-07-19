package io.github.hhagenbuch.meter.core.budget;

/**
 * What the caller should do for this call.
 *
 * @param degradeToModel the cheaper model to use, when {@code outcome == DEGRADE}
 * @param message        an operator-facing explanation for WARN/DEGRADE/BLOCK (null for ALLOW)
 */
public record BudgetDecision(Outcome outcome, String degradeToModel, String message) {

    /** Ordered least → most restrictive, so the most restrictive applicable budget wins. */
    public enum Outcome { ALLOW, WARN, DEGRADE, BLOCK }

    public static final BudgetDecision ALLOW = new BudgetDecision(Outcome.ALLOW, null, null);

    public boolean isMoreRestrictiveThan(BudgetDecision other) {
        return outcome.ordinal() > other.outcome.ordinal();
    }

    public boolean blocks() {
        return outcome == Outcome.BLOCK;
    }

    public boolean degrades() {
        return outcome == Outcome.DEGRADE;
    }
}
