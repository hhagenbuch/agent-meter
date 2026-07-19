package io.github.hhagenbuch.meter.core.budget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link BudgetStore}. Single-instance only: each process tracks its own
 * spend, so N replicas enforce a limit up to N times over. Fine for one instance or a
 * generous cap; swap in a shared store for strict multi-instance budgets.
 */
public final class InMemoryBudgetStore implements BudgetStore {

    private final Map<String, Double> spend = new ConcurrentHashMap<>();

    @Override
    public double getSpend(String bucketKey, String windowKey) {
        return spend.getOrDefault(key(bucketKey, windowKey), 0.0);
    }

    @Override
    public void addSpend(String bucketKey, String windowKey, double costUsd) {
        spend.merge(key(bucketKey, windowKey), costUsd, Double::sum);
    }

    private static String key(String bucketKey, String windowKey) {
        return bucketKey + "@" + windowKey;
    }
}
