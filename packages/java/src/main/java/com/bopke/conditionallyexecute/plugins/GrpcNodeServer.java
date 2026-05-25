package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.Handler;
import com.bopke.conditionallyexecute.proto.ConditionallyExecuteNodeGrpc;
import com.bopke.conditionallyexecute.proto.ExecuteRequest;
import com.bopke.conditionallyexecute.proto.ExecuteResponse;
import com.bopke.conditionallyexecute.proto.HealthRequest;
import com.bopke.conditionallyexecute.proto.HealthResponse;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Reference gRPC server implementing the {@code ConditionallyExecuteNode}
 * service from {@code conditionally_execute.proto}. Mirrors the JS
 * {@code startGrpcNode()} helper — primarily used by tests, but also fine for
 * production node processes.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If the requested handler isn't registered → {@code executed=false}, error
 *       contains "Handler '{name}' not registered on {nodeId}".</li>
 *   <li>If {@code condition=false} → {@code executed=false}, branch={@code "onFalse"},
 *       empty error (matches the JS node behavior of skipping execution).</li>
 *   <li>If {@code condition=true} and the handler exists → it runs; the response
 *       reports {@code executed=true}, branch={@code "onTrue"}, duration.
 *       Thrown exceptions surface as {@code error} (still {@code executed=false}).</li>
 * </ul>
 */
public final class GrpcNodeServer {

    /** Builder for {@link GrpcNodeServer}. */
    public static final class Builder {
        private final int port;
        private final Map<String, Handler> handlers;
        private String nodeId;

        Builder(int port, Map<String, Handler> handlers) {
            this.port = port;
            this.handlers = Map.copyOf(handlers);
        }

        /**
         * @param nodeId override the default {@code "node-{port}"} id
         * @return this
         */
        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        /**
         * Start the server and bind to the configured port.
         *
         * @return future that completes with the running server
         */
        public CompletableFuture<GrpcNodeServer> startAsync() {
            CompletableFuture<GrpcNodeServer> result = new CompletableFuture<>();
            try {
                String id = nodeId != null ? nodeId : "node-" + port;
                GrpcNodeServer server = new GrpcNodeServer(port, id, handlers);
                server.start();
                result.complete(server);
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
            return result;
        }

        /**
         * Start the server synchronously.
         *
         * @return the running server
         * @throws IOException on bind failure
         */
        public GrpcNodeServer start() throws IOException {
            String id = nodeId != null ? nodeId : "node-" + port;
            GrpcNodeServer server = new GrpcNodeServer(port, id, handlers);
            server.start();
            return server;
        }
    }

    /**
     * Create a builder for a node bound to {@code port} with the given handler
     * map (name → handler).
     *
     * @param port     listen port
     * @param handlers name-to-handler map (defensively copied)
     * @return the builder
     */
    public static Builder builder(int port, Map<String, Handler> handlers) {
        return new Builder(port, Objects.requireNonNull(handlers, "handlers"));
    }

    /**
     * Convenience: start a node directly (matches the JS
     * {@code startGrpcNode(port, handlers)} call signature).
     *
     * @param port     listen port
     * @param handlers name-to-handler map
     * @return future of the running server
     */
    public static CompletableFuture<GrpcNodeServer> startAsync(int port, Map<String, Handler> handlers) {
        return builder(port, handlers).startAsync();
    }

    private final int port;
    private final String nodeId;
    private final Map<String, Handler> handlers;
    private Server server;

    private GrpcNodeServer(int port, String nodeId, Map<String, Handler> handlers) {
        this.port = port;
        this.nodeId = nodeId;
        this.handlers = handlers;
    }

    private void start() throws IOException {
        this.server = ServerBuilder.forPort(port)
                .addService(new NodeService())
                .build()
                .start();
    }

    /** @return the node identifier (defaults to {@code "node-{port}"}) */
    public String nodeId() {
        return nodeId;
    }

    /** @return the listen port */
    public int port() {
        return port;
    }

    /** @return immutable handler map */
    public Map<String, Handler> handlers() {
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Initiate graceful shutdown and wait for termination.
     *
     * @return future that completes when the server has stopped
     */
    public CompletableFuture<Void> close() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (server == null) {
            done.complete(null);
            return done;
        }
        Thread.startVirtualThread(() -> {
            try {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                done.complete(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    private final class NodeService extends ConditionallyExecuteNodeGrpc.ConditionallyExecuteNodeImplBase {

        @Override
        public void execute(ExecuteRequest request, StreamObserver<ExecuteResponse> obs) {
            String handlerName = request.getHandlerName();
            boolean condition = request.getCondition();
            Handler handler = handlers.get(handlerName);

            if (handler == null) {
                obs.onNext(ExecuteResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setExecuted(false)
                        .setBranch(condition ? "onTrue" : "onFalse")
                        .setDurationMs(0.0)
                        .setError("Handler '" + handlerName + "' not registered on " + nodeId)
                        .build());
                obs.onCompleted();
                return;
            }

            if (!condition) {
                obs.onNext(ExecuteResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setExecuted(false)
                        .setBranch("onFalse")
                        .setDurationMs(0.0)
                        .setError("")
                        .build());
                obs.onCompleted();
                return;
            }

            long start = System.nanoTime();
            CompletableFuture<Void> exec;
            try {
                exec = handler.run();
                if (exec == null) {
                    exec = CompletableFuture.completedFuture(null);
                }
            } catch (Throwable t) {
                exec = new CompletableFuture<>();
                exec.completeExceptionally(t);
            }

            exec.whenComplete((v, err) -> {
                double durationMs = (System.nanoTime() - start) / 1_000_000.0;
                ExecuteResponse.Builder b = ExecuteResponse.newBuilder()
                        .setNodeId(nodeId)
                        .setBranch("onTrue")
                        .setDurationMs(durationMs);
                if (err == null) {
                    b.setExecuted(true).setError("");
                } else {
                    Throwable cause = err;
                    while (cause.getCause() != null && cause instanceof java.util.concurrent.CompletionException) {
                        cause = cause.getCause();
                    }
                    String msg = cause.getMessage();
                    b.setExecuted(false).setError(msg != null ? msg : cause.toString());
                }
                obs.onNext(b.build());
                obs.onCompleted();
            });
        }

        @Override
        public void health(HealthRequest request, StreamObserver<HealthResponse> obs) {
            HealthResponse.Builder b = HealthResponse.newBuilder()
                    .setNodeId(nodeId)
                    .setStatus("ok");
            for (String name : handlers.keySet()) {
                b.addHandlers(name);
            }
            obs.onNext(b.build());
            obs.onCompleted();
        }
    }
}
