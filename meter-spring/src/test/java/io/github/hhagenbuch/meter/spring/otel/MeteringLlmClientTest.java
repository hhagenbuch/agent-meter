package io.github.hhagenbuch.meter.spring.otel;

import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;
import io.github.hhagenbuch.meter.spring.api.Attempt;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MeteringLlmClientTest {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spans)).build();
    private final InMemoryMetricReader metrics = InMemoryMetricReader.create();
    private final SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(metrics).build();

    private final Instruments instruments = new Instruments(meterProvider.get("test"));

    // model m1: $10/MTok input, $30/MTok output, $1/MTok cached. Table compiled 2026-07-01.
    private final PriceTable table = new PriceTable(LocalDate.of(2026, 7, 1), "t", List.of(
            new ModelPricing("m1", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 10.0, 30.0, 1.0)))));

    private MeteringLlmClient client(LlmResponse canned) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC);
        return new MeteringLlmClient(request -> canned, tracerProvider.get("test"),
                instruments, new CostEngine(table), clock, "anthropic");
    }

    @Test
    void successfulCallSetsExactAttributesAndMetrics() {
        MeteringLlmClient client = client(LlmResponse.single("m1", TokenUsage.of(1000, 500)));

        client.call(new LlmRequest("m1", "support-chat", "s1", null, "v7"));

        SpanData parent = span("chat m1");
        assertThat(parent.getAttributes().get(MeterAttributes.GEN_AI_PROVIDER_NAME)).isEqualTo("anthropic");
        assertThat(parent.getAttributes().get(MeterAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("m1");
        assertThat(parent.getAttributes().get(MeterAttributes.GEN_AI_RESPONSE_MODEL)).isEqualTo("m1");
        assertThat(parent.getAttributes().get(MeterAttributes.FEATURE)).isEqualTo("support-chat");
        assertThat(parent.getAttributes().get(MeterAttributes.SESSION_ID)).isEqualTo("s1");
        assertThat(parent.getAttributes().get(MeterAttributes.PROMPT_VERSION)).isEqualTo("v7");
        assertThat(parent.getAttributes().get(MeterAttributes.GEN_AI_INPUT_TOKENS)).isEqualTo(1000L);
        assertThat(parent.getAttributes().get(MeterAttributes.GEN_AI_OUTPUT_TOKENS)).isEqualTo(500L);
        // 1000/1e6*10 + 500/1e6*30 = 0.025
        assertThat(parent.getAttributes().get(MeterAttributes.COST_USD)).isCloseTo(0.025, within(1e-9));

        assertThat(costMetric()).isCloseTo(0.025, within(1e-9));
        assertThat(tokenMetric("input")).isEqualTo(1000L);
        assertThat(tokenMetric("output")).isEqualTo(500L);
    }

    @Test
    void unknownModelSetsNoCostAttributeAndCountsIt() {
        MeteringLlmClient client = client(LlmResponse.single("mystery-model", TokenUsage.of(1000, 500)));

        client.call(LlmRequest.of("mystery-model"));

        SpanData parent = span("chat mystery-model");
        assertThat(parent.getAttributes().get(MeterAttributes.COST_USD)).isNull(); // never a lying 0.00
        assertThat(metricSum("agent.cost.unknown_model")).isEqualTo(1L);
        assertThat(costMetric()).isEqualTo(0.0); // cost counter untouched
    }

    @Test
    void staleTableFlagsCostEstimatedOnTheSpan() {
        // table compiled 2026-01-01, call on 2026-07-19 -> > 90 days stale
        CostEngine staleEngine = new CostEngine(new PriceTable(LocalDate.of(2026, 1, 1), "t", table.models()));
        MeteringLlmClient client = new MeteringLlmClient(
                r -> LlmResponse.single("m1", TokenUsage.of(1000, 0)),
                tracerProvider.get("test"), instruments, staleEngine,
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC), "anthropic");

        client.call(LlmRequest.of("m1"));

        SpanData parent = span("chat m1");
        assertThat(parent.getAttributes().get(MeterAttributes.COST_USD)).isNotNull();
        assertThat(parent.getAttributes().get(MeterAttributes.COST_ESTIMATED)).isTrue();
    }

    @Test
    void retriesProduceChildSpansAndCostCountsEveryAttempt() {
        LlmResponse withRetries = new LlmResponse("m1", List.of(
                new Attempt(TokenUsage.of(100, 0), 529, 50, false),   // overloaded
                new Attempt(TokenUsage.of(100, 0), 529, 60, false),   // overloaded
                Attempt.ok(TokenUsage.of(1000, 500), 200)), false);
        MeteringLlmClient client = client(withRetries);

        client.call(LlmRequest.of("m1"));

        assertThat(attemptSpanCount()).isEqualTo(3);
        SpanData parent = span("chat m1");
        assertThat(parent.getAttributes().get(MeterAttributes.GEN_AI_INPUT_TOKENS)).isEqualTo(1200L);
        // (1200/1e6*10) + (500/1e6*30) = 0.012 + 0.015 = 0.027 — retries are paid for
        assertThat(parent.getAttributes().get(MeterAttributes.COST_USD)).isCloseTo(0.027, within(1e-9));
        assertThat(tokenMetric("input")).isEqualTo(1200L);
    }

    @Test
    void incompleteStreamIsFlagged() {
        MeteringLlmClient client = client(new LlmResponse("m1",
                List.of(Attempt.ok(TokenUsage.of(500, 100), 0)), true));

        client.call(LlmRequest.of("m1"));

        assertThat(span("chat m1").getAttributes().get(MeterAttributes.INCOMPLETE)).isTrue();
    }

    // --- helpers ---

    private SpanData span(String name) {
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private long attemptSpanCount() {
        return spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("llm.attempt")).count();
    }

    private double costMetric() {
        return metrics.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("agent.cost_usd"))
                .flatMap(m -> m.getDoubleSumData().getPoints().stream())
                .mapToDouble(DoublePointData::getValue).sum();
    }

    private long tokenMetric(String tokenType) {
        // gen_ai.client.token.usage is a histogram; its per-type sum is the token total.
        AttributeKey<String> type = MeterAttributes.GEN_AI_TOKEN_TYPE;
        return (long) metrics.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("gen_ai.client.token.usage"))
                .flatMap(m -> m.getHistogramData().getPoints().stream())
                .filter(p -> tokenType.equals(p.getAttributes().get(type)))
                .mapToDouble(io.opentelemetry.sdk.metrics.data.HistogramPointData::getSum).sum();
    }

    private long metricSum(String name) {
        return metrics.collectAllMetrics().stream()
                .filter(m -> m.getName().equals(name))
                .flatMap(m -> m.getLongSumData().getPoints().stream())
                .mapToLong(LongPointData::getValue).sum();
    }
}
