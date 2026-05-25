package com.bopke.conditionallyexecute;

import com.bopke.conditionallyexecute.plugins.GrpcConsensusPlugin;
import com.bopke.conditionallyexecute.plugins.GrpcNodeServer;
import com.bopke.conditionallyexecute.plugins.QuorumError;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirror of {@code packages/js/test/grpc.js}.
 *
 * <p>Assumes ports 52100-52102 (running nodes) and 59997-59999 (unreachable
 * targets) are free on the test machine.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class GrpcConsensusTest {

    private static final int[] PORTS = { 52100, 52101, 52102 };

    private final List<GrpcNodeServer> nodes = new ArrayList<>();

    @BeforeAll
    void startNodes() {
        List<CompletableFuture<GrpcNodeServer>> starts = new ArrayList<>();
        for (int i = 0; i < PORTS.length; i++) {
            final int idx = i;
            Map<String, Handler> handlerMap = Map.of(
                    "deploy", Handler.sync(() -> { /* handler runs on node */ }),
                    "failing", Handler.sync(() -> { throw new RuntimeException("node " + idx + " handler failed"); })
            );
            starts.add(GrpcNodeServer.startAsync(PORTS[i], handlerMap));
        }
        for (CompletableFuture<GrpcNodeServer> f : starts) {
            nodes.add(f.join());
        }
    }

    @AfterAll
    void stopNodes() {
        List<CompletableFuture<Void>> stops = new ArrayList<>();
        for (GrpcNodeServer n : nodes) {
            stops.add(n.close());
        }
        for (CompletableFuture<Void> f : stops) {
            try {
                f.join();
            } catch (Exception ignored) { }
        }
    }

    private static List<String> addresses() {
        List<String> a = new ArrayList<>();
        for (int p : PORTS) a.add("localhost:" + p);
        return a;
    }

    @Test
    @DisplayName("should execute handler on all nodes and pass quorum")
    void allNodesPassQuorum() {
        AtomicBoolean coordinatorRan = new AtomicBoolean(false);

        new ConditionallyExecute()
                .use(GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                        .nodes(addresses())
                        .handlerName("deploy")
                        .quorum(2)
                        .build()))
                .condition(true)
                .onTrue(Handler.sync(() -> coordinatorRan.set(true)))
                .execute().join();

        assertThat(coordinatorRan).isTrue();
    }

    @Test
    @DisplayName("should skip coordinator handler when condition is false")
    void skipsCoordinatorOnFalseCondition() {
        AtomicBoolean coordinatorRan = new AtomicBoolean(false);

        try {
            new ConditionallyExecute()
                    .use(GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                            .nodes(addresses())
                            .handlerName("deploy")
                            .quorum(1)
                            .build()))
                    .condition(false)
                    .onTrue(Handler.sync(() -> coordinatorRan.set(true)))
                    .execute().join();
        } catch (Exception expected) {
            // quorum not reached when nodes report executed=false for condition=false
        }

        assertThat(coordinatorRan).isFalse();
    }

    @Test
    @DisplayName("should throw QuorumError when quorum is not reached")
    void quorumNotReachedThrows() {
        assertThatThrownBy(() ->
                new ConditionallyExecute()
                        .use(GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                                .nodes(List.of("localhost:59998", "localhost:59999"))
                                .handlerName("deploy")
                                .quorum(1)
                                .timeout(500)
                                .build()))
                        .condition(true)
                        .onTrue(Handler.sync(() -> {}))
                        .execute().join())
                .satisfies(thrown -> {
                    Throwable cause = thrown;
                    while (cause instanceof CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    assertThat(cause).isInstanceOf(QuorumError.class);
                    QuorumError qe = (QuorumError) cause;
                    assertThat(qe.reached()).isEqualTo(0);
                    assertThat(qe.required()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("should expose node results on QuorumError")
    void quorumErrorHasNodeResults() {
        AtomicReference<Throwable> caught = new AtomicReference<>(null);

        new ConditionallyExecute()
                .use(GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                        .nodes(List.of("localhost:59997"))
                        .handlerName("deploy")
                        .quorum(1)
                        .timeout(300)
                        .build()))
                .condition(true)
                .onTrue(Handler.sync(() -> {}))
                .onError(caught::set)
                .execute().join();

        assertThat(caught.get()).isInstanceOf(QuorumError.class);
        QuorumError qe = (QuorumError) caught.get();
        assertThat(qe.nodeResults()).hasSize(1);
        assertThat(qe.nodeResults().get(0).address()).isEqualTo("localhost:59997");
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
                .use(GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                        .nodes(List.of("localhost:" + PORTS[0], "localhost:" + PORTS[1]))
                        .handlerName("deploy")
                        .quorum(1)
                        .build()))
                .use((ctx, next) -> {
                    log.add("inner-in");
                    return next.proceed().thenRun(() -> log.add("inner-out"));
                })
                .condition(true)
                .onTrue(Handler.sync(() -> log.add("coordinator")))
                .execute().join();

        assertThat(log).containsExactly("outer-in", "inner-in", "coordinator", "inner-out", "outer-out");
    }

    @Test
    @DisplayName("should throw on invalid options")
    void rejectsInvalidOptions() {
        assertThatThrownBy(() ->
                GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                        .nodes(List.of())
                        .handlerName("x")
                        .build()))
                .hasMessageContaining("non-empty");

        assertThatThrownBy(() ->
                GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                        .nodes(List.of("a"))
                        .handlerName("")
                        .build()))
                .hasMessageContaining("handlerName");

        assertThatThrownBy(() ->
                GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
                        .nodes(List.of("a"))
                        .handlerName("x")
                        .quorum(5)
                        .build()))
                .hasMessageContaining("cannot exceed");
    }
}
