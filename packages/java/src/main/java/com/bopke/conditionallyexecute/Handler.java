package com.bopke.conditionallyexecute;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A handler invoked when a branch is selected.
 *
 * <p>Handlers return a {@link CompletableFuture} representing their (possibly
 * asynchronous) completion. Use {@link #sync(Runnable)} or {@link #async(Supplier)}
 * to wrap synchronous or asynchronous code respectively.</p>
 *
 * <p>This is the Java equivalent of the JavaScript signature
 * {@code () => void | Promise<void>}.</p>
 */
@FunctionalInterface
public interface Handler {
    /**
     * Run the handler.
     *
     * @return a future that completes when the handler is done; never {@code null}.
     */
    CompletableFuture<Void> run();

    /**
     * Wrap a {@link Runnable} as a Handler that completes synchronously.
     *
     * @param r the runnable to invoke
     * @return a Handler that runs {@code r} and returns an already-completed future
     */
    static Handler sync(Runnable r) {
        return () -> {
            try {
                r.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable t) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        };
    }

    /**
     * Wrap a {@link Supplier} of {@link CompletableFuture} as a Handler.
     *
     * @param s the supplier
     * @return a Handler delegating to {@code s}
     */
    static Handler async(Supplier<CompletableFuture<Void>> s) {
        return s::get;
    }
}
