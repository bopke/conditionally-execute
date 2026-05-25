package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.ConditionallyExecuteError;

/**
 * Thrown when {@link TimeoutPlugin} cancels execution because it exceeded the
 * configured deadline. Message format matches the JS module:
 * {@code "Handler execution timed out after {ms}ms"}.
 */
public class TimeoutError extends ConditionallyExecuteError {
    private static final long serialVersionUID = 1L;

    private final long ms;

    /**
     * @param ms the timeout in milliseconds that was exceeded
     */
    public TimeoutError(long ms) {
        super("Handler execution timed out after " + ms + "ms");
        this.ms = ms;
    }

    /** @return the timeout (in ms) that was exceeded */
    public long getMs() {
        return ms;
    }
}
