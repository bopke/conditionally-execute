package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Middleware;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Emits a structured log entry after the downstream chain completes. Defaults
 * to {@link System#out} but accepts any {@link Consumer} as a sink.
 *
 * <p>Output format matches the JS module exactly:
 * {@code [{ISO}] ConditionallyExecute: condition={cond} branch={branch} handlers={n} duration={ms}ms}.</p>
 */
public final class AuditLogPlugin {

    private AuditLogPlugin() {}

    /**
     * @return audit middleware that writes to {@code System.out}
     */
    public static Middleware of() {
        return of(System.out::println);
    }

    /**
     * @param logger non-null log sink
     * @return audit middleware that writes formatted entries to {@code logger}
     */
    public static Middleware of(Consumer<String> logger) {
        Objects.requireNonNull(logger, "logger");
        return (ctx, next) -> {
            long startNanos = System.nanoTime();
            return next.proceed().whenComplete((v, err) -> {
                double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
                String timestamp = Instant.now().toString();
                String message = String.format(
                        Locale.ROOT,
                        "[%s] ConditionallyExecute: condition=%s branch=%s handlers=%d duration=%.2fms",
                        timestamp,
                        ctx.condition(),
                        ctx.branch().toJsString(),
                        ctx.handlers().size(),
                        durationMs);
                logger.accept(message);
            });
        };
    }
}
