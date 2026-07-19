package io.github.hhagenbuch.meter.core.budget;

/**
 * Accumulated spend per (bucket, window). A pluggable interface so the in-memory MVP
 * can be swapped for a shared store (Redis) without touching the policy — the
 * single-instance limitation of the default is documented, not hidden (DESIGN.md section 6).
 */
public interface BudgetStore {

    double getSpend(String bucketKey, String windowKey);

    void addSpend(String bucketKey, String windowKey, double costUsd);
}
