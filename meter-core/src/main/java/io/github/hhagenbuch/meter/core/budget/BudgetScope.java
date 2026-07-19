package io.github.hhagenbuch.meter.core.budget;

/**
 * What a budget applies to: everything ({@code global}), a feature ({@code feature:name}
 * or {@code feature:*} for per-feature), or a session ({@code session:id} / {@code session:*}).
 *
 * <p>A wildcard ({@code *}) budget applies once <em>per</em> distinct value — a
 * per-session $1 cap means each session gets its own $1 — so {@link #bucketKey} resolves
 * the concrete spend bucket from the call context.
 */
public record BudgetScope(Type type, String value) {

    public enum Type { GLOBAL, FEATURE, SESSION }

    private static final String WILDCARD = "*";

    /** Parses {@code "global"}, {@code "feature:support-chat"}, {@code "session:*"}, etc. */
    public static BudgetScope parse(String raw) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("global")) {
            return new BudgetScope(Type.GLOBAL, WILDCARD);
        }
        int colon = s.indexOf(':');
        String head = colon < 0 ? s : s.substring(0, colon);
        String tail = colon < 0 ? WILDCARD : s.substring(colon + 1);
        Type type = switch (head.toLowerCase()) {
            case "feature" -> Type.FEATURE;
            case "session" -> Type.SESSION;
            default -> throw new IllegalArgumentException("unknown budget scope: '" + raw + "'");
        };
        return new BudgetScope(type, tail.isBlank() ? WILDCARD : tail);
    }

    /** Does this scope apply to the given call? A specific value must equal the call's; a
     *  wildcard applies whenever the call carries that dimension. */
    public boolean matches(CallContext ctx) {
        return switch (type) {
            case GLOBAL -> true;
            case FEATURE -> ctx.feature() != null && (isWildcard() || value.equals(ctx.feature()));
            case SESSION -> ctx.sessionId() != null && (isWildcard() || value.equals(ctx.sessionId()));
        };
    }

    /** The concrete spend bucket for this call (wildcards resolve to the call's value). */
    public String bucketKey(CallContext ctx) {
        return switch (type) {
            case GLOBAL -> "global";
            case FEATURE -> "feature:" + (isWildcard() ? ctx.feature() : value);
            case SESSION -> "session:" + (isWildcard() ? ctx.sessionId() : value);
        };
    }

    /** Human label for messages. */
    public String describe() {
        return type == Type.GLOBAL ? "global" : type.name().toLowerCase() + ":" + value;
    }

    private boolean isWildcard() {
        return WILDCARD.equals(value);
    }
}
