package io.github.hhagenbuch.meter.spring.budget;

/**
 * Thrown by {@code block}-mode enforcement. The message is written for the developer who
 * meets it at 2am — which budget, the window, spend vs limit, and that it resets.
 */
public class BudgetExceededException extends RuntimeException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
