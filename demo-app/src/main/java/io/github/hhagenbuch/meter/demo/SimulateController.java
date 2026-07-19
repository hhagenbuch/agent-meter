package io.github.hhagenbuch.meter.demo;

import io.github.hhagenbuch.meter.spring.Instrumentation;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import io.github.hhagenbuch.meter.spring.budget.BudgetExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /simulate?feature=support-chat&model=claude-sonnet-5} runs one metered,
 * budget-enforced call through agent-meter. The load script hits this with a mix of
 * features and models so the dashboard comes alive — and so the small support-chat
 * budget trips, showing degrade (and then block) in the traces and metrics.
 */
@RestController
public class SimulateController {

    private final LlmClient metered;

    public SimulateController(Instrumentation instrumentation) {
        // Wrap the fake provider once: metering + budget enforcement, in the right order.
        this.metered = instrumentation.instrument(new FakeProvider());
    }

    @PostMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulate(
            @RequestParam String feature,
            @RequestParam(defaultValue = "claude-sonnet-5") String model) {
        LlmRequest request = new LlmRequest(model, feature, "sess-" + UUID.randomUUID(), null, "v1");
        try {
            LlmResponse response = metered.call(request);
            return ResponseEntity.ok(Map.of(
                    "feature", feature,
                    "model", response.responseModel(),
                    "attempts", response.attempts().size()));
        } catch (BudgetExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("feature", feature, "blocked", e.getMessage()));
        }
    }
}
