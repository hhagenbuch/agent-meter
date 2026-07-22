package io.github.hhagenbuch.meter.core.attr;

import io.opentelemetry.api.common.AttributeKey;

/**
 * The shared attribute vocabulary, so meter-core and meter-spring agree on keys. Standard
 * OpenTelemetry GenAI semantic-convention keys sit alongside the {@code agent.*} additions
 * this project needs for cost and FinOps attribution — namespaced so they never collide
 * with standard keys.
 *
 * <p>The {@code gen_ai.*} keys below track the conventions in
 * {@code open-telemetry/semantic-conventions-genai} audited at commit
 * {@code c26a2c21d1ee70d5231bd440c7b48d3c94ee506a} (2026-07-17), Development stability.
 * See the "Standards alignment" table in {@code README.md} / {@code docs/DESIGN.md}.
 */
public final class MeterAttributes {

    private MeterAttributes() {
    }

    // --- standard OTel GenAI semconv (audited at the SHA above) ---
    /** Provider discriminator, e.g. {@code anthropic}, {@code openai} (renamed from the
     *  deprecated {@code gen_ai.system} per the current convention). */
    public static final AttributeKey<String> GEN_AI_PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    public static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    public static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    public static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    /** Required attribute on the {@code gen_ai.client.token.usage} metric: {@code input} / {@code output}. */
    public static final AttributeKey<String> GEN_AI_TOKEN_TYPE = AttributeKey.stringKey("gen_ai.token.type");

    // --- agent-meter additions (cost + FinOps attribution units) ---
    public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("agent.session_id");
    public static final AttributeKey<String> TOOL = AttributeKey.stringKey("agent.tool");
    public static final AttributeKey<String> PROMPT_VERSION = AttributeKey.stringKey("agent.prompt_version");
    public static final AttributeKey<String> FEATURE = AttributeKey.stringKey("agent.feature");
    public static final AttributeKey<Double> COST_USD = AttributeKey.doubleKey("agent.cost_usd");
    public static final AttributeKey<Boolean> COST_ESTIMATED = AttributeKey.booleanKey("agent.cost_estimated");
    public static final AttributeKey<Boolean> BUDGET_DEGRADED = AttributeKey.booleanKey("agent.budget_degraded");
    public static final AttributeKey<Boolean> INCOMPLETE = AttributeKey.booleanKey("agent.incomplete");

    // --- behavioral-SLI attribution (agent-slo RFC) ---
    /** {@code pass} / {@code fail} on the {@code agent.sli.eval_cases} counter. */
    public static final AttributeKey<String> SLI_RESULT = AttributeKey.stringKey("agent.sli.result");
    /** Dataset name a continuous-eval run executed. */
    public static final AttributeKey<String> SLI_DATASET = AttributeKey.stringKey("agent.sli.dataset");
}
