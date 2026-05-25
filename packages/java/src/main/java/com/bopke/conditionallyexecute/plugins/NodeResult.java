package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.proto.ExecuteResponse;

/**
 * Result of an Execute RPC call to a single gRPC node. Exposed on
 * {@link QuorumError#nodeResults()} so callers can inspect what each node did
 * after a failed consensus.
 *
 * @param address  the {@code host:port} the call targeted
 * @param success  whether the node executed cleanly ({@code executed=true} and empty error)
 * @param rpcError the transport-level failure, or {@code null} on success
 * @param response the protobuf response, or {@code null} when {@code rpcError != null}
 */
public record NodeResult(
        String address,
        boolean success,
        Throwable rpcError,
        ExecuteResponse response) {
}
