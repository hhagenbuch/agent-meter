package io.github.hhagenbuch.meter.starter;

import io.github.hhagenbuch.agent.llm.LlmClient;
import io.github.hhagenbuch.agent.llm.LlmResponse;
import io.github.hhagenbuch.agent.llm.TokenUsage;
import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.github.hhagenbuch.meter.core.budget.Budget;
import io.github.hhagenbuch.meter.core.budget.BudgetAction;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.BudgetScope;
import io.github.hhagenbuch.meter.core.budget.CallContext;
import io.github.hhagenbuch.meter.core.budget.InMemoryBudgetStore;
import io.github.hhagenbuch.meter.core.budget.Window;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.budget.BudgetExceededException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StarterMeteringLlmClientTest {

    private final InMemorySpanExporter spans = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spans)).build();
    private final Instruments instruments = new Instruments(SdkMeterProvider.builder()
            .registerMetricReader(InMemoryMetricReader.create()).build().get("t"));
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC);
    private final CostEngine engine = new CostEngine(new PriceTable(LocalDate.of(2026, 7, 1), "t", List.of(
            new ModelPricing("m1", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 10.0, 30.0, null))))));

    // The starter's LlmClient, faked, returning a response that carries usage.
    private final LlmClient delegate = (messages, tools) ->
            Mono.just(new LlmResponse("hi", List.of(), null, "end_turn", new TokenUsage(1000, 500, 0, 0)));

    private StarterMeteringLlmClient client(BudgetLedger ledger) {
        return new StarterMeteringLlmClient(delegate, "m1", tracerProvider.get("t"), instruments,
                engine, ledger, BudgetEventListener.NO_OP, clock, "anthropic", "v7");
    }

    private BudgetLedger emptyLedger() {
        return new BudgetLedger(List.of(), new InMemoryBudgetStore(), clock);
    }

    @Test
    void metersAStarterCallAttributeExactIncludingUsageAndSessionFromContext() {
        client(emptyLedger())
                .chat(List.of(), List.of())
                .contextWrite(Context.of(MeterContext.SESSION_ID, "s1", MeterContext.FEATURE, "support-chat"))
                .block();

        SpanData span = spans.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("chat m1")).findFirst().orElseThrow();
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_PROVIDER_NAME)).isEqualTo("anthropic");
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_REQUEST_MODEL)).isEqualTo("m1");
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_RESPONSE_MODEL)).isEqualTo("m1");
        assertThat(span.getAttributes().get(MeterAttributes.SESSION_ID)).isEqualTo("s1");          // from context
        assertThat(span.getAttributes().get(MeterAttributes.FEATURE)).isEqualTo("support-chat");   // from context
        assertThat(span.getAttributes().get(MeterAttributes.PROMPT_VERSION)).isEqualTo("v7");      // from config
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_INPUT_TOKENS)).isEqualTo(1000L);
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_OUTPUT_TOKENS)).isEqualTo(500L);
        // 1000/1e6*10 + 500/1e6*30 = 0.025
        assertThat(span.getAttributes().get(MeterAttributes.COST_USD)).isCloseTo(0.025, within(1e-9));
    }

    @Test
    void withoutContextItStillMetersModelAndUsageJustNoSessionOrFeature() {
        client(emptyLedger()).chat(List.of(), List.of()).block();

        SpanData span = spans.getFinishedSpanItems().get(0);
        assertThat(span.getAttributes().get(MeterAttributes.GEN_AI_INPUT_TOKENS)).isEqualTo(1000L);
        assertThat(span.getAttributes().get(MeterAttributes.SESSION_ID)).isNull();
        assertThat(span.getAttributes().get(MeterAttributes.FEATURE)).isNull();
    }

    @Test
    void blockBudgetFailsFastAndNeverCallsTheStarter() {
        boolean[] delegateCalled = {false};
        LlmClient trackingDelegate = (messages, tools) -> {
            delegateCalled[0] = true;
            return Mono.just(new LlmResponse("hi", List.of(), null, "end_turn", TokenUsage.EMPTY));
        };
        Budget block = new Budget(BudgetScope.parse("feature:support-chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        InMemoryBudgetStore store = new InMemoryBudgetStore();
        BudgetLedger ledger = new BudgetLedger(List.of(block), store, clock);
        ledger.record(new CallContext("support-chat", "s1", "m1"), 6.0); // already over the limit

        StarterMeteringLlmClient client = new StarterMeteringLlmClient(trackingDelegate, "m1",
                tracerProvider.get("t"), instruments, engine, ledger, BudgetEventListener.NO_OP, clock, "anthropic", null);

        assertThatThrownBy(() -> client.chat(List.of(), List.of())
                .contextWrite(Context.of(MeterContext.SESSION_ID, "s1", MeterContext.FEATURE, "support-chat"))
                .block())
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("feature:support-chat");
        assertThat(delegateCalled[0]).isFalse(); // fail fast — the model was never called
    }
}
