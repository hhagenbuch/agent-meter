package io.github.hhagenbuch.meter.demo;

import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.spring.api.Attempt;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real provider client: returns plausible token counts (bigger for
 * bigger models) and occasionally reports a retry, so the dashboard shows retry cost.
 */
public final class FakeProvider implements LlmClient {

    @Override
    public LlmResponse call(LlmRequest request) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long input = 400 + rnd.nextLong(1600);
        long output = 100 + rnd.nextLong(900);

        List<Attempt> attempts = new ArrayList<>();
        if (rnd.nextInt(6) == 0) {
            // ~1 in 6 calls hits an overloaded retry — you pay for that attempt.
            attempts.add(new Attempt(TokenUsage.of(input, 0), 529, 40 + rnd.nextLong(80), false));
        }
        attempts.add(Attempt.ok(TokenUsage.of(input, output), 200 + rnd.nextLong(600)));
        return new LlmResponse(request.requestedModel(), attempts, false);
    }
}
