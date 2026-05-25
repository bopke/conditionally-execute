package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.AggregateException;
import com.bopke.conditionallyexecute.Handler;
import com.bopke.conditionallyexecute.Middleware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Runs every handler even if some fail, then collects all failures into a
 * single {@link AggregateException}. Without this plugin
 * {@link java.util.concurrent.CompletableFuture#allOf} short-circuits on the
 * first failure.
 *
 * <pre>{@code
 * new ConditionallyExecute()
 *     .use(CollectErrorsPlugin.of())
 *     .condition(true)
 *     .onTrue(handlerA)   // fails — captured
 *     .onTrue(handlerB)   // still runs
 *     .onTrue(handlerC)   // still runs
 *     .execute()
 *     .join();           // throws AggregateException
 * }</pre>
 */
public final class CollectErrorsPlugin {

    private CollectErrorsPlugin() {}

    /**
     * @return middleware that collects per-handler failures
     */
    public static Middleware of() {
        return (ctx, next) -> {
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

            List<Handler> wrapped = new ArrayList<>(ctx.handlers().size());
            for (Handler h : ctx.handlers()) {
                wrapped.add(() -> {
                    CompletableFuture<Void> inner;
                    try {
                        inner = h.run();
                        if (inner == null) {
                            inner = CompletableFuture.completedFuture(null);
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                        return CompletableFuture.completedFuture(null);
                    }
                    return inner.handle((v, err) -> {
                        if (err != null) {
                            errors.add(unwrap(err));
                        }
                        return null;
                    });
                });
            }
            ctx.setHandlers(wrapped);

            return next.proceed().thenCompose(v -> {
                if (errors.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new AggregateException(new ArrayList<>(errors)));
                return failed;
            });
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
