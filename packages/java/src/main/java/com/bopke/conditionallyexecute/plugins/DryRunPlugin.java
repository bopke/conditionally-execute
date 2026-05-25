package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Middleware;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Logs what would have run and short-circuits the chain — {@code next.proceed()}
 * is never called, so handlers and downstream middleware are skipped.
 *
 * <p>Compose last (innermost) so upstream middleware still executes normally.</p>
 *
 * <p>Log format matches the JS module:
 * {@code [DryRun] ConditionallyExecute: would execute {n} handler(s) on branch {branch}}.</p>
 */
public final class DryRunPlugin {

    private DryRunPlugin() {}

    /**
     * @return dry-run middleware that writes to {@code System.out}
     */
    public static Middleware of() {
        return of(System.out::println);
    }

    /**
     * @param logger non-null log sink
     * @return dry-run middleware that writes to {@code logger}
     */
    public static Middleware of(Consumer<String> logger) {
        Objects.requireNonNull(logger, "logger");
        return (ctx, next) -> {
            logger.accept(String.format(
                    "[DryRun] ConditionallyExecute: would execute %d handler(s) on branch %s",
                    ctx.handlers().size(),
                    ctx.branch().toJsString()));
            // intentionally does not call next.proceed()
            return CompletableFuture.completedFuture(null);
        };
    }
}
