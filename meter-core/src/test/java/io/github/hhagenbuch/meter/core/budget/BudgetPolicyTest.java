package io.github.hhagenbuch.meter.core.budget;

import io.github.hhagenbuch.meter.core.budget.BudgetDecision.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetPolicyTest {

    private final CallContext ctx = new CallContext("support-chat", "s1", "m");

    @Test
    void underLimitAllows() {
        Budget b = new Budget(BudgetScope.parse("feature:support-chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        assertThat(BudgetPolicy.decide(b, 4.999, ctx).outcome()).isEqualTo(Outcome.ALLOW);
    }

    @Test
    void atOrOverLimitTakesTheBreachAction() {
        Budget block = new Budget(BudgetScope.parse("feature:support-chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        assertThat(BudgetPolicy.decide(block, 5.0, ctx).outcome()).isEqualTo(Outcome.BLOCK);

        Budget warn = new Budget(BudgetScope.parse("global"), 5.0, Window.DAILY, BudgetAction.WARN, null);
        assertThat(BudgetPolicy.decide(warn, 9.0, ctx).outcome()).isEqualTo(Outcome.WARN);

        Budget degrade = new Budget(BudgetScope.parse("global"), 5.0, Window.DAILY, BudgetAction.DEGRADE, "claude-haiku-4-5");
        BudgetDecision d = BudgetPolicy.decide(degrade, 6.0, ctx);
        assertThat(d.outcome()).isEqualTo(Outcome.DEGRADE);
        assertThat(d.degradeToModel()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    void blockMessageIsActionable() {
        Budget block = new Budget(BudgetScope.parse("feature:support-chat"), 5.0, Window.DAILY, BudgetAction.BLOCK, null);
        String message = BudgetPolicy.decide(block, 6.25, ctx).message();
        assertThat(message)
                .contains("feature:support-chat")
                .contains("daily")
                .contains("$6.25")
                .contains("$5.00")
                .contains("blocked");
    }

    @Test
    void degradeBudgetRequiresADegradeToModel() {
        assertThatThrownBy(() -> new Budget(BudgetScope.parse("global"), 5.0, Window.DAILY, BudgetAction.DEGRADE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
