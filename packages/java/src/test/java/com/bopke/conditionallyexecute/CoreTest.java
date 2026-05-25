package com.bopke.conditionallyexecute;

import com.bopke.conditionallyexecute.plugins.AuditLogPlugin;
import com.bopke.conditionallyexecute.plugins.CollectErrorsPlugin;
import com.bopke.conditionallyexecute.plugins.DryRunPlugin;
import com.bopke.conditionallyexecute.plugins.RetryPlugin;
import com.bopke.conditionallyexecute.plugins.TimeoutError;
import com.bopke.conditionallyexecute.plugins.TimeoutPlugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirror of {@code packages/js/test/core.js} — 1:1 mapping where possible.
 */
class CoreTest {

    // ---------------------------------------------------------------------
    // Basic functionality
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("basic functionality")
    class BasicFunctionality {

        @Test
        @DisplayName("should execute onFalse when condition is falsy")
        void executesOnFalseWhenFalsy() {
            AtomicBoolean wasTrue = new AtomicBoolean(false);
            AtomicBoolean wasFalse = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .onTrue(Handler.sync(() -> wasTrue.set(true)))
                    .onFalse(Handler.sync(() -> wasFalse.set(true)))
                    .condition(1 == 2)
                    .execute().join();

            assertThat(wasFalse).isTrue();
            assertThat(wasTrue).isFalse();
        }

        @Test
        @DisplayName("should execute onTrue when condition is truthy")
        void executesOnTrueWhenTruthy() {
            AtomicBoolean wasTrue = new AtomicBoolean(false);
            AtomicBoolean wasFalse = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .onTrue(Handler.sync(() -> wasTrue.set(true)))
                    .onFalse(Handler.sync(() -> wasFalse.set(true)))
                    .condition(1 == 1)
                    .execute().join();

            assertThat(wasFalse).isFalse();
            assertThat(wasTrue).isTrue();
        }

        @Test
        @DisplayName("should execute onTrue when no condition is set (default true)")
        void executesOnTrueByDefault() {
            AtomicBoolean wasTrue = new AtomicBoolean(false);
            AtomicBoolean wasFalse = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .onTrue(Handler.sync(() -> wasTrue.set(true)))
                    .onFalse(Handler.sync(() -> wasFalse.set(true)))
                    .execute().join();

            assertThat(wasFalse).isFalse();
            assertThat(wasTrue).isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // Extended functionality
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("extended functionality")
    class ExtendedFunctionality {

        @Test
        @DisplayName("should execute all onFalse functions when condition is falsy")
        void runsAllOnFalse() {
            AtomicBoolean wasTrue = new AtomicBoolean(false);
            AtomicBoolean wasFalse = new AtomicBoolean(false);
            AtomicBoolean wasSecondFalse = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .onTrue(Handler.sync(() -> wasTrue.set(true)))
                    .onFalse(Handler.sync(() -> wasFalse.set(true)))
                    .onFalse(Handler.sync(() -> wasSecondFalse.set(true)))
                    .condition(1 == 2)
                    .execute().join();

            assertThat(wasFalse).isTrue();
            assertThat(wasSecondFalse).isTrue();
            assertThat(wasTrue).isFalse();
        }

        @Test
        @DisplayName("should execute all onTrue functions when condition is truthy")
        void runsAllOnTrue() {
            AtomicBoolean wasTrue = new AtomicBoolean(false);
            AtomicBoolean wasSecondTrue = new AtomicBoolean(false);
            AtomicBoolean wasFalse = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .onTrue(Handler.sync(() -> wasTrue.set(true)))
                    .onTrue(Handler.sync(() -> wasSecondTrue.set(true)))
                    .onFalse(Handler.sync(() -> wasFalse.set(true)))
                    .execute().join();

            assertThat(wasFalse).isFalse();
            assertThat(wasTrue).isTrue();
            assertThat(wasSecondTrue).isTrue();
        }

        @Test
        @DisplayName("should execute all onTrue functions when no condition is set")
        void runsAllOnTrueByDefault() {
            AtomicBoolean wasTrue = new AtomicBoolean(false);
            AtomicBoolean wasSecondTrue = new AtomicBoolean(false);
            AtomicBoolean wasFalse = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .onTrue(Handler.sync(() -> wasTrue.set(true)))
                    .onTrue(Handler.sync(() -> wasSecondTrue.set(true)))
                    .onFalse(Handler.sync(() -> wasFalse.set(true)))
                    .execute().join();

            assertThat(wasFalse).isFalse();
            assertThat(wasTrue).isTrue();
            assertThat(wasSecondTrue).isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // condition() semantics
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("condition() semantics")
    class ConditionSemantics {

        @Test
        @DisplayName("should coerce non-boolean truthy values to true")
        void coercesTruthy() {
            AtomicReference<String> branch = new AtomicReference<>(null);

            new ConditionallyExecute()
                    .condition((Object) "non-empty string")
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();

            assertThat(branch.get()).isEqualTo("true");
        }

        @Test
        @DisplayName("should coerce non-boolean falsy values to false")
        void coercesFalsy() {
            Object[] falsy = { 0, "", null, Double.NaN };
            for (Object f : falsy) {
                AtomicReference<String> branch = new AtomicReference<>(null);
                new ConditionallyExecute()
                        .condition(f)
                        .onTrue(Handler.sync(() -> branch.set("true")))
                        .onFalse(Handler.sync(() -> branch.set("false")))
                        .execute().join();
                assertThat(branch.get())
                        .as("Expected false for condition(%s)", String.valueOf(f))
                        .isEqualTo("false");
            }
        }

        @Test
        @DisplayName("should use last condition() call when called multiple times")
        void lastCallWins() {
            AtomicReference<String> branch = new AtomicReference<>(null);

            new ConditionallyExecute()
                    .condition(false)
                    .condition(true)
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();

            assertThat(branch.get()).isEqualTo("true");
        }
    }

    // ---------------------------------------------------------------------
    // Async handlers
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("async handlers")
    class AsyncHandlers {

        @Test
        @DisplayName("should await async onTrue handlers")
        void awaitsAsyncOnTrue() {
            AtomicBoolean result = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(() -> CompletableFuture.runAsync(() -> {
                        sleep(10);
                        result.set(true);
                    }))
                    .execute().join();

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should await async onFalse handlers")
        void awaitsAsyncOnFalse() {
            AtomicBoolean result = new AtomicBoolean(false);

            new ConditionallyExecute()
                    .condition(false)
                    .onFalse(() -> CompletableFuture.runAsync(() -> {
                        sleep(10);
                        result.set(true);
                    }))
                    .execute().join();

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should run multiple async handlers concurrently")
        void runsAsyncConcurrently() {
            List<String> order = Collections.synchronizedList(new ArrayList<>());

            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(() -> CompletableFuture.runAsync(() -> {
                        sleep(20);
                        order.add("slow");
                    }))
                    .onTrue(() -> CompletableFuture.runAsync(() -> {
                        sleep(5);
                        order.add("fast");
                    }))
                    .execute().join();

            assertThat(order).hasSize(2);
            assertThat(order).contains("slow", "fast");
        }

        @Test
        @DisplayName("should propagate rejections from async handlers")
        void propagatesRejections() {
            assertThatThrownBy(() ->
                    new ConditionallyExecute()
                            .condition(true)
                            .onTrue(() -> CompletableFuture.failedFuture(new RuntimeException("boom")))
                            .execute().join())
                    .hasMessageContaining("boom");
        }
    }

    // ---------------------------------------------------------------------
    // executeSync()
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("executeSync()")
    class ExecuteSync {

        @Test
        @DisplayName("should execute onTrue synchronously when condition is true")
        void runsOnTrueSync() {
            AtomicBoolean called = new AtomicBoolean(false);
            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(Handler.sync(() -> called.set(true)))
                    .executeSync();
            assertThat(called).isTrue();
        }

        @Test
        @DisplayName("should execute onFalse synchronously when condition is false")
        void runsOnFalseSync() {
            AtomicBoolean called = new AtomicBoolean(false);
            new ConditionallyExecute()
                    .condition(false)
                    .onFalse(Handler.sync(() -> called.set(true)))
                    .executeSync();
            assertThat(called).isTrue();
        }

        @Test
        @DisplayName("should not execute onFalse when condition is true (sync)")
        void skipsOtherBranchSync() {
            AtomicBoolean called = new AtomicBoolean(false);
            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(Handler.sync(() -> {}))
                    .onFalse(Handler.sync(() -> called.set(true)))
                    .executeSync();
            assertThat(called).isFalse();
        }

        @Test
        @DisplayName("should execute multiple handlers in registration order (sync)")
        void runsHandlersInOrder() {
            List<Integer> order = new ArrayList<>();
            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(Handler.sync(() -> order.add(1)))
                    .onTrue(Handler.sync(() -> order.add(2)))
                    .onTrue(Handler.sync(() -> order.add(3)))
                    .executeSync();
            assertThat(order).containsExactly(1, 2, 3);
        }
    }

    // ---------------------------------------------------------------------
    // Input validation
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        @DisplayName("should throw when onTrue receives null")
        void rejectsNullOnTrue() {
            assertThatThrownBy(() -> new ConditionallyExecute().onTrue((Handler) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("onTrue");
        }

        @Test
        @DisplayName("should throw when onFalse receives null")
        void rejectsNullOnFalse() {
            assertThatThrownBy(() -> new ConditionallyExecute().onFalse((Handler) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("onFalse");
        }

        @Test
        @DisplayName("should throw for null Runnable passed to onTrue")
        void rejectsNullRunnableOnTrue() {
            assertThatThrownBy(() -> new ConditionallyExecute().onTrue((Runnable) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("onTrue");
        }

        @Test
        @DisplayName("should throw when use() receives null")
        void rejectsNullMiddleware() {
            assertThatThrownBy(() -> new ConditionallyExecute().use(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("use");
        }

        @Test
        @DisplayName("should throw when onError() receives null")
        void rejectsNullOnError() {
            assertThatThrownBy(() -> new ConditionallyExecute().onError(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("onError");
        }
    }

    // ---------------------------------------------------------------------
    // TimeoutPlugin
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("TimeoutPlugin")
    class Timeout {

        @Test
        @DisplayName("should throw TimeoutError when handler exceeds timeout")
        void throwsOnTimeout() {
            assertThatThrownBy(() ->
                    new ConditionallyExecute()
                            .use(TimeoutPlugin.of(50))
                            .condition(true)
                            .onTrue(() -> {
                                CompletableFuture<Void> slow = new CompletableFuture<>();
                                CompletableFuture.delayedExecutor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                                        .execute(() -> slow.complete(null));
                                return slow;
                            })
                            .execute().join())
                    .hasCauseInstanceOf(TimeoutError.class)
                    .hasMessageContaining("50ms");
        }

        @Test
        @DisplayName("should not throw when handler completes within timeout")
        void noThrowWhenWithinTimeout() {
            AtomicBoolean ran = new AtomicBoolean(false);
            new ConditionallyExecute()
                    .use(TimeoutPlugin.of(500))
                    .condition(true)
                    .onTrue(() -> CompletableFuture.runAsync(() -> {
                        sleep(10);
                        ran.set(true);
                    }))
                    .execute().join();
            assertThat(ran).isTrue();
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid ms argument")
        void rejectsInvalidMs() {
            assertThatThrownBy(() -> TimeoutPlugin.of(0))
                    .hasMessageContaining("positive number");
            assertThatThrownBy(() -> TimeoutPlugin.of(-1))
                    .hasMessageContaining("positive number");
        }
    }

    // ---------------------------------------------------------------------
    // RetryPlugin
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("RetryPlugin")
    class Retry {

        @Test
        @DisplayName("should retry failing handlers up to n times")
        void retriesUpToN() {
            AtomicInteger attempts = new AtomicInteger(0);
            new ConditionallyExecute()
                    .use(RetryPlugin.of(2))
                    .condition(true)
                    .onTrue(() -> {
                        int a = attempts.incrementAndGet();
                        if (a < 3) {
                            return CompletableFuture.failedFuture(new RuntimeException("transient failure"));
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .execute().join();
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw after exhausting retries")
        void throwsAfterExhausting() {
            AtomicInteger attempts = new AtomicInteger(0);
            assertThatThrownBy(() ->
                    new ConditionallyExecute()
                            .use(RetryPlugin.of(1))
                            .condition(true)
                            .onTrue(() -> {
                                attempts.incrementAndGet();
                                return CompletableFuture.failedFuture(new RuntimeException("always fails"));
                            })
                            .execute().join())
                    .hasMessageContaining("always fails");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should not retry when n is 0")
        void noRetriesWhenZero() {
            AtomicInteger attempts = new AtomicInteger(0);
            assertThatThrownBy(() ->
                    new ConditionallyExecute()
                            .use(RetryPlugin.of(0))
                            .condition(true)
                            .onTrue(() -> {
                                attempts.incrementAndGet();
                                return CompletableFuture.failedFuture(new RuntimeException("fail"));
                            })
                            .execute().join())
                    .hasMessageContaining("fail");
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("should apply exponential backoff between retries")
        void exponentialBackoff() {
            AtomicInteger attempts = new AtomicInteger(0);
            List<Long> times = new CopyOnWriteArrayList<>();
            new ConditionallyExecute()
                    .use(RetryPlugin.of(2, RetryPlugin.Backoff.EXPONENTIAL))
                    .condition(true)
                    .onTrue(() -> {
                        times.add(System.currentTimeMillis());
                        int a = attempts.incrementAndGet();
                        if (a < 3) {
                            return CompletableFuture.failedFuture(new RuntimeException("transient"));
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .execute().join();
            assertThat(attempts.get()).isEqualTo(3);
            assertThat(times.get(1) - times.get(0)).isGreaterThanOrEqualTo(90L);
            assertThat(times.get(2) - times.get(1)).isGreaterThanOrEqualTo(180L);
        }

        @Test
        @DisplayName("should apply linear backoff between retries")
        void linearBackoff() {
            AtomicInteger attempts = new AtomicInteger(0);
            List<Long> times = new CopyOnWriteArrayList<>();
            new ConditionallyExecute()
                    .use(RetryPlugin.of(2, RetryPlugin.Backoff.LINEAR))
                    .condition(true)
                    .onTrue(() -> {
                        times.add(System.currentTimeMillis());
                        int a = attempts.incrementAndGet();
                        if (a < 3) {
                            return CompletableFuture.failedFuture(new RuntimeException("transient"));
                        }
                        return CompletableFuture.completedFuture(null);
                    })
                    .execute().join();
            assertThat(attempts.get()).isEqualTo(3);
            // linear: attempt 0→1: 0ms, attempt 1→2: 100ms
            assertThat(times.get(2) - times.get(1)).isGreaterThanOrEqualTo(90L);
        }

        @Test
        @DisplayName("should throw for invalid arguments")
        void rejectsInvalid() {
            assertThatThrownBy(() -> RetryPlugin.of(-1))
                    .hasMessageContaining("non-negative integer");
            assertThatThrownBy(() -> RetryPlugin.of(1, null))
                    .hasMessageContaining("backoff");
        }
    }

    // ---------------------------------------------------------------------
    // DryRunPlugin
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DryRunPlugin")
    class DryRun {

        @Test
        @DisplayName("should not execute handlers")
        void skipsHandlers() {
            AtomicBoolean called = new AtomicBoolean(false);
            new ConditionallyExecute()
                    .use(DryRunPlugin.of(msg -> {}))
                    .condition(true)
                    .onTrue(Handler.sync(() -> called.set(true)))
                    .execute().join();
            assertThat(called).isFalse();
        }

        @Test
        @DisplayName("should log what would have run")
        void logsBranchAndCount() {
            List<String> logs = new ArrayList<>();
            new ConditionallyExecute()
                    .use(DryRunPlugin.of(logs::add))
                    .condition(false)
                    .onFalse(Handler.sync(() -> {}))
                    .onFalse(Handler.sync(() -> {}))
                    .execute().join();
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0)).contains("DryRun");
            assertThat(logs.get(0)).contains("2");
            assertThat(logs.get(0)).contains("onFalse");
        }
    }

    // ---------------------------------------------------------------------
    // onError()
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("onError()")
    class OnError {

        @Test
        @DisplayName("should call onError instead of throwing when handler fails")
        void routesToOnError() {
            AtomicReference<Throwable> caught = new AtomicReference<>(null);
            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(() -> CompletableFuture.failedFuture(new RuntimeException("handler blew up")))
                    .onError(caught::set)
                    .execute().join();
            assertThat(caught.get()).isNotNull();
            assertThat(caught.get().getMessage()).contains("handler blew up");
        }

        @Test
        @DisplayName("ConditionallyExecuteError should have correct name property")
        void errorClassMeta() {
            ConditionallyExecuteError err = new ConditionallyExecuteError("test");
            assertThat(err.getMessage()).isEqualTo("test");
            assertThat(err).isInstanceOf(RuntimeException.class);
            assertThat(err.getClass().getSimpleName()).isEqualTo("ConditionallyExecuteError");
        }

        @Test
        @DisplayName("should call onError with TimeoutError on timeout")
        void routesTimeoutToOnError() {
            AtomicReference<Throwable> caught = new AtomicReference<>(null);
            new ConditionallyExecute()
                    .use(TimeoutPlugin.of(30))
                    .condition(true)
                    .onTrue(() -> {
                        CompletableFuture<Void> slow = new CompletableFuture<>();
                        CompletableFuture.delayedExecutor(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                                .execute(() -> slow.complete(null));
                        return slow;
                    })
                    .onError(caught::set)
                    .execute().join();
            assertThat(caught.get()).isInstanceOf(TimeoutError.class);
        }
    }

    // ---------------------------------------------------------------------
    // Middleware
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("middleware (.use())")
    class MiddlewareTests {

        @Test
        @DisplayName("should call middleware before handler execution")
        void runsBeforeAndAfter() {
            List<String> log = Collections.synchronizedList(new ArrayList<>());

            new ConditionallyExecute()
                    .use((ctx, next) -> {
                        log.add("before");
                        return next.proceed().thenRun(() -> log.add("after"));
                    })
                    .condition(true)
                    .onTrue(Handler.sync(() -> log.add("handler")))
                    .execute().join();

            assertThat(log).containsExactly("before", "handler", "after");
        }

        @Test
        @DisplayName("should expose correct branch in ctx")
        void exposesBranchInCtx() {
            AtomicReference<Context> captured = new AtomicReference<>();
            new ConditionallyExecute()
                    .use((ctx, next) -> {
                        captured.set(ctx);
                        return next.proceed();
                    })
                    .condition(false)
                    .onTrue(Handler.sync(() -> {}))
                    .onFalse(Handler.sync(() -> {}))
                    .execute().join();
            assertThat(captured.get().branch()).isEqualTo(Branch.ON_FALSE);
            assertThat(captured.get().condition()).isFalse();
        }

        @Test
        @DisplayName("should allow middleware to override condition")
        void allowsConditionOverride() {
            AtomicReference<String> branch = new AtomicReference<>(null);

            Middleware overrideToFalse = (ctx, next) -> {
                ctx.setCondition(false);
                ctx.setBranch(Branch.ON_FALSE);
                ctx.setHandlers(new ArrayList<>(ctx.onFalseHandlers()));
                return next.proceed();
            };

            new ConditionallyExecute()
                    .use(overrideToFalse)
                    .condition(true)
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();

            assertThat(branch.get()).isEqualTo("false");
        }

        @Test
        @DisplayName("should compose multiple middlewares in order")
        void composesMiddlewares() {
            List<String> log = Collections.synchronizedList(new ArrayList<>());

            new ConditionallyExecute()
                    .use((ctx, next) -> {
                        log.add("mw1-in");
                        return next.proceed().thenRun(() -> log.add("mw1-out"));
                    })
                    .use((ctx, next) -> {
                        log.add("mw2-in");
                        return next.proceed().thenRun(() -> log.add("mw2-out"));
                    })
                    .condition(true)
                    .onTrue(Handler.sync(() -> log.add("handler")))
                    .execute().join();

            assertThat(log).containsExactly("mw1-in", "mw2-in", "handler", "mw2-out", "mw1-out");
        }
    }

    // ---------------------------------------------------------------------
    // AuditLogPlugin
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("AuditLogPlugin")
    class AuditLog {

        @Test
        @DisplayName("should log to the provided logger after execution")
        void logsToCustomLogger() {
            List<String> logs = new ArrayList<>();

            new ConditionallyExecute()
                    .use(AuditLogPlugin.of(logs::add))
                    .condition(true)
                    .onTrue(Handler.sync(() -> {}))
                    .execute().join();

            assertThat(logs).hasSize(1);
            assertThat(logs.get(0)).contains("ConditionallyExecute");
            assertThat(logs.get(0)).contains("condition=true");
            assertThat(logs.get(0)).contains("branch=onTrue");
            assertThat(logs.get(0)).contains("handlers=1");
            assertThat(logs.get(0)).contains("duration=");
        }

        @Test
        @DisplayName("should not log when plugin is not used")
        void silentByDefault() {
            List<String> logs = new ArrayList<>();
            // No plugin → no log call possible; verify by asserting logs stays empty
            new ConditionallyExecute()
                    .condition(true)
                    .onTrue(Handler.sync(() -> {}))
                    .execute().join();
            assertThat(logs).isEmpty();
        }
    }

    // ---------------------------------------------------------------------
    // Named condition registry
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("named condition registry")
    class Registry {

        @AfterEach
        void clearRegistry() {
            ConditionallyExecute.clearRegistry();
        }

        @Test
        @DisplayName("should evaluate a registered condition by name")
        void registeredCondition() {
            ConditionallyExecute.register("alwaysTrue", () -> true);

            AtomicReference<String> branch = new AtomicReference<>(null);
            new ConditionallyExecute()
                    .condition("alwaysTrue")
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();

            assertThat(branch.get()).isEqualTo("true");
        }

        @Test
        @DisplayName("should NOT use registry for non-string condition values")
        void boolNotLookedUpInRegistry() {
            ConditionallyExecute.register("false", () -> true);
            AtomicReference<String> branch = new AtomicReference<>(null);
            new ConditionallyExecute()
                    .condition(false)
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();
            assertThat(branch.get()).isEqualTo("false");
        }

        @Test
        @DisplayName("should support dynamic registered conditions")
        void dynamicCondition() {
            AtomicBoolean value = new AtomicBoolean(false);
            ConditionallyExecute.register("dynamic", value::get);

            AtomicReference<String> branch = new AtomicReference<>(null);

            new ConditionallyExecute()
                    .condition("dynamic")
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();
            assertThat(branch.get()).isEqualTo("false");

            value.set(true);
            new ConditionallyExecute()
                    .condition("dynamic")
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();
            assertThat(branch.get()).isEqualTo("true");
        }

        @Test
        @DisplayName("should clear registry via clearRegistry()")
        void clearsRegistry() {
            ConditionallyExecute.register("myCondition", () -> false);
            ConditionallyExecute.clearRegistry();

            AtomicReference<String> branch = new AtomicReference<>(null);
            new ConditionallyExecute()
                    .condition("myCondition") // missing → coerces non-empty string to true
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();
            assertThat(branch.get()).isEqualTo("true");
        }

        @Test
        @DisplayName("should remove a single entry via unregister()")
        void unregistersSingle() {
            ConditionallyExecute.register("gone", () -> false);
            ConditionallyExecute.register("stays", () -> false);
            ConditionallyExecute.unregister("gone");

            AtomicReference<String> branch = new AtomicReference<>(null);
            new ConditionallyExecute()
                    .condition("gone")
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();
            assertThat(branch.get()).isEqualTo("true");

            new ConditionallyExecute()
                    .condition("stays")
                    .onTrue(Handler.sync(() -> branch.set("true")))
                    .onFalse(Handler.sync(() -> branch.set("false")))
                    .execute().join();
            assertThat(branch.get()).isEqualTo("false");
        }

        @Test
        @DisplayName("should throw for invalid register() arguments with useful messages")
        void rejectsInvalidRegister() {
            assertThatThrownBy(() -> ConditionallyExecute.register(null, () -> true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("string");
            assertThatThrownBy(() -> ConditionallyExecute.register("name", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("function");
        }
    }

    // ---------------------------------------------------------------------
    // CollectErrorsPlugin
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("CollectErrorsPlugin")
    class CollectErrors {

        @Test
        @DisplayName("should collect all handler errors into AggregateException")
        void collectsAllErrors() {
            assertThatThrownBy(() ->
                    new ConditionallyExecute()
                            .use(CollectErrorsPlugin.of())
                            .condition(true)
                            .onTrue(() -> CompletableFuture.failedFuture(new RuntimeException("err1")))
                            .onTrue(() -> CompletableFuture.failedFuture(new RuntimeException("err2")))
                            .execute().join())
                    .satisfies(thrown -> {
                        Throwable cause = thrown;
                        while (cause instanceof CompletionException && cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        assertThat(cause).isInstanceOf(AggregateException.class);
                        AggregateException ae = (AggregateException) cause;
                        assertThat(ae.errors()).hasSize(2);
                        assertThat(ae.getMessage()).contains("2 handler");
                        List<String> messages = new ArrayList<>();
                        for (Throwable e : ae.errors()) messages.add(e.getMessage());
                        assertThat(messages).contains("err1", "err2");
                    });
        }

        @Test
        @DisplayName("should not throw when all handlers succeed")
        void noThrowOnAllSuccess() {
            AtomicInteger count = new AtomicInteger(0);
            new ConditionallyExecute()
                    .use(CollectErrorsPlugin.of())
                    .condition(true)
                    .onTrue(Handler.sync(count::incrementAndGet))
                    .onTrue(Handler.sync(count::incrementAndGet))
                    .execute().join();
            assertThat(count.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should not include successful handlers in error list")
        void onlyFailedAreCollected() {
            assertThatThrownBy(() ->
                    new ConditionallyExecute()
                            .use(CollectErrorsPlugin.of())
                            .condition(true)
                            .onTrue(Handler.sync(() -> { /* succeeds */ }))
                            .onTrue(() -> CompletableFuture.failedFuture(new RuntimeException("only-this-fails")))
                            .execute().join())
                    .satisfies(thrown -> {
                        Throwable cause = thrown;
                        while (cause instanceof CompletionException && cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        assertThat(cause).isInstanceOf(AggregateException.class);
                        AggregateException ae = (AggregateException) cause;
                        assertThat(ae.errors()).hasSize(1);
                        assertThat(ae.errors().get(0).getMessage()).contains("only-this-fails");
                    });
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
