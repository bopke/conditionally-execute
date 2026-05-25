package com.bopke.conditionallyexecute;

import java.util.concurrent.CompletableFuture;

/**
 * Middleware that wraps the handler execution chain.
 *
 * <p>A middleware receives the mutable {@link Context} and a {@link Next}
 * continuation. It may inspect or mutate the context, optionally call
 * {@code next.proceed()} (short-circuit by not calling it), and run additional
 * logic before or after the downstream chain completes.</p>
 *
 * <p>Composes in registration order — the first registered middleware runs
 * outermost.</p>
 */
@FunctionalInterface
public interface Middleware {
    /**
     * Apply this middleware.
     *
     * @param ctx  mutable execution context
     * @param next continuation
     * @return a future that completes when this middleware (and any downstream
     *         work it awaited) has finished
     */
    CompletableFuture<Void> apply(Context ctx, Next next);
}
