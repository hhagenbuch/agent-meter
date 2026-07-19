package io.github.hhagenbuch.meter.core.budget;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetScopeTest {

    @Test
    void specificFeatureMatchesOnlyThatFeature() {
        BudgetScope scope = BudgetScope.parse("feature:support-chat");
        assertThat(scope.matches(new CallContext("support-chat", "s1", "m"))).isTrue();
        assertThat(scope.matches(new CallContext("billing", "s1", "m"))).isFalse();
        assertThat(scope.matches(new CallContext(null, "s1", "m"))).isFalse();
        assertThat(scope.bucketKey(new CallContext("support-chat", "s1", "m"))).isEqualTo("feature:support-chat");
    }

    @Test
    void wildcardSessionAppliesPerSessionWithItsOwnBucket() {
        BudgetScope scope = BudgetScope.parse("session:*");
        assertThat(scope.matches(new CallContext("f", "s1", "m"))).isTrue();
        assertThat(scope.bucketKey(new CallContext("f", "s1", "m"))).isEqualTo("session:s1");
        assertThat(scope.bucketKey(new CallContext("f", "s2", "m"))).isEqualTo("session:s2");
    }

    @Test
    void globalAlwaysMatches() {
        BudgetScope scope = BudgetScope.parse("global");
        assertThat(scope.matches(new CallContext(null, null, "m"))).isTrue();
        assertThat(scope.bucketKey(new CallContext(null, null, "m"))).isEqualTo("global");
    }

    @Test
    void unknownScopeIsRejected() {
        assertThatThrownBy(() -> BudgetScope.parse("team:platform"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
