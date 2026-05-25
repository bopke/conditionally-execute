package com.bopke.conditionallyexecute;

import java.util.concurrent.CompletableFuture;

/**
 * Continuation passed to a {@link Middleware}. Calling {@link #proceed()}
 * invokes the next middleware in the chain (or the handler dispatcher when
 * the current middleware is the last one).
 *
 * <p>Equivalent to the JavaScript {@code () => Promise<void>} callback.</p>
 */
@FunctionalInterface
public interface Next {
    /**
     * Continue execution downstream.
     *
     * @return a future that completes when the rest of the chain has finished
     */
    CompletableFuture<Void> proceed();
}
