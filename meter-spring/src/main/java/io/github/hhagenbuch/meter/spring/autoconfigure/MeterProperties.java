package io.github.hhagenbuch.meter.spring.autoconfigure;

import io.github.hhagenbuch.meter.core.budget.Budget;
import io.github.hhagenbuch.meter.core.budget.BudgetAction;
import io.github.hhagenbuch.meter.core.budget.BudgetScope;
import io.github.hhagenbuch.meter.core.budget.Window;
import io.github.hhagenbuch.meter.core.cost.CostEngine;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bound from the {@code meter.*} namespace. */
@ConfigurationProperties(prefix = "meter")
public class MeterProperties {

    /** Override for the bundled price table; when null, the jar's {@code prices.yaml} is used. */
    private String priceTablePath;

    /** A table older than this many days flags cost as estimated. */
    private int staleDays = CostEngine.DEFAULT_STALE_DAYS;

    /** {@code gen_ai.system} value for spans (e.g. anthropic, openai). */
    private String genAiSystem = "anthropic";

    /** {@code agent.prompt_version} for spans/metrics; typically {@code ${AGENT_PROMPT_VERSION:}}
     *  (agent-operator sets this label on deployments). Null when unset. */
    private String promptVersion;

    /** Declarative budgets, enforced by degrading before denying. */
    private List<BudgetSpec> budgets = new ArrayList<>();

    public String getPriceTablePath() {
        return priceTablePath;
    }

    public void setPriceTablePath(String priceTablePath) {
        this.priceTablePath = priceTablePath;
    }

    public int getStaleDays() {
        return staleDays;
    }

    public void setStaleDays(int staleDays) {
        this.staleDays = staleDays;
    }

    public String getGenAiSystem() {
        return genAiSystem;
    }

    public void setGenAiSystem(String genAiSystem) {
        this.genAiSystem = genAiSystem;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public List<BudgetSpec> getBudgets() {
        return budgets;
    }

    public void setBudgets(List<BudgetSpec> budgets) {
        this.budgets = budgets;
    }

    /** Convert the bound specs into meter-core {@link Budget} value objects. */
    public List<Budget> toBudgets() {
        List<Budget> out = new ArrayList<>();
        for (BudgetSpec spec : budgets) {
            out.add(new Budget(
                    BudgetScope.parse(spec.getScope()),
                    spec.getLimitUsd(),
                    Window.valueOf(spec.getWindow().toUpperCase(Locale.ROOT)),
                    BudgetAction.valueOf(spec.getOnBreach().toUpperCase(Locale.ROOT)),
                    spec.getDegradeTo()));
        }
        return out;
    }

    /** One budget as declared in {@code meter.budgets[*]}. */
    public static class BudgetSpec {
        private String scope;
        private double limitUsd;
        private String window = "daily";
        private String onBreach = "warn";
        private String degradeTo;

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public double getLimitUsd() {
            return limitUsd;
        }

        public void setLimitUsd(double limitUsd) {
            this.limitUsd = limitUsd;
        }

        public String getWindow() {
            return window;
        }

        public void setWindow(String window) {
            this.window = window;
        }

        public String getOnBreach() {
            return onBreach;
        }

        public void setOnBreach(String onBreach) {
            this.onBreach = onBreach;
        }

        public String getDegradeTo() {
            return degradeTo;
        }

        public void setDegradeTo(String degradeTo) {
            this.degradeTo = degradeTo;
        }
    }
}

