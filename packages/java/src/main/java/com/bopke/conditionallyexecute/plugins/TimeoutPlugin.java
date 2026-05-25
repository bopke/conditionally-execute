package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Middleware;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Aborts the downstream chain if it exceeds the configured deadline. Wraps the
 * remaining chain in a race against a scheduled timeout; if the timeout fires
 * first, a {@link TimeoutError} is thrown.
 *
 * <p>Compose <em>before</em> other middleware so the deadline covers everything
 * downstream.</p>
 *
 * <pre>{@code
 * new ConditionallyExecute()
 *     .use(TimeoutPlugin.of(3000))
 *     .condition(isReady)
 *     .onTrue(slowHandler)
 *     .execute();
 * }</pre>
 */
public final class TimeoutPlugin {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "conditionally-execute-timeout");
                t.setDaemon(true);
                return t;
            });

    private TimeoutPlugin() {}

    /**
     * Create the middleware.
     *
     * @param ms maximum allowed execution time in milliseconds (must be {@code > 0})
     * @return the middleware
     * @throws IllegalArgumentException if {@code ms <= 0}
     */
    public static Middleware of(long ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException(
                    "TimeoutPlugin: ms must be a positive number, got " + ms);
        }
        return (ctx, next) -> {
            CompletableFuture<Void> result = new CompletableFuture<>();
            var task = SCHEDULER.schedule(
                    () -> result.completeExceptionally(new TimeoutError(ms)),
                    ms, TimeUnit.MILLISECONDS);

            CompletableFuture<Void> downstream;
            try {
                downstream = next.proceed();
                if (downstream == null) {
                    downstream = CompletableFuture.completedFuture(null);
                }
            } catch (Throwable t) {
                task.cancel(false);
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }

            downstream.whenComplete((v, err) -> {
                task.cancel(false);
                if (err != null) {
                    result.completeExceptionally(err);
                } else {
                    result.complete(null);
                }
            });

            return result;
        };
    }
}
