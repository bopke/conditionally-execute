package com.bopke.conditionallyexecute;

import com.bopke.conditionallyexecute.plugins.MultiThreadedPlugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirror of {@code packages/js/test/multi-threaded.js}.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class MultiThreadedTest {

    @Test
    @DisplayName("should execute onTrue when majority votes true")
    void onTrueOnMajorityTrue() {
        AtomicReference<String> branch = new AtomicReference<>(null);

        new ConditionallyExecute()
                .use(MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(3).build()))
                .condition(true)
                .onTrue(Handler.sync(() -> branch.set("true")))
                .onFalse(Handler.sync(() -> branch.set("false")))
                .execute().join();

        assertThat(branch.get()).isEqualTo("true");
    }

    @Test
    @DisplayName("should execute onFalse when majority votes false")
    void onFalseOnMajorityFalse() {
        AtomicReference<String> branch = new AtomicReference<>(null);

        new ConditionallyExecute()
                .use(MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(3).build()))
                .condition(false)
                .onTrue(Handler.sync(() -> branch.set("true")))
                .onFalse(Handler.sync(() -> branch.set("false")))
                .execute().join();

        assertThat(branch.get()).isEqualTo("false");
    }

    @Test
    @DisplayName("should work with 5 nodes")
    void fiveNodes() {
        AtomicBoolean called = new AtomicBoolean(false);

        new ConditionallyExecute()
                .use(MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(5).build()))
                .condition(true)
                .onTrue(Handler.sync(() -> called.set(true)))
                .execute().join();

        assertThat(called).isTrue();
    }

    @Test
    @DisplayName("should work with jitter enabled (chaos mode)")
    void jitterMode() {
        AtomicBoolean called = new AtomicBoolean(false);

        new ConditionallyExecute()
                .use(MultiThreadedPlugin.of(
                        MultiThreadedPlugin.options().nodes(3).jitter(true).build()))
                .condition(true)
                .onTrue(Handler.sync(() -> called.set(true)))
                .execute().join();

        assertThat(called).isTrue();
    }

    @Test
    @DisplayName("should compose with other middleware")
    void composesWithOtherMiddleware() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());

        new ConditionallyExecute()
                .use((ctx, next) -> {
                    log.add("outer-in");
                    return next.proceed().thenRun(() -> log.add("outer-out"));
                })
                .use(MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(3).build()))
                .use((ctx, next) -> {
                    log.add("inner-in");
                    return next.proceed().thenRun(() -> log.add("inner-out"));
                })
                .condition(true)
                .onTrue(Handler.sync(() -> log.add("handler")))
                .execute().join();

        assertThat(log).containsExactly("outer-in", "inner-in", "handler", "inner-out", "outer-out");
    }

    @Test
    @DisplayName("should throw on even node count")
    void rejectsEvenNodes() {
        assertThatThrownBy(() ->
                MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(4).build()))
                .hasMessageContaining("odd");
    }

    @Test
    @DisplayName("should throw on node count < 3")
    void rejectsTooFewNodes() {
        assertThatThrownBy(() ->
                MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(1).build()))
                .hasMessageContaining("≥ 3");
    }

    @Test
    @DisplayName("should timeout when workers are too slow")
    void timesOut() {
        assertThatThrownBy(() ->
                new ConditionallyExecute()
                        .use(MultiThreadedPlugin.of(
                                MultiThreadedPlugin.options().nodes(3).timeout(1).jitter(true).build()))
                        .condition(true)
                        .onTrue(Handler.sync(() -> {}))
                        .execute().join())
                .hasMessageContaining("timed out");
    }
}
