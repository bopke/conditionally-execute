package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Branch;
import com.bopke.conditionallyexecute.Middleware;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributed consensus for your if-statements.
 *
 * <p>Spawns N virtual threads acting as independent consensus nodes. Each
 * "node" receives the condition value and casts a vote. The majority vote
 * determines which branch executes. Communication is in-process via shared
 * concurrent primitives — same architecture as the JS module's
 * {@code worker_threads} implementation, just using JEP 444 virtual threads
 * instead.</p>
 *
 * <pre>{@code
 * new ConditionallyExecute()
 *     .use(MultiThreadedPlugin.of(MultiThreadedPlugin.options().nodes(5).build()))
 *     .condition(userIsAdmin)
 *     .onTrue(() -> grantAccess())
 *     .onFalse(() -> denyAccess())
 *     .execute();
 * }</pre>
 */
public final class MultiThreadedPlugin {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "conditionally-execute-mt-timeout");
                t.setDaemon(true);
                return t;
            });

    private MultiThreadedPlugin() {}

    /** Configuration for {@link #of(Options)}. */
    public static final class Options {
        final int nodes;
        final long timeoutMs;
        final boolean jitter;
        final boolean verbose;

        private Options(Builder b) {
            this.nodes = b.nodes;
            this.timeoutMs = b.timeoutMs;
            this.jitter = b.jitter;
            this.verbose = b.verbose;
        }

        /** @return a fresh builder */
        public static Builder builder() {
            return new Builder();
        }

        /** Mutable builder for {@link Options}. */
        public static final class Builder {
            private int nodes = 3;
            private long timeoutMs = 2000L;
            private boolean jitter = false;
            private boolean verbose = false;

            /**
             * @param nodes consensus node count (must be odd, ≥ 3)
             * @return this
             */
            public Builder nodes(int nodes) {
                this.nodes = nodes;
                return this;
            }

            /**
             * @param timeoutMs maximum ms to wait for all votes
             * @return this
             */
            public Builder timeout(long timeoutMs) {
                this.timeoutMs = timeoutMs;
                return this;
            }

            /**
             * @param jitter add random latency per node (chaos testing)
             * @return this
             */
            public Builder jitter(boolean jitter) {
                this.jitter = jitter;
                return this;
            }

            /**
             * @param verbose log vote results to stdout
             * @return this
             */
            public Builder verbose(boolean verbose) {
                this.verbose = verbose;
                return this;
            }

            /** @return built options */
            public Options build() {
                return new Options(this);
            }
        }
    }

    /** Shortcut for {@code Options.builder()}. */
    public static Options.Builder options() {
        return Options.builder();
    }

    /**
     * Create the consensus middleware.
     *
     * @param options config (use {@link #options()} to build)
     * @return the middleware
     * @throws IllegalArgumentException if nodes is even or {@literal < 3}
     */
    public static Middleware of(Options options) {
        Objects.requireNonNull(options, "options");
        if (options.nodes < 3) {
            throw new IllegalArgumentException(
                    "MultiThreadedPlugin: nodes must be an integer ≥ 3");
        }
        if (options.nodes % 2 == 0) {
            throw new IllegalArgumentException(
                    "MultiThreadedPlugin: nodes must be odd to guarantee a clear majority");
        }

        return (ctx, next) -> {
            CompletableFuture<boolean[]> votesFuture =
                    collectVotes(ctx.condition(), options.nodes, options.timeoutMs, options.jitter);

            return votesFuture.thenCompose(votes -> {
                int trueVotes = 0;
                for (boolean v : votes) if (v) trueVotes++;
                int falseVotes = options.nodes - trueVotes;
                boolean consensus = trueVotes > falseVotes;

                if (options.verbose || System.getenv("CE_CONSENSUS_DEBUG") != null) {
                    System.out.println(String.format(
                            "[MultiThreadedPlugin] %d nodes voted: %d true / %d false → consensus=%s",
                            options.nodes, trueVotes, falseVotes, consensus));
                }

                ctx.setCondition(consensus);
                ctx.setBranch(Branch.of(consensus));
                ctx.setHandlers(new java.util.ArrayList<>(
                        consensus ? ctx.onTrueHandlers() : ctx.onFalseHandlers()));

                return next.proceed();
            });
        };
    }

    /**
     * Spawn N virtual-thread nodes and collect their votes.
     *
     * @param condition the input condition
     * @param nodeCount how many nodes
     * @param timeoutMs vote-collection timeout
     * @param jitter    whether to simulate latency
     * @return future of the vote array
     */
    public static CompletableFuture<boolean[]> collectVotes(
            boolean condition, int nodeCount, long timeoutMs, boolean jitter) {

        CompletableFuture<boolean[]> result = new CompletableFuture<>();
        ConcurrentLinkedQueue<Boolean> votes = new ConcurrentLinkedQueue<>();
        AtomicInteger remaining = new AtomicInteger(nodeCount);
        AtomicBoolean settled = new AtomicBoolean(false);

        var timeoutTask = SCHEDULER.schedule(() -> {
            if (settled.compareAndSet(false, true)) {
                result.completeExceptionally(new RuntimeException(
                        "MultiThreadedPlugin: vote collection timed out after " + timeoutMs + "ms"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        for (int i = 0; i < nodeCount; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    boolean vote = condition; // deterministic evaluation
                    if (jitter) {
                        long delay = ThreadLocalRandom.current().nextInt(50);
                        if (delay > 0) {
                            Thread.sleep(delay);
                        }
                    }
                    votes.add(vote);
                    if (remaining.decrementAndGet() == 0
                            && settled.compareAndSet(false, true)) {
                        timeoutTask.cancel(false);
                        boolean[] arr = new boolean[nodeCount];
                        int idx = 0;
                        for (Boolean b : votes) {
                            arr[idx++] = b != null && b;
                        }
                        result.complete(arr);
                    }
                } catch (Throwable t) {
                    if (settled.compareAndSet(false, true)) {
                        timeoutTask.cancel(false);
                        result.completeExceptionally(t);
                    }
                }
            });
        }

        return result;
    }
}
