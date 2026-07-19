package io.github.hhagenbuch.meter.core.budget;

/**
 * One declarative budget: a spend limit over a window for a scope, and what to do when
 * it's breached.
 *
 * @param degradeTo cheaper model to switch to; required when {@code onBreach == DEGRADE}
 */
public record Budget(BudgetScope scope, double limitUsd, Window window,
                     BudgetAction onBreach, String degradeTo) {

    public Budget {
        if (onBreach == BudgetAction.DEGRADE && (degradeTo == null || degradeTo.isBlank())) {
            throw new IllegalArgumentException("budget on " + scope.describe()
                    + " uses on_breach=degrade but no degrade_to model was set");
        }
    }
}
