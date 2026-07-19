package io.github.hhagenbuch.meter.core.budget;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Ties budgets to a {@link BudgetStore} and a clock. Before a call, {@link #check} returns
 * the most restrictive decision across every applicable budget; after a call,
 * {@link #record} charges its cost to each applicable budget's current window.
 *
 * <p>Because windows are calendar-aligned, spend is keyed by the window bucket — so when
 * the clock rolls into a new window, accumulated spend is zero again and a degraded or
 * blocked scope recovers automatically, no reset job required.
 */
public final class BudgetLedger {

    private final List<Budget> budgets;
    private final BudgetStore store;
    private final Clock clock;

    public BudgetLedger(List<Budget> budgets, BudgetStore store, Clock clock) {
        this.budgets = List.copyOf(budgets);
        this.store = store;
        this.clock = clock;
    }

    /** The decision for this call: the most restrictive of every budget that applies. */
    public BudgetDecision check(CallContext ctx) {
        Instant now = clock.instant();
        BudgetDecision worst = BudgetDecision.ALLOW;
        for (Budget budget : budgets) {
            if (!budget.scope().matches(ctx)) {
                continue;
            }
            double spent = store.getSpend(budget.scope().bucketKey(ctx), budget.window().key(now));
            BudgetDecision decision = BudgetPolicy.decide(budget, spent, ctx);
            if (decision.isMoreRestrictiveThan(worst)) {
                worst = decision;
            }
        }
        return worst;
    }

    /** Charge a call's cost to every applicable budget's current window. */
    public void record(CallContext ctx, double costUsd) {
        Instant now = clock.instant();
        for (Budget budget : budgets) {
            if (budget.scope().matches(ctx)) {
                store.addSpend(budget.scope().bucketKey(ctx), budget.window().key(now), costUsd);
            }
        }
    }
}
