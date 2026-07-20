package io.github.hhagenbuch.meter.spring.otel;

import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.CostResult;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.spring.api.Attempt;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Wraps an {@link LlmClient} to emit OpenTelemetry telemetry per the attribution table
 * (DESIGN.md section 4): a parent turn span with the {@code gen_ai.*}/{@code agent.*}
 * attributes, a child span per HTTP attempt, and the {@code gen_ai.client.token.usage} /
 * {@code gen_ai.client.operation.duration} (ratified) plus {@code agent.cost_usd} metrics.
 * Cost sums every attempt.
 *
 * <p>Metrics are recorded inside the parent span's scope, so backends attach exemplars —
 * a spike in a dashboard is one click from the trace that caused it.
 */
public final class MeteringLlmClient implements LlmClient {

    private static final AttributeKey<Long> HTTP_STATUS = AttributeKey.longKey("http.response.status_code");

    private final LlmClient delegate;
    private final Tracer tracer;
    private final Instruments instruments;
    private final CostEngine costEngine;
    private final Clock clock;
    private final String genAiSystem;

    public MeteringLlmClient(LlmClient delegate, Tracer tracer, Instruments instruments,
                             CostEngine costEngine, Clock clock, String genAiSystem) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.instruments = instruments;
        this.costEngine = costEngine;
        this.clock = clock;
        this.genAiSystem = genAiSystem;
    }

    @Override
    public LlmResponse call(LlmRequest request) {
        Span span = tracer.spanBuilder("chat " + request.requestedModel())
                .setSpanKind(SpanKind.CLIENT).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.setAttribute(MeterAttributes.GEN_AI_PROVIDER_NAME, genAiSystem);
            span.setAttribute(MeterAttributes.GEN_AI_OPERATION_NAME, "chat");
            span.setAttribute(MeterAttributes.GEN_AI_REQUEST_MODEL, request.requestedModel());
            setIfPresent(span, MeterAttributes.FEATURE, request.feature());
            setIfPresent(span, MeterAttributes.SESSION_ID, request.sessionId());
            setIfPresent(span, MeterAttributes.TOOL, request.tool());
            setIfPresent(span, MeterAttributes.PROMPT_VERSION, request.promptVersion());
            if (request.budgetDegraded()) {
                span.setAttribute(MeterAttributes.BUDGET_DEGRADED, true);
            }

            LlmResponse response = delegate.call(request);

            span.setAttribute(MeterAttributes.GEN_AI_RESPONSE_MODEL, response.responseModel());
            recordAttemptSpans(response);
            record(span, request, response);
            return response;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }

    private void record(Span span, LlmRequest request, LlmResponse response) {
        TokenUsage total = response.totalUsage();
        span.setAttribute(MeterAttributes.GEN_AI_INPUT_TOKENS, total.inputTokens());
        span.setAttribute(MeterAttributes.GEN_AI_OUTPUT_TOKENS, total.outputTokens());

        Attributes dims = dimensions(request, response);
        instruments.addTokens(total.inputTokens(), "input", dims);
        instruments.addTokens(total.outputTokens(), "output", dims);

        CostResult cost = costEngine.cost(response.responseModel(), total, today());
        if (cost.known()) {
            span.setAttribute(MeterAttributes.COST_USD, cost.usd());
            instruments.addCost(cost.usd(), dims);
            if (cost.estimated()) {
                span.setAttribute(MeterAttributes.COST_ESTIMATED, true);
            }
        } else {
            // Unknown, not zero — a missing cost attribute plus a counter, never a lying 0.00.
            instruments.addUnknownModel(dims);
        }
        if (response.incomplete()) {
            span.setAttribute(MeterAttributes.INCOMPLETE, true);
        }
        instruments.recordDurationSeconds(totalDurationSeconds(response), dims);
    }

    private void recordAttemptSpans(LlmResponse response) {
        for (Attempt attempt : response.attempts()) {
            Span child = tracer.spanBuilder("llm.attempt").setSpanKind(SpanKind.CLIENT).startSpan();
            child.setAttribute(MeterAttributes.GEN_AI_INPUT_TOKENS, attempt.usage().inputTokens());
            child.setAttribute(MeterAttributes.GEN_AI_OUTPUT_TOKENS, attempt.usage().outputTokens());
            child.setAttribute(HTTP_STATUS, (long) attempt.httpStatus());
            if (!attempt.succeeded()) {
                child.setStatus(StatusCode.ERROR);
            }
            child.end();
        }
    }

    private Attributes dimensions(LlmRequest request, LlmResponse response) {
        AttributesBuilder b = Attributes.builder()
                .put(MeterAttributes.GEN_AI_RESPONSE_MODEL, response.responseModel());
        if (request.feature() != null) {
            b.put(MeterAttributes.FEATURE, request.feature());
        }
        if (request.promptVersion() != null) {
            b.put(MeterAttributes.PROMPT_VERSION, request.promptVersion());
        }
        return b.build();
    }

    private static double totalDurationSeconds(LlmResponse response) {
        return response.attempts().stream().mapToLong(Attempt::durationMillis).sum() / 1000.0;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void setIfPresent(Span span, AttributeKey<String> key, String value) {
        if (value != null && !value.isBlank()) {
            span.setAttribute(key, value);
        }
    }
}
