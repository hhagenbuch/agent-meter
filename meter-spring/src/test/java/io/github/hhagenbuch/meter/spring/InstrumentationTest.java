package io.github.hhagenbuch.meter.spring;

import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.github.hhagenbuch.meter.core.budget.Budget;
import io.github.hhagenbuch.meter.core.budget.BudgetAction;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.BudgetScope;
import io.github.hhagenbuch.meter.core.budget.CallContext;
import io.github.hhagenbuch.meter.core.budget.InMemoryBudgetStore;
import io.github.hhagenbuch.meter.core.budget.Window;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.github.hhagenbuch.meter.spring.otel.MeteringFactory;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentationTest {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spans)).build();
    private final SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(InMemoryMetricReader.create()).build();

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC);
    private final CostEngine engine = new CostEngine(new PriceTable(LocalDate.of(2026, 7, 1), "t", List.of(
            new ModelPricing("opus", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 15.0, 75.0, null))),
            new ModelPricing("haiku", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 0.8, 4.0, null))))));

    @Test
    void budgetOutsideMeterSoTheSpanRecordsTheModelThatActuallyRan() {
        // A degrade budget already over its limit this window.
        Budget degrade = new Budget(BudgetScope.parse("feature:chat"), 5.0, Window.DAILY, BudgetAction.DEGRADE, "haiku");
        InMemoryBudgetStore store = new InMemoryBudgetStore();
        BudgetLedger ledger = new BudgetLedger(List.of(degrade), store, clock);
        ledger.record(new CallContext("chat", "s1", "opus"), 6.0);

        MeteringFactory metering = new MeteringFactory(tracerProvider.get("t"),
                new Instruments(meterProvider.get("t")), engine, clock, "anthropic");
        Instrumentation instrumentation = new Instrumentation(metering, ledger, engine, clock, BudgetEventListener.NO_OP);

        AtomicReference<String> providerSaw = new AtomicReference<>();
        LlmClient provider = request -> {
            providerSaw.set(request.requestedModel());
            return LlmResponse.single(request.requestedModel(), TokenUsage.of(100, 50));
        };

        // Caller asks for opus; the budget must degrade it to haiku before the meter records.
        instrumentation.instrument(provider).call(new LlmRequest("opus", "chat", "s1", null, null));

        assertThat(providerSaw.get()).isEqualTo("haiku"); // enforcement ran before the provider

        SpanData span = spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("chat haiku")).findFirst().orElseThrow();
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("haiku");
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_RESPONSE_MODEL)).isEqualTo("haiku");
        assertThat(span.getAttributes().get(MeterAttributes.BUDGET_DEGRADED)).isTrue();
    }
}
