package com.bopke.conditionallyexecute.plugins;

import com.bopke.conditionallyexecute.ConditionallyExecuteError;

import java.util.List;

/**
 * Thrown by {@link GrpcConsensusPlugin} when the number of successful node
 * responses is below the configured quorum. Message format matches the JS
 * module: {@code "Quorum not reached: {reached}/{total} nodes succeeded (required {required})"}.
 */
public class QuorumError extends ConditionallyExecuteError {
    private static final long serialVersionUID = 1L;

    private final int reached;
    private final int required;
    private final int total;
    private final List<NodeResult> nodeResults;

    /**
     * @param reached     number of nodes that succeeded
     * @param required    the required quorum
     * @param total       total nodes contacted
     * @param nodeResults per-node detail
     */
    public QuorumError(int reached, int required, int total, List<NodeResult> nodeResults) {
        super(String.format(
                "Quorum not reached: %d/%d nodes succeeded (required %d)",
                reached, total, required));
        this.reached = reached;
        this.required = required;
        this.total = total;
        this.nodeResults = List.copyOf(nodeResults);
    }

    /** @return number of nodes that returned success */
    public int reached() {
        return reached;
    }

    /** @return the required quorum (minimum successes) */
    public int required() {
        return required;
    }

    /** @return total nodes the coordinator tried to contact */
    public int total() {
        return total;
    }

    /** @return immutable per-node result list */
    public List<NodeResult> nodeResults() {
        return nodeResults;
    }
}
