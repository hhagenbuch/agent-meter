package io.github.hhagenbuch.meter.starter;

import io.github.hhagenbuch.agent.llm.LlmClient;
import io.github.hhagenbuch.agent.llm.LlmResponse;
import io.github.hhagenbuch.agent.llm.TokenUsage;
import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.InMemoryBudgetStore;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Meter emits a cost-attributed OTel span for a fathom-backed session ... the LLM
 * turn in a session whose feature is the fathom context layer is metered exactly,
 * with session/feature attribution lifted from the reactor context. This is how a
 * fathom-backed agent's spend shows up in FinOps: same seam as any starter session,
 * attributed to the fathom feature.
 */
class FathomSessionMeteringTest {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spans)).build();
    private final Instruments instruments = new Instruments(SdkMeterProvider.builder()
            .registerMetricReader(InMemoryMetricReader.create()).build().get("t"));
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);
    private final CostEngine engine = new CostEngine(new PriceTable(LocalDate.of(2026, 7, 1), "t", List.of(
            new ModelPricing("m1", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 10.0, 30.0, null))))));

    // A fathom-backed agent turn: the model answered after consulting fathom; the
    // turn carries token usage that meter prices.
    private final LlmClient delegate = (messages, tools) ->
            Mono.just(new LlmResponse("verified answer", List.of(), null, "end_turn", new TokenUsage(1000, 500, 0, 0)));

    @Test
    void metersAFathomBackedSessionWithFeatureAttribution() {
        BudgetLedger ledger = new BudgetLedger(List.of(), new InMemoryBudgetStore(), clock);
        StarterMeteringLlmClient client = new StarterMeteringLlmClient(delegate, "m1",
                tracerProvider.get("t"), instruments, engine, ledger, BudgetEventListener.NO_OP,
                clock, "anthropic", "fathom-v1");

        client.chat(List.of(), List.of())
                .contextWrite(Context.of(MeterContext.SESSION_ID, "fathom-session-1",
                        MeterContext.FEATURE, "fathom-context"))
                .block();

        SpanData span = spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("chat m1")).findFirst().orElseThrow();
        assertThat(span.getAttributes().get(MeterAttributes.SESSION_ID)).isEqualTo("fathom-session-1");
        assertThat(span.getAttributes().get(MeterAttributes.FEATURE)).isEqualTo("fathom-context");
        assertThat(span.getAttributes().get(MeterAttributes.PROMPT_VERSION)).isEqualTo("fathom-v1");
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_PROVIDER_NAME)).isEqualTo("anthropic");
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_INPUT_TOKENS)).isEqualTo(1000L);
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_OUTPUT_TOKENS)).isEqualTo(500L);
        // 1000/1e6*10 + 500/1e6*30 = 0.025
        assertThat(span.getAttributes().get(MeterAttributes.COST_USD)).isCloseTo(0.025, within(1e-9));
    }
}
