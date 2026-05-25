package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Handler;
import com.bopke.conditionallyexecute.Middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Retries each handler individually on failure. Wraps every handler in
 * {@code ctx.handlers} with retry logic before forwarding the chain.
 *
 * <p>Each handler is retried independently up to {@code n} times — so total
 * attempts = {@code n + 1}.</p>
 *
 * <pre>{@code
 * new ConditionallyExecute()
 *     .use(RetryPlugin.of(3, Backoff.EXPONENTIAL))
 *     .condition(isHealthy)
 *     .onTrue(flakyNetworkCall)
 *     .execute();
 * }</pre>
 */
public final class RetryPlugin {

    /** Backoff strategy applied between retries. */
    public enum Backoff {
        /** No delay between attempts. */
        NONE,
        /** Delay = attempt × 100ms (0, 100, 200, …). */
        LINEAR,
        /** Delay = 2^attempt × 100ms (100, 200, 400, …). */
        EXPONENTIAL
    }

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "conditionally-execute-retry");
                t.setDaemon(true);
                return t;
            });

    private RetryPlugin() {}

    /**
     * Create a retry middleware with no backoff.
     *
     * @param n maximum retries (must be {@code >= 0})
     * @return the middleware
     * @throws IllegalArgumentException if {@code n < 0}
     */
    public static Middleware of(int n) {
        return of(n, Backoff.NONE);
    }

    /**
     * Create a retry middleware.
     *
     * @param n       maximum retries (must be {@code >= 0})
     * @param backoff backoff strategy
     * @return the middleware
     * @throws IllegalArgumentException if {@code n < 0} or backoff is null
     */
    public static Middleware of(int n, Backoff backoff) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    "RetryPlugin: n must be a non-negative integer, got " + n);
        }
        Objects.requireNonNull(backoff,
                "RetryPlugin: backoff must be 'none', 'linear', or 'exponential', got null");

        return (ctx, next) -> {
            List<Handler> wrapped = new ArrayList<>(ctx.handlers().size());
            for (Handler h : ctx.handlers()) {
                wrapped.add(wrapWithRetry(h, n, backoff));
            }
            ctx.setHandlers(wrapped);
            return next.proceed();
        };
    }

    private static Handler wrapWithRetry(Handler fn, int maxRetries, Backoff backoff) {
        return () -> attempt(fn, 0, maxRetries, backoff, null);
    }

    private static CompletableFuture<Void> attempt(
            Handler fn, int attempt, int maxRetries, Backoff backoff, Throwable lastError) {
        if (attempt > maxRetries) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(lastError);
            return failed;
        }

        CompletableFuture<Void> tryThis;
        try {
            tryThis = fn.run();
            if (tryThis == null) {
                tryThis = CompletableFuture.completedFuture(null);
            }
        } catch (Throwable t) {
            tryThis = new CompletableFuture<>();
            tryThis.completeExceptionally(t);
        }

        return tryThis.handle((v, err) -> {
            if (err == null) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            Throwable cause = unwrap(err);
            if (attempt >= maxRetries) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(cause);
                return failed;
            }
            long delayMs = backoffDelay(attempt, backoff);
            if (delayMs <= 0) {
                return attempt(fn, attempt + 1, maxRetries, backoff, cause);
            }
            CompletableFuture<Void> delayed = new CompletableFuture<>();
            SCHEDULER.schedule(() -> {
                attempt(fn, attempt + 1, maxRetries, backoff, cause)
                        .whenComplete((v2, err2) -> {
                            if (err2 != null) {
                                delayed.completeExceptionally(unwrap(err2));
                            } else {
                                delayed.complete(null);
                            }
                        });
            }, delayMs, TimeUnit.MILLISECONDS);
            return delayed;
        }).thenCompose(f -> f);
    }

    private static long backoffDelay(int attempt, Backoff backoff) {
        return switch (backoff) {
            case NONE -> 0L;
            case LINEAR -> attempt * 100L;
            case EXPONENTIAL -> (1L << attempt) * 100L;
        };
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while (cur instanceof CompletionException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }
}
