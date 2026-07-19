package io.github.hhagenbuch.meter.spring.budget;

import io.github.hhagenbuch.meter.core.budget.BudgetDecision;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.CallContext;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.CostResult;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Enforces budgets by degrading before denying (DESIGN.md section 6). Before a call it
 * consults the {@link BudgetLedger}: {@code block} fails fast (the delegate is never
 * called); {@code degrade} swaps the request to the cheaper model; {@code warn} and
 * {@code allow} proceed. After the call it records the actual cost so the window
 * accumulates and a degraded scope recovers on its own at window rollover.
 *
 * <p>This decorator sits <b>outside</b> the metering decorator, so a degraded model is
 * swapped in <em>before</em> the meter records it — the span shows the model that actually
 * ran, tagged {@code agent.budget_degraded}. See {@code Instrumentation}.
 */
public final class BudgetEnforcingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final BudgetLedger ledger;
    private final CostEngine costEngine;
    private final Clock clock;
    private final BudgetEventListener listener;

    public BudgetEnforcingLlmClient(LlmClient delegate, BudgetLedger ledger, CostEngine costEngine,
                                    Clock clock, BudgetEventListener listener) {
        this.delegate = delegate;
        this.ledger = ledger;
        this.costEngine = costEngine;
        this.clock = clock;
        this.listener = listener;
    }

    @Override
    public LlmResponse call(LlmRequest request) {
        CallContext context = new CallContext(request.feature(), request.sessionId(), request.requestedModel());
        BudgetDecision decision = ledger.check(context);
        if (decision.outcome() != BudgetDecision.Outcome.ALLOW) {
            listener.onEnforced(decision, context);
        }
        if (decision.blocks()) {
            throw new BudgetExceededException(decision.message());
        }
        LlmRequest effective = decision.degrades()
                ? request.degradedTo(decision.degradeToModel())
                : request;

        LlmResponse response = delegate.call(effective);

        // Charge the window with the real cost (bucketed by scope, so the swapped model
        // doesn't matter for bucketing — the cheaper spend just accumulates more slowly).
        CostResult cost = costEngine.cost(response.responseModel(), response.totalUsage(),
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (cost.known()) {
            ledger.record(context, cost.usd());
        }
        return response;
    }
}
