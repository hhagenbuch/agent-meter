package io.github.hhagenbuch.meter.core.budget;

/** What to do when a budget is breached (DESIGN.md section 6): degrade before you deny. */
public enum BudgetAction {
    /** Emit an event/metric and proceed at the normal model. */
    WARN,
    /** Swap the request to a cheaper model for the rest of the window. */
    DEGRADE,
    /** Fail fast with an actionable message. */
    BLOCK
}
