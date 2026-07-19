package io.github.hhagenbuch.meter.spring.budget;

import io.github.hhagenbuch.meter.core.budget.BudgetDecision;
import io.github.hhagenbuch.meter.core.budget.CallContext;

/**
 * Notified whenever a budget takes effect (WARN / DEGRADE / BLOCK) — for emitting a
 * metric or an event. Kept separate from the decorator so enforcement doesn't depend on
 * any particular telemetry backend.
 */
@FunctionalInterface
public interface BudgetEventListener {

    void onEnforced(BudgetDecision decision, CallContext context);

    BudgetEventListener NO_OP = (decision, context) -> { };
}
