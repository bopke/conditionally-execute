'use strict';

/**
 * DryRunPlugin — skips handler execution, logs what would have run.
 *
 * Intercepts the chain before handlers are invoked. Logs the branch name
 * and handler count to stdout, then stops — `next()` is never called.
 * Middleware registered after this plugin will not run.
 *
 * Compose last (innermost) so that upstream middleware still executes normally.
 *
 * @returns {import('../index').Middleware}
 *
 * @example
 * await new ConditionallyExecute()
 *   .use(AuditLogPlugin())   // still runs
 *   .use(DryRunPlugin())     // stops here, handlers skipped
 *   .condition(isReady)
 *   .onTrue(deployToProduction)
 *   .execute();
 */
function DryRunPlugin() {
  return async function dryRunMiddleware(ctx) {
    // eslint-disable-next-line no-console
    console.log(
      `[DryRun] ConditionallyExecute: would execute ${ctx.handlers.length} ` +
      `handler(s) on branch ${ctx.branch}`
    );
    // intentionally does not call next()
  };
}

module.exports = { DryRunPlugin };
