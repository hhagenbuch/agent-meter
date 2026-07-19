package io.github.hhagenbuch.meter.core.budget;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetLedgerTest {

    private static final Instant DAY1 = Instant.parse("2026-07-19T10:00:00Z");
    private static final Instant DAY2 = Instant.parse("2026-07-20T00:00:00Z");

    private final BudgetStore store = new InMemoryBudgetStore();
    private final CallContext ctx = new CallContext("support-chat", "s1", "m");

    private Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    @Test
    void accumulatesSpendAndBlocksOverTheLimitThenRecoversNextWindow() {
        Budget b = new Budget(BudgetScope.parse("feature:support-chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        BudgetLedger day1 = new BudgetLedger(List.of(b), store, at(DAY1));

        assertThat(day1.check(ctx).outcome()).isEqualTo(BudgetDecision.Outcome.ALLOW);
        day1.record(ctx, 3.0);
        assertThat(day1.check(ctx).outcome()).isEqualTo(BudgetDecision.Outcome.ALLOW);
        day1.record(ctx, 3.0); // total 6 > 5
        assertThat(day1.check(ctx).blocks()).isTrue();

        // New calendar day = new window bucket = zero spend = recovered. Same store.
        BudgetLedger day2 = new BudgetLedger(List.of(b), store, at(DAY2));
        assertThat(day2.check(ctx).outcome()).isEqualTo(BudgetDecision.Outcome.ALLOW);
    }

    @Test
    void degradeThenRecoverAtWindowReset() {
        Budget b = new Budget(BudgetScope.parse("global"), 5.0, Window.DAILY, BudgetAction.DEGRADE, "claude-haiku-4-5");
        BudgetLedger day1 = new BudgetLedger(List.of(b), store, at(DAY1));

        day1.record(ctx, 6.0);
        BudgetDecision d = day1.check(ctx);
        assertThat(d.degrades()).isTrue();
        assertThat(d.degradeToModel()).isEqualTo("claude-haiku-4-5");

        assertThat(new BudgetLedger(List.of(b), store, at(DAY2)).check(ctx).outcome())
                .isEqualTo(BudgetDecision.Outcome.ALLOW);
    }

    @Test
    void mostRestrictiveApplicableBudgetWins() {
        Budget warn = new Budget(BudgetScope.parse("global"), 1.0, Window.DAILY, BudgetAction.WARN, null);
        Budget block = new Budget(BudgetScope.parse("feature:support-chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        BudgetLedger ledger = new BudgetLedger(List.of(warn, block), store, at(DAY1));

        ledger.record(ctx, 6.0); // over both limits
        assertThat(ledger.check(ctx).blocks()).isTrue(); // BLOCK beats WARN
    }

    @Test
    void wildcardSessionBudgetIsIsolatedPerSession() {
        Budget perSession = new Budget(BudgetScope.parse("session:*"), 2.0, Window.DAILY, BudgetAction.BLOCK, null);
        BudgetLedger ledger = new BudgetLedger(List.of(perSession), store, at(DAY1));
        CallContext s1 = new CallContext("f", "s1", "m");
        CallContext s2 = new CallContext("f", "s2", "m");

        ledger.record(s1, 2.5); // s1 over its own $2 cap
        assertThat(ledger.check(s1).blocks()).isTrue();
        assertThat(ledger.check(s2).outcome()).isEqualTo(BudgetDecision.Outcome.ALLOW); // s2 untouched
    }
}
