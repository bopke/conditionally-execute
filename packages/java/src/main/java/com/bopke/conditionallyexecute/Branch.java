package com.bopke.conditionallyexecute;

/**
 * Identifies which branch is active for a given {@link ConditionallyExecute}
 * execution.
 */
public enum Branch {
    /** Truthy branch (handlers registered via {@code onTrue}). */
    ON_TRUE,
    /** Falsy branch (handlers registered via {@code onFalse}). */
    ON_FALSE;

    /**
     * Returns the JavaScript-compatible string for this branch — {@code "onTrue"}
     * or {@code "onFalse"} — so plugins like the audit log can emit messages with
     * identical wording to the JS module.
     *
     * @return JS-compatible branch name
     */
    public String toJsString() {
        return this == ON_TRUE ? "onTrue" : "onFalse";
    }

    /**
     * Convert a boolean condition to the corresponding branch.
     *
     * @param condition the resolved condition
     * @return {@link #ON_TRUE} when {@code condition} is true, otherwise {@link #ON_FALSE}
     */
    public static Branch of(boolean condition) {
        return condition ? ON_TRUE : ON_FALSE;
    }
}
