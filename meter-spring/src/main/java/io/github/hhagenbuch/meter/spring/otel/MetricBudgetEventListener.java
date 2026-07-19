package io.github.hhagenbuch.meter.spring.otel;

import io.github.hhagenbuch.meter.core.budget.BudgetDecision;
import io.github.hhagenbuch.meter.core.budget.CallContext;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Locale;

/** Records every budget enforcement as {@code agent.budget.enforced} counter points, by action. */
public final class MetricBudgetEventListener implements BudgetEventListener {

    private static final AttributeKey<String> ACTION = AttributeKey.stringKey("agent.budget.action");

    private final LongCounter enforced;

    public MetricBudgetEventListener(Meter meter) {
        this.enforced = meter.counterBuilder("agent.budget.enforced")
                .setDescription("Budget enforcements, by action (warn/degrade/block)").build();
    }

    @Override
    public void onEnforced(BudgetDecision decision, CallContext context) {
        enforced.add(1, Attributes.of(ACTION, decision.outcome().name().toLowerCase(Locale.ROOT)));
    }
}
