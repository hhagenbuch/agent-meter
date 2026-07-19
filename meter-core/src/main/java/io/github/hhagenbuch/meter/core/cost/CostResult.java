package io.github.hhagenbuch.meter.core.cost;

/**
 * The outcome of a cost computation. {@code known == false} means the model had no
 * price — recorded as <em>unknown</em>, never {@code 0.00}, because zero is a lie that
 * hides exactly the spend you most need to see (DESIGN.md section 5).
 *
 * @param estimated true when the price table was stale at call time (prices may have moved)
 */
public record CostResult(boolean known, double usd, boolean estimated) {

    public static final CostResult UNKNOWN = new CostResult(false, 0.0, false);

    public static CostResult of(double usd, boolean estimated) {
        return new CostResult(true, usd, estimated);
    }
}
