package com.bopke.conditionallyexecute;

import java.util.List;
import java.util.Objects;

/**
 * Aggregates multiple handler failures into a single exception. Java equivalent
 * of the JavaScript {@code AggregateError}. The message has the format
 * {@code "{n} handler(s) failed"} to match the JS plugin output.
 */
public class AggregateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final List<Throwable> errors;

    /**
     * Construct from a list of throwables.
     *
     * @param errors all collected failures; must be non-null
     */
    public AggregateException(List<Throwable> errors) {
        super(Objects.requireNonNull(errors, "errors").size() + " handler(s) failed");
        this.errors = List.copyOf(errors);
        for (Throwable t : this.errors) {
            addSuppressed(t);
        }
    }

    /**
     * Construct with a custom message.
     *
     * @param errors  all collected failures
     * @param message message override
     */
    public AggregateException(List<Throwable> errors, String message) {
        super(message);
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        for (Throwable t : this.errors) {
            addSuppressed(t);
        }
    }

    /** @return immutable list of all collected errors */
    public List<Throwable> errors() {
        return errors;
    }
}
