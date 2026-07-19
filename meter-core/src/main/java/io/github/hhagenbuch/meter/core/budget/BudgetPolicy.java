package io.github.hhagenbuch.meter.core.budget;

import java.util.Locale;

/**
 * The pure breach decision for a single budget given how much has already been spent in
 * the current window. Enforcement is on <em>accumulated</em> spend checked before a call
 * (a call's own cost isn't known until after it runs), so the call that crosses the limit
 * is allowed and subsequent calls take the action — a bounded, documented overshoot.
 */
public final class BudgetPolicy {

    private BudgetPolicy() {
    }

    public static BudgetDecision decide(Budget budget, double accumulatedUsd, CallContext ctx) {
        if (accumulatedUsd < budget.limitUsd()) {
            return BudgetDecision.ALLOW;
        }
        String where = budget.scope().describe() + " (" + budget.window().name().toLowerCase(Locale.ROOT) + ")";
        String spend = String.format(Locale.ROOT, "$%.4f of $%.2f", accumulatedUsd, budget.limitUsd());
        return switch (budget.onBreach()) {
            case WARN -> new BudgetDecision(BudgetDecision.Outcome.WARN, null,
                    "Budget WARNING: " + where + " has spent " + spend + " this window.");
            case DEGRADE -> new BudgetDecision(BudgetDecision.Outcome.DEGRADE, budget.degradeTo(),
                    "Budget degraded: " + where + " reached " + spend + "; routing to "
                            + budget.degradeTo() + " for the rest of the window.");
            case BLOCK -> new BudgetDecision(BudgetDecision.Outcome.BLOCK, null,
                    "Budget exceeded: " + where + " has spent " + spend
                            + "; requests are blocked until the window resets. "
                            + "Raise the limit or wait for the next window.");
        };
    }
}
