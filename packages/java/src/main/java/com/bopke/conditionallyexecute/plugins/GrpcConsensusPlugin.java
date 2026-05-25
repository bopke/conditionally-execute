package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Middleware;
import com.bopke.conditionallyexecute.proto.ConditionallyExecuteNodeGrpc;
import com.bopke.conditionallyexecute.proto.ExecuteRequest;
import com.bopke.conditionallyexecute.proto.ExecuteResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade distributed execution with quorum agreement over gRPC.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Each "node" is a gRPC server running the {@code ConditionallyExecuteNode}
 *       service (see {@link GrpcNodeServer}).</li>
 *   <li>The coordinator fans out {@code Execute} RPCs to all configured nodes
 *       simultaneously.</li>
 *   <li>Each node runs its locally-registered handler and reports back.</li>
 *   <li>If fewer than {@code quorum} nodes confirm success → {@link QuorumError}.</li>
 * </ul>
 *
 * <pre>{@code
 * var n1 = GrpcNodeServer.startAsync(50051, Map.of("deploy", deployHandler)).join();
 * var n2 = GrpcNodeServer.startAsync(50052, Map.of("deploy", deployHandler)).join();
 * var n3 = GrpcNodeServer.startAsync(50053, Map.of("deploy", deployHandler)).join();
 *
 * new ConditionallyExecute()
 *     .use(GrpcConsensusPlugin.of(GrpcConsensusPlugin.options()
 *         .nodes(List.of("localhost:50051", "localhost:50052", "localhost:50053"))
 *         .handlerName("deploy")
 *         .quorum(2)
 *         .build()))
 *     .condition(isReadyForDeploy)
 *     .onTrue(() -> log.info("coordinator: quorum reached"))
 *     .execute();
 * }</pre>
 */
public final class GrpcConsensusPlugin {

    private GrpcConsensusPlugin() {}

    /** Configuration for {@link #of(Options)}. */
    public static final class Options {
        final List<String> nodes;
        final String handlerName;
        final int quorum;
        final long timeoutMs;
        final boolean verbose;

        private Options(Builder b) {
            this.nodes = List.copyOf(b.nodes);
            this.handlerName = b.handlerName;
            this.quorum = b.quorum > 0 ? b.quorum : (this.nodes.size() / 2 + 1);
            this.timeoutMs = b.timeoutMs;
            this.verbose = b.verbose;
        }

        /** @return a fresh builder */
        public static Builder builder() {
            return new Builder();
        }

        /** Mutable builder for {@link Options}. */
        public static final class Builder {
            private List<String> nodes = List.of();
            private String handlerName;
            private int quorum = -1;
            private long timeoutMs = 5000L;
            private boolean verbose = false;

            /**
             * @param nodes gRPC node addresses (host:port)
             * @return this
             */
            public Builder nodes(List<String> nodes) {
                this.nodes = nodes != null ? nodes : List.of();
                return this;
            }

            /**
             * @param handlerName name of the handler each node should invoke
             * @return this
             */
            public Builder handlerName(String handlerName) {
                this.handlerName = handlerName;
                return this;
            }

            /**
             * @param quorum minimum successful nodes required (default: majority)
             * @return this
             */
            public Builder quorum(int quorum) {
                this.quorum = quorum;
                return this;
            }

            /**
             * @param timeoutMs per-node RPC deadline
             * @return this
             */
            public Builder timeout(long timeoutMs) {
                this.timeoutMs = timeoutMs;
                return this;
            }

            /**
             * @param verbose log per-node results
             * @return this
             */
            public Builder verbose(boolean verbose) {
                this.verbose = verbose;
                return this;
            }

            /** @return built options */
            public Options build() {
                if (nodes == null || nodes.isEmpty()) {
                    throw new IllegalArgumentException(
                            "GrpcConsensusPlugin: nodes must be a non-empty array of gRPC addresses");
                }
                if (handlerName == null || handlerName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "GrpcConsensusPlugin: handlerName is required");
                }
                int effectiveQuorum = quorum > 0 ? quorum : (nodes.size() / 2 + 1);
                if (effectiveQuorum > nodes.size()) {
                    throw new IllegalArgumentException(
                            "GrpcConsensusPlugin: quorum (" + effectiveQuorum
                                    + ") cannot exceed node count (" + nodes.size() + ")");
                }
                return new Options(this);
            }
        }
    }

    /** Shortcut for {@code Options.builder()}. */
    public static Options.Builder options() {
        return Options.builder();
    }

    /**
     * Create the middleware.
     *
     * @param options config (use {@link #options()} to build)
     * @return the middleware
     */
    public static Middleware of(Options options) {
        Objects.requireNonNull(options, "options");

        return (ctx, next) -> {
            String requestId = UUID.randomUUID().toString();
            ExecuteRequest request = ExecuteRequest.newBuilder()
                    .setRequestId(requestId)
                    .setCondition(ctx.condition())
                    .setHandlerName(options.handlerName)
                    .build();

            List<CompletableFuture<NodeResult>> calls = new ArrayList<>(options.nodes.size());
            for (String addr : options.nodes) {
                calls.add(callNode(addr, request, options.timeoutMs));
            }

            CompletableFuture<?>[] arr = calls.toArray(new CompletableFuture<?>[0]);
            return CompletableFuture.allOf(arr).thenCompose(v -> {
                List<NodeResult> results = new ArrayList<>(calls.size());
                int succeeded = 0;
                for (CompletableFuture<NodeResult> c : calls) {
                    NodeResult r = c.join();
                    results.add(r);
                    if (r.success()) succeeded++;
                }

                if (options.verbose || System.getenv("CE_GRPC_DEBUG") != null) {
                    for (NodeResult r : results) {
                        String icon = r.success() ? "OK " : "ERR";
                        String detail;
                        if (r.rpcError() != null) {
                            detail = "RPC error: " + r.rpcError().getMessage();
                        } else if (r.response() != null) {
                            detail = String.format("branch=%s duration=%.2fms",
                                    r.response().getBranch(), r.response().getDurationMs());
                        } else {
                            detail = "no response";
                        }
                        System.out.println(String.format(
                                "[GrpcConsensusPlugin] %s %s: %s",
                                icon, r.address(), detail));
                    }
                    System.out.println(String.format(
                            "[GrpcConsensusPlugin] quorum: %d/%d (required %d) → %s",
                            succeeded, options.nodes.size(), options.quorum,
                            succeeded >= options.quorum ? "PASSED" : "FAILED"));
                }

                if (succeeded < options.quorum) {
                    CompletableFuture<Void> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new QuorumError(
                            succeeded, options.quorum, options.nodes.size(), results));
                    return failed;
                }

                return next.proceed();
            });
        };
    }

    private static CompletableFuture<NodeResult> callNode(
            String address, ExecuteRequest request, long deadlineMs) {
        CompletableFuture<NodeResult> result = new CompletableFuture<>();
        ManagedChannel channel;
        try {
            channel = ManagedChannelBuilder.forTarget(address)
                    .usePlaintext()
                    .build();
        } catch (Throwable t) {
            result.complete(new NodeResult(address, false, t, null));
            return result;
        }

        ConditionallyExecuteNodeGrpc.ConditionallyExecuteNodeStub stub =
                ConditionallyExecuteNodeGrpc.newStub(channel)
                        .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);

        try {
            stub.execute(request, new StreamObserver<>() {
                @Override
                public void onNext(ExecuteResponse value) {
                    boolean success = value.getError().isEmpty() && value.getExecuted();
                    result.complete(new NodeResult(address, success, null, value));
                }

                @Override
                public void onError(Throwable t) {
                    result.complete(new NodeResult(address, false, t, null));
                    shutdownChannel(channel);
                }

                @Override
                public void onCompleted() {
                    shutdownChannel(channel);
                }
            });
        } catch (Throwable t) {
            result.complete(new NodeResult(address, false, t, null));
            shutdownChannel(channel);
        }

        return result;
    }

    private static void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
