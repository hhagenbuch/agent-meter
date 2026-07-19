package io.github.hhagenbuch.meter.core.attr;

import io.opentelemetry.api.common.AttributeKey;

/**
 * The shared attribute vocabulary, so meter-core and meter-spring agree on keys. Standard
 * OpenTelemetry GenAI semantic-convention keys (Development stability, target semconv
 * v1.30.x) sit alongside the {@code agent.*} additions this project needs for cost and
 * FinOps attribution — namespaced so they never collide with standard keys.
 */
public final class MeterAttributes {

    private MeterAttributes() {
    }

    // --- standard OTel GenAI semconv ---
    public static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    public static final AttributeKey<String> GEN_AI_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    public static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    public static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    public static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    public static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");

    // --- agent-meter additions (cost + FinOps attribution units) ---
    public static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("agent.session_id");
    public static final AttributeKey<String> TOOL = AttributeKey.stringKey("agent.tool");
    public static final AttributeKey<String> PROMPT_VERSION = AttributeKey.stringKey("agent.prompt_version");
    public static final AttributeKey<String> FEATURE = AttributeKey.stringKey("agent.feature");
    public static final AttributeKey<Double> COST_USD = AttributeKey.doubleKey("agent.cost_usd");
    public static final AttributeKey<Boolean> COST_ESTIMATED = AttributeKey.booleanKey("agent.cost_estimated");
    public static final AttributeKey<Boolean> BUDGET_DEGRADED = AttributeKey.booleanKey("agent.budget_degraded");
    public static final AttributeKey<Boolean> INCOMPLETE = AttributeKey.booleanKey("agent.incomplete");
}
