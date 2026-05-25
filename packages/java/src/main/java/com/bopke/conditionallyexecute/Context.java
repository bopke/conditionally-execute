package com.bopke.conditionallyexecute;

import java.util.List;
import java.util.Objects;

/**
 * Execution context passed through the middleware chain.
 *
 * <p>This is the Java equivalent of the JavaScript {@code ExecutionContext}
 * object. Like its JS counterpart, the fields {@link #condition},
 * {@link #branch}, and {@link #handlers} are mutable so middleware can override
 * the resolved branch (e.g. {@link com.bopke.conditionallyexecute.plugins.MultiThreadedPlugin}
 * replaces the condition with the consensus result).</p>
 *
 * <p>The two snapshots {@link #onTrueHandlers()} and {@link #onFalseHandlers()}
 * are immutable — they correspond to the JS {@code _onTrue} and {@code _onFalse}
 * arrays and are used by middleware that needs to swap the active handler list
 * after re-evaluating the condition.</p>
 */
public final class Context {
    private boolean condition;
    private Branch branch;
    private List<Handler> handlers;
    private final List<Handler> onTrueHandlers;
    private final List<Handler> onFalseHandlers;

    /**
     * Create a new context.
     *
     * @param condition       resolved condition value
     * @param branch          active branch
     * @param handlers        active handler list (will be mutated through the chain)
     * @param onTrueHandlers  full list of onTrue handlers (immutable snapshot)
     * @param onFalseHandlers full list of onFalse handlers (immutable snapshot)
     */
    public Context(
            boolean condition,
            Branch branch,
            List<Handler> handlers,
            List<Handler> onTrueHandlers,
            List<Handler> onFalseHandlers) {
        this.condition = condition;
        this.branch = Objects.requireNonNull(branch, "branch");
        this.handlers = Objects.requireNonNull(handlers, "handlers");
        this.onTrueHandlers = List.copyOf(onTrueHandlers);
        this.onFalseHandlers = List.copyOf(onFalseHandlers);
    }

    /** @return the current condition value */
    public boolean condition() {
        return condition;
    }

    /**
     * Override the condition value. Middleware uses this to inject results from
     * consensus, feature flags, or other late-binding decisions.
     *
     * @param condition new value
     */
    public void setCondition(boolean condition) {
        this.condition = condition;
    }

    /** @return the active branch */
    public Branch branch() {
        return branch;
    }

    /**
     * Override the active branch.
     *
     * @param branch new branch
     */
    public void setBranch(Branch branch) {
        this.branch = Objects.requireNonNull(branch, "branch");
    }

    /** @return the active handlers list (mutable reference) */
    public List<Handler> handlers() {
        return handlers;
    }

    /**
     * Replace the active handler list. Plugins use this to wrap each handler
     * (retry, error-collection) or to swap branches mid-chain.
     *
     * @param handlers new handler list
     */
    public void setHandlers(List<Handler> handlers) {
        this.handlers = Objects.requireNonNull(handlers, "handlers");
    }

    /** @return immutable snapshot of all registered {@code onTrue} handlers */
    public List<Handler> onTrueHandlers() {
        return onTrueHandlers;
    }

    /** @return immutable snapshot of all registered {@code onFalse} handlers */
    public List<Handler> onFalseHandlers() {
        return onFalseHandlers;
    }
}
