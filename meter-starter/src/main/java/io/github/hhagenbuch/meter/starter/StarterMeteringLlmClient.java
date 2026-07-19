package io.github.hhagenbuch.meter.starter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hhagenbuch.agent.llm.LlmClient;
import io.github.hhagenbuch.agent.llm.LlmResponse;
import io.github.hhagenbuch.agent.tools.AgentTool;
import io.github.hhagenbuch.meter.core.attr.MeterAttributes;
import io.github.hhagenbuch.meter.core.budget.BudgetDecision;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.CallContext;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.CostResult;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.spring.budget.BudgetEventListener;
import io.github.hhagenbuch.meter.spring.budget.BudgetExceededException;
import io.github.hhagenbuch.meter.spring.otel.Instruments;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

/**
 * Decorates the starter's {@link LlmClient} with agent-meter telemetry and budget
 * enforcement — no changes to the starter itself. It's reactive, matching the starter's
 * WebFlux seam.
 *
 * <p><b>What it can and can't attribute at this seam.</b> {@code chat(messages, tools)}
 * carries no model / session / feature, so:
 * <ul>
 *   <li><b>model</b> comes from the starter's {@code agent.model} config;</li>
 *   <li><b>usage/cost</b> from the starter's {@link LlmResponse#usage()} (unblocked by the
 *       starter now exposing it);</li>
 *   <li><b>session/feature</b> from the Reactor context (see {@link MeterContextWebFilter});</li>
 *   <li><b>prompt_version</b> from config/env.</li>
 * </ul>
 *
 * <p><b>Retries are aggregate</b> — the starter retries inside {@code AnthropicClient}, so
 * per-attempt cost isn't observable here (roadmap: a {@code WebClient} filter). <b>Budget
 * degrade is not possible</b> — there's no model override on {@code chat()} — so only
 * {@code warn} and {@code block} enforce at this seam (DESIGN §7).
 */
public class StarterMeteringLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final String model;
    private final Tracer tracer;
    private final Instruments instruments;
    private final CostEngine costEngine;
    private final BudgetLedger ledger;
    private final BudgetEventListener budgetListener;
    private final Clock clock;
    private final String genAiSystem;
    private final String promptVersion;

    public StarterMeteringLlmClient(LlmClient delegate, String model, Tracer tracer, Instruments instruments,
                                    CostEngine costEngine, BudgetLedger ledger, BudgetEventListener budgetListener,
                                    Clock clock, String genAiSystem, String promptVersion) {
        this.delegate = delegate;
        this.model = model;
        this.tracer = tracer;
        this.instruments = instruments;
        this.costEngine = costEngine;
        this.ledger = ledger;
        this.budgetListener = budgetListener;
        this.clock = clock;
        this.genAiSystem = genAiSystem;
        this.promptVersion = promptVersion;
    }

    @Override
    public Mono<LlmResponse> chat(List<ObjectNode> messages, Collection<AgentTool> tools) {
        return Mono.deferContextual(ctx -> {
            String sessionId = ctx.hasKey(MeterContext.SESSION_ID) ? ctx.get(MeterContext.SESSION_ID) : null;
            String feature = ctx.hasKey(MeterContext.FEATURE) ? ctx.get(MeterContext.FEATURE) : null;
            CallContext budgetCtx = new CallContext(feature, sessionId, model);

            BudgetDecision decision = ledger.check(budgetCtx);
            if (decision.outcome() != BudgetDecision.Outcome.ALLOW) {
                budgetListener.onEnforced(decision, budgetCtx);
            }
            if (decision.blocks()) {
                return Mono.error(new BudgetExceededException(decision.message()));
            }
            // (degrade cannot swap the model here — see class doc / DESIGN §7)

            Span span = tracer.spanBuilder("chat " + model).setSpanKind(SpanKind.CLIENT).startSpan();
            span.setAttribute(MeterAttributes.GEN_AI_SYSTEM, genAiSystem);
            span.setAttribute(MeterAttributes.GEN_AI_OPERATION_NAME, "chat");
            span.setAttribute(MeterAttributes.GEN_AI_REQUEST_MODEL, model);
            setIfPresent(span, MeterAttributes.SESSION_ID, sessionId);
            setIfPresent(span, MeterAttributes.FEATURE, feature);
            setIfPresent(span, MeterAttributes.PROMPT_VERSION, promptVersion);

            long startNanos = System.nanoTime();
            return delegate.chat(messages, tools)
                    .doOnNext(response -> record(span, budgetCtx, feature, response, startNanos))
                    .doOnError(error -> {
                        span.recordException(error);
                        span.setStatus(StatusCode.ERROR);
                    })
                    .doFinally(signal -> span.end());
        });
    }

    /** Streaming passes straight through (real SSE); streamed-usage metering is roadmap. */
    @Override
    public Flux<String> chatStream(List<ObjectNode> messages, Collection<AgentTool> tools) {
        return delegate.chatStream(messages, tools);
    }

    private void record(Span span, CallContext budgetCtx, String feature, LlmResponse response, long startNanos) {
        TokenUsage usage = toMeterUsage(response.usage());
        span.setAttribute(MeterAttributes.GEN_AI_RESPONSE_MODEL, model);
        span.setAttribute(MeterAttributes.GEN_AI_INPUT_TOKENS, usage.inputTokens());
        span.setAttribute(MeterAttributes.GEN_AI_OUTPUT_TOKENS, usage.outputTokens());

        Attributes dims = dimensions(feature);
        instruments.addTokens(usage.inputTokens(), "input", dims);
        instruments.addTokens(usage.outputTokens(), "output", dims);

        CostResult cost = costEngine.cost(model, usage, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (cost.known()) {
            span.setAttribute(MeterAttributes.COST_USD, cost.usd());
            instruments.addCost(cost.usd(), dims);
            if (cost.estimated()) {
                span.setAttribute(MeterAttributes.COST_ESTIMATED, true);
            }
            ledger.record(budgetCtx, cost.usd());
        } else {
            instruments.addUnknownModel(dims);
        }
        instruments.recordDuration((System.nanoTime() - startNanos) / 1_000_000.0, dims);
    }

    /** Map the starter's usage to the cost engine's: cache-read bills at the cached rate;
     *  cache-creation is folded into input (close enough for cost, documented). */
    private static TokenUsage toMeterUsage(io.github.hhagenbuch.agent.llm.TokenUsage u) {
        return new TokenUsage(u.inputTokens() + u.cacheCreationInputTokens(), u.outputTokens(),
                u.cacheReadInputTokens());
    }

    private Attributes dimensions(String feature) {
        AttributesBuilder b = Attributes.builder().put(MeterAttributes.GEN_AI_RESPONSE_MODEL, model);
        if (feature != null) {
            b.put(MeterAttributes.FEATURE, feature);
        }
        if (promptVersion != null) {
            b.put(MeterAttributes.PROMPT_VERSION, promptVersion);
        }
        return b.build();
    }

    private static void setIfPresent(Span span, io.opentelemetry.api.common.AttributeKey<String> key, String value) {
        if (value != null && !value.isBlank()) {
            span.setAttribute(key, value);
        }
    }
}
