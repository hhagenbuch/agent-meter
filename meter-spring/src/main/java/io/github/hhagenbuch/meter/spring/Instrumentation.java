package io.github.hhagenbuch.meter.spring;

import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.budget.BudgetEnforcingLlmClient;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.otel.MeteringFactory;

import java.time.Clock;

/**
 * The one-call entry point: wrap a provider {@link LlmClient} with the full agent-meter
 * stack. Encodes the decorator ordering — <b>budget enforcement outside metering</b> — so
 * a budget-degraded model is swapped in before the meter records it, and the span shows
 * the model that actually ran (DESIGN.md section 7).
 *
 * <pre>caller -&gt; budget-enforce -&gt; meter -&gt; provider</pre>
 */
public final class Instrumentation {

    private final MeteringFactory metering;
    private final BudgetLedger ledger;
    private final CostEngine costEngine;
    private final Clock clock;
    private final BudgetEventListener listener;

    public Instrumentation(MeteringFactory metering, BudgetLedger ledger, CostEngine costEngine,
                           Clock clock, BudgetEventListener listener) {
        this.metering = metering;
        this.ledger = ledger;
        this.costEngine = costEngine;
        this.clock = clock;
        this.listener = listener;
    }

    public LlmClient instrument(LlmClient provider) {
        LlmClient metered = metering.wrap(provider);                 // inner: telemetry
        return new BudgetEnforcingLlmClient(metered, ledger, costEngine, clock, listener); // outer: enforcement
    }
}
