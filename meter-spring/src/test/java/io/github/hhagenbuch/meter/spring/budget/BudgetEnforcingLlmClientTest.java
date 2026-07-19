package io.github.hhagenbuch.meter.spring.budget;

import io.github.hhagenbuch.meter.core.budget.Budget;
import io.github.hhagenbuch.meter.core.budget.BudgetAction;
import io.github.hhagenbuch.meter.core.budget.BudgetDecision;
import io.github.hhagenbuch.meter.core.budget.BudgetLedger;
import io.github.hhagenbuch.meter.core.budget.BudgetScope;
import io.github.hhagenbuch.meter.core.budget.BudgetStore;
import io.github.hhagenbuch.meter.core.budget.CallContext;
import io.github.hhagenbuch.meter.core.budget.InMemoryBudgetStore;
import io.github.hhagenbuch.meter.core.budget.Window;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import io.github.hhagenbuch.meter.core.cost.TokenUsage;
import io.github.hhagenbuch.meter.core.pricing.ModelPricing;
import io.github.hhagenbuch.meter.core.pricing.PriceTable;
import io.github.hhagenbuch.meter.core.pricing.RatePeriod;
import io.github.hhagenbuch.meter.spring.api.LlmClient;
import io.github.hhagenbuch.meter.spring.api.LlmRequest;
import io.github.hhagenbuch.meter.spring.api.LlmResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetEnforcingLlmClientTest {

    private static final Instant DAY1 = Instant.parse("2026-07-19T10:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-07-20T00:00:00Z");

    private final CostEngine engine = new CostEngine(new PriceTable(LocalDate.of(2026, 7, 1), "t", List.of(
            new ModelPricing("opus", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 15.0, 75.0, null))),
            new ModelPricing("haiku", List.of(new RatePeriod(LocalDate.of(2026, 1, 1), 0.8, 4.0, null))))));

    private final BudgetStore store = new InMemoryBudgetStore();
    private final AtomicReference<LlmRequest> delegateSaw = new AtomicReference<>();
    private final List<BudgetDecision> events = new ArrayList<>();

    private final LlmClient delegate = request -> {
        delegateSaw.set(request);
        return LlmResponse.single(request.requestedModel(), TokenUsage.of(100, 50));
    };

    private final CallContext ctx = new CallContext("chat", "s1", "opus");
    private final LlmRequest request = new LlmRequest("opus", "chat", "s1", null, null);

    private BudgetEnforcingLlmClient client(Budget budget, Instant now) {
        BudgetLedger ledger = new BudgetLedger(List.of(budget), store, Clock.fixed(now, ZoneOffset.UTC));
        return new BudgetEnforcingLlmClient(delegate, ledger, engine, Clock.fixed(now, ZoneOffset.UTC),
                (decision, context) -> events.add(decision));
    }

    private void preSpend(Budget budget, double usd) {
        new BudgetLedger(List.of(budget), store, Clock.fixed(DAY1, ZoneOffset.UTC)).record(ctx, usd);
    }

    @Test
    void underBudgetCallsTheDelegateUnchanged() {
        Budget block = new Budget(BudgetScope.parse("feature:chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        client(block, DAY1).call(request);

        assertThat(delegateSaw.get().requestedModel()).isEqualTo("opus");
        assertThat(delegateSaw.get().budgetDegraded()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void blockFailsFastWithAMessageAndNeverCallsTheDelegate() {
        Budget block = new Budget(BudgetScope.parse("feature:chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        preSpend(block, 6.0);

        assertThatThrownBy(() -> client(block, DAY1).call(request))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("feature:chat")
                .hasMessageContaining("blocked");

        assertThat(delegateSaw.get()).isNull();                 // delegate never invoked
        assertThat(events).extracting(BudgetDecision::outcome).containsExactly(BudgetDecision.Outcome.BLOCK);
    }

    @Test
    void degradeSwapsToTheCheaperModelAndFlagsTheRequest() {
        Budget degrade = new Budget(BudgetScope.parse("feature:chat"), 5.0, Window.DAILY, BudgetAction.DEGRADE, "haiku");
        preSpend(degrade, 6.0);

        client(degrade, DAY1).call(request);

        assertThat(delegateSaw.get().requestedModel()).isEqualTo("haiku"); // model actually swapped
        assertThat(delegateSaw.get().budgetDegraded()).isTrue();
        assertThat(events).extracting(BudgetDecision::outcome).containsExactly(BudgetDecision.Outcome.DEGRADE);
    }

    @Test
    void warnProceedsAtTheNormalModelButNotifies() {
        Budget warn = new Budget(BudgetScope.parse("feature:chat"), 5.0, Window.DAILY, BudgetAction.WARN, null);
        preSpend(warn, 6.0);

        client(warn, DAY1).call(request);

        assertThat(delegateSaw.get().requestedModel()).isEqualTo("opus"); // unchanged
        assertThat(events).extracting(BudgetDecision::outcome).containsExactly(BudgetDecision.Outcome.WARN);
    }

    @Test
    void degradedScopeRecoversAtWindowReset() {
        Budget degrade = new Budget(BudgetScope.parse("feature:chat"), 5.0, Window.DAILY, BudgetAction.DEGRADE, "haiku");
        preSpend(degrade, 6.0);

        client(degrade, DAY1).call(request);
        assertThat(delegateSaw.get().requestedModel()).isEqualTo("haiku"); // day 1: degraded

        client(degrade, DAY2).call(request);
        assertThat(delegateSaw.get().requestedModel()).isEqualTo("opus");  // next window: recovered
    }
}
