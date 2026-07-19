package io.github.hhagenbuch.meter.starter;

import io.github.hhagenbuch.agent.config.AgentProperties;
import io.github.hhagenbuch.agent.llm.LlmClient;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.opentelemetry.api.trace.Tracer;

import java.time.Clock;

/** Builds a {@link StarterMeteringLlmClient} around a starter {@link LlmClient} bean. */
public final class StarterMeteringFactory {

    private final Tracer tracer;
    private final Instruments instruments;
    private final CostEngine costEngine;
    private final BudgetLedger ledger;
    private final BudgetEventListener budgetListener;
    private final AgentProperties agentProperties;
    private final Clock clock;
    private final String genAiSystem;
    private final String promptVersion;

    public StarterMeteringFactory(Tracer tracer, Instruments instruments, CostEngine costEngine,
                                  BudgetLedger ledger, BudgetEventListener budgetListener,
                                  AgentProperties agentProperties, Clock clock,
                                  String genAiSystem, String promptVersion) {
        this.tracer = tracer;
        this.instruments = instruments;
        this.costEngine = costEngine;
        this.ledger = ledger;
        this.budgetListener = budgetListener;
        this.agentProperties = agentProperties;
        this.clock = clock;
        this.genAiSystem = genAiSystem;
        this.promptVersion = promptVersion;
    }

    public LlmClient wrap(LlmClient delegate) {
        return new StarterMeteringLlmClient(delegate, agentProperties.model(), tracer, instruments,
                costEngine, ledger, budgetListener, clock, genAiSystem, promptVersion);
    }
}
