package io.github.hhagenbuch.meter.spring.api;

/**
 * The seam agent-meter instruments — the same decorator seam the starter's {@code LlmClient}
 * exposes. A real implementation calls the provider and reports what it did (including
 * retries) as an {@link LlmResponse}; {@code MeteringLlmClient} wraps it to emit telemetry.
 */
@FunctionalInterface
public interface LlmClient {

    LlmResponse call(LlmRequest request);
}
