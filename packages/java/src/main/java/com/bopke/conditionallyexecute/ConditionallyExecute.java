package com.bopke.conditionallyexecute;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ConditionallyExecute — composable conditional execution.
 *
 * <p>The core provides {@link #condition(boolean)}, {@link #onTrue(Handler)},
 * {@link #onFalse(Handler)}, {@link #onError(Consumer)},
 * {@link #use(Middleware)}, {@link #execute()}, and {@link #executeSync()}.
 * Everything else — timeouts, retries, dry runs, audit logs, distributed
 * consensus — lives in {@code com.bopke.conditionallyexecute.plugins}
 * and is composed via {@link #use(Middleware)}.</p>
 *
 * <p>This is the Java port of the
 * <a href="https://github.com/bopke/conditionally-execute">JavaScript
 * conditionally-execute</a> library, intentionally preserving the same API
 * shape (builder + middleware) with idiomatic Java equivalents (functional
 * interfaces, {@link CompletableFuture}, virtual threads where appropriate).</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * import com.bopke.conditionallyexecute.plugins.TimeoutPlugin;
 * import com.bopke.conditionallyexecute.plugins.RetryPlugin;
 * import com.bopke.conditionallyexecute.plugins.RetryPlugin.Backoff;
 *
 * new ConditionallyExecute()
 *     .use(TimeoutPlugin.of(5000))
 *     .use(RetryPlugin.of(3, Backoff.EXPONENTIAL))
 *     .condition(isHealthy)
 *     .onTrue(Handler.sync(this::deployToProduction))
 *     .execute()
 *     .join();
 * }</pre>
 */
public final class ConditionallyExecute {

    // -----------------------------------------------------------------------
    // Named-condition registry (static)
    // -----------------------------------------------------------------------

    private static final ConcurrentMap<String, Supplier<Object>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * Register a named condition for reuse across instances. Mirrors the JS
     * {@code ConditionallyExecute.register(name, fn)} static method.
     *
     * @param name unique identifier (non-null)
     * @param fn   evaluator that produces an Object whose truthiness is checked
     *             at execution time (non-null)
     * @throws IllegalArgumentException if either argument is null
     */
    public static void register(String name, Supplier<Object> fn) {
        if (name == null) {
            throw new IllegalArgumentException(
                    "register() expects a string name, got null");
        }
        if (fn == null) {
            throw new IllegalArgumentException(
                    "register() expects a function evaluator, got null");
        }
        REGISTRY.put(name, fn);
    }

    /**
     * Remove a named condition from the registry. No-op if absent.
     *
     * @param name the name to remove
     */
    public static void unregister(String name) {
        if (name != null) {
            REGISTRY.remove(name);
        }
    }

    /** Clear every entry from the named-condition registry. */
    public static void clearRegistry() {
        REGISTRY.clear();
    }

    // -----------------------------------------------------------------------
    // Instance state
    // -----------------------------------------------------------------------

    private boolean condition = true;
    private final List<Handler> onTrue = new ArrayList<>();
    private final List<Handler> onFalse = new ArrayList<>();
    private final List<Middleware> middlewares = new ArrayList<>();
    private Function<Throwable, CompletableFuture<Void>> errorHandler;

    /** Create an empty builder; condition defaults to {@code true}. */
    public ConditionallyExecute() {
        // default state initialized above
    }

    // -----------------------------------------------------------------------
    // Builder API
    // -----------------------------------------------------------------------

    /**
     * Set the condition value directly. Last call wins.
     *
     * @param value the boolean condition
     * @return this, for chaining
     */
    public ConditionallyExecute condition(boolean value) {
        this.condition = value;
        return this;
    }

    /**
     * Set the condition from an arbitrary value. Mirrors JavaScript coercion
     * semantics:
     * <ul>
     *   <li>If {@code value} is a {@link String} and matches a registered name,
     *       the registry evaluator is invoked and its result coerced.</li>
     *   <li>Otherwise the value is coerced to boolean using JS-equivalent rules
     *       ({@code null} → false; empty string → false; non-empty string →
     *       true; {@link Number} with value 0 or NaN → false; {@link Boolean}
     *       passed through; any other non-null reference → true).</li>
     * </ul>
     *
     * @param value the value to coerce
     * @return this, for chaining
     */
    public ConditionallyExecute condition(Object value) {
        if (value instanceof String name && REGISTRY.containsKey(name)) {
            Supplier<Object> fn = REGISTRY.get(name);
            this.condition = coerceToBoolean(fn.get());
        } else {
            this.condition = coerceToBoolean(value);
        }
        return this;
    }

    /**
     * Set the condition by name. Tries the registry first; if no entry exists,
     * falls back to coercing the name string (any non-empty string is truthy,
     * matching {@code Boolean("anyString") === true} in JS).
     *
     * @param name registry name or arbitrary string
     * @return this, for chaining
     */
    public ConditionallyExecute condition(String name) {
        return condition((Object) name);
    }

    /**
     * Append a handler to the truthy branch.
     *
     * @param handler non-null handler
     * @return this, for chaining
     * @throws IllegalArgumentException when handler is null
     */
    public ConditionallyExecute onTrue(Handler handler) {
        if (handler == null) {
            throw new IllegalArgumentException(
                    "onTrue() expects a function, got null");
        }
        onTrue.add(handler);
        return this;
    }

    /**
     * Append a synchronous {@link Runnable} as a truthy-branch handler.
     *
     * @param runnable non-null runnable
     * @return this, for chaining
     */
    public ConditionallyExecute onTrue(Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException(
                    "onTrue() expects a function, got null");
        }
        return onTrue(Handler.sync(runnable));
    }

    /**
     * Append a handler to the falsy branch.
     *
     * @param handler non-null handler
     * @return this, for chaining
     * @throws IllegalArgumentException when handler is null
     */
    public ConditionallyExecute onFalse(Handler handler) {
        if (handler == null) {
            throw new IllegalArgumentException(
                    "onFalse() expects a function, got null");
        }
        onFalse.add(handler);
        return this;
    }

    /**
     * Append a synchronous {@link Runnable} as a falsy-branch handler.
     *
     * @param runnable non-null runnable
     * @return this, for chaining
     */
    public ConditionallyExecute onFalse(Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException(
                    "onFalse() expects a function, got null");
        }
        return onFalse(Handler.sync(runnable));
    }

    /**
     * Register an error handler. When set, exceptions thrown during
     * {@link #execute()} are forwarded to this consumer instead of propagating.
     *
     * @param fn non-null error consumer
     * @return this, for chaining
     * @throws IllegalArgumentException when fn is null
     */
    public ConditionallyExecute onError(Consumer<Throwable> fn) {
        if (fn == null) {
            throw new IllegalArgumentException(
                    "onError() expects a function, got null");
        }
        this.errorHandler = err -> {
            fn.accept(err);
            return CompletableFuture.completedFuture(null);
        };
        return this;
    }

    /**
     * Register an asynchronous error handler. When set, exceptions thrown
     * during {@link #execute()} are forwarded to this function and the returned
     * future is awaited.
     *
     * @param fn non-null async error handler
     * @return this, for chaining
     * @throws IllegalArgumentException when fn is null
     */
    public ConditionallyExecute onErrorAsync(Function<Throwable, CompletableFuture<Void>> fn) {
        if (fn == null) {
            throw new IllegalArgumentException(
                    "onError() expects a function, got null");
        }
        this.errorHandler = fn;
        return this;
    }

    /**
     * Install a middleware. Middlewares run in registration order, with the
     * first registered being the outermost wrapper.
     *
     * @param middleware non-null middleware
     * @return this, for chaining
     * @throws IllegalArgumentException when middleware is null
     */
    public ConditionallyExecute use(Middleware middleware) {
        if (middleware == null) {
            throw new IllegalArgumentException(
                    "use() expects a function middleware, got null");
        }
        middlewares.add(middleware);
        return this;
    }

    // -----------------------------------------------------------------------
    // Execution
    // -----------------------------------------------------------------------

    /**
     * Execute the middleware chain and active-branch handlers asynchronously.
     * All active-branch handlers run concurrently via
     * {@link CompletableFuture#allOf(CompletableFuture[])}.
     *
     * @return a future that completes when execution (including any error
     *         handler) has finished
     */
    public CompletableFuture<Void> execute() {
        Branch initialBranch = Branch.of(condition);
        List<Handler> active = new ArrayList<>(condition ? onTrue : onFalse);
        Context ctx = new Context(condition, initialBranch, active, onTrue, onFalse);

        CompletableFuture<Void> chain = dispatch(ctx, 0);

        if (errorHandler == null) {
            return chain;
        }

        return chain.handle((unused, err) -> {
            if (err == null) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            Throwable unwrapped = unwrap(err);
            try {
                CompletableFuture<Void> handled = errorHandler.apply(unwrapped);
                return handled != null ? handled : CompletableFuture.<Void>completedFuture(null);
            } catch (Throwable t) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }).thenCompose(f -> f);
    }

    private CompletableFuture<Void> dispatch(Context ctx, int i) {
        if (i < middlewares.size()) {
            Middleware mw = middlewares.get(i);
            try {
                CompletableFuture<Void> r = mw.apply(ctx, () -> dispatch(ctx, i + 1));
                return r != null ? r : CompletableFuture.completedFuture(null);
            } catch (Throwable t) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(t);
                return failed;
            }
        }
        return runHandlers(ctx.handlers());
    }

    private static CompletableFuture<Void> runHandlers(List<Handler> handlers) {
        if (handlers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = new CompletableFuture<?>[handlers.size()];
        for (int i = 0; i < handlers.size(); i++) {
            CompletableFuture<Void> f;
            try {
                f = handlers.get(i).run();
                if (f == null) {
                    f = CompletableFuture.completedFuture(null);
                }
            } catch (Throwable t) {
                f = new CompletableFuture<>();
                f.completeExceptionally(t);
            }
            futures[i] = f;
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * Execute active-branch handlers sequentially, in registration order, with
     * no middleware support. Use when handlers are synchronous and overhead
     * matters. The {@link Handler#run()} future is invoked but not awaited —
     * sync handlers complete immediately so this works as expected for the
     * intended use case.
     */
    public void executeSync() {
        List<Handler> handlers = condition ? onTrue : onFalse;
        for (Handler h : handlers) {
            CompletableFuture<Void> f = h.run();
            if (f != null && f.isCompletedExceptionally()) {
                // Surface synchronous exceptions immediately, unwrapping the
                // CompletionException wrapper that join() would otherwise add.
                try {
                    f.join();
                } catch (CompletionException ce) {
                    Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                    if (cause instanceof RuntimeException re) throw re;
                    if (cause instanceof Error e) throw e;
                    throw new RuntimeException(cause);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Coerce an arbitrary value to boolean using JavaScript semantics:
     * {@code null} → false; {@link Boolean} pass-through;
     * {@link Number} 0 or NaN → false; non-empty {@link String} → true;
     * empty {@link String} → false; any other non-null object → true.
     */
    static boolean coerceToBoolean(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean b -> b;
            case String s -> !s.isEmpty();
            case Number n -> {
                double d = n.doubleValue();
                yield d != 0.0 && !Double.isNaN(d);
            }
            default -> true;
        };
    }

    private static Throwable unwrap(Throwable t) {
        Objects.requireNonNull(t, "throwable");
        Throwable cur = t;
        while (cur instanceof CompletionException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }
}
