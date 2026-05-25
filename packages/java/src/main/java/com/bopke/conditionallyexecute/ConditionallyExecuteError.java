package com.bopke.conditionallyexecute;

/**
 * Base type for errors thrown by the library or its plugins. Equivalent to
 * the JavaScript {@code ConditionallyExecuteError} class.
 */
public class ConditionallyExecuteError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Construct with a message.
     *
     * @param message human-readable error message
     */
    public ConditionallyExecuteError(String message) {
        super(message);
    }

    /**
     * Construct with a message and underlying cause.
     *
     * @param message human-readable error message
     * @param cause   underlying throwable
     */
    public ConditionallyExecuteError(String message, Throwable cause) {
        super(message, cause);
    }
}
