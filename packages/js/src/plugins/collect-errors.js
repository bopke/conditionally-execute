'use strict';

/**
 * CollectErrorsPlugin — runs all handlers and aggregates failures instead of short-circuiting.
 *
 * By default, `Promise.all` stops on the first rejection. This plugin replaces each
 * handler with an error-capturing wrapper so all handlers always run. If any failed,
 * their errors are aggregated into a single `AggregateError` and thrown after all
 * handlers complete.
 *
 * @returns {import('../index').Middleware}
 *
 * @example
 * await new ConditionallyExecute()
 *   .use(CollectErrorsPlugin())
 *   .condition(true)
 *   .onTrue(handlerA)   // fails
 *   .onTrue(handlerB)   // also runs, even though handlerA failed
 *   .onTrue(handlerC)   // also runs
 *   .execute();
 * // throws AggregateError with all collected failures
 */
function CollectErrorsPlugin() {
  return async function collectErrorsMiddleware(ctx, next) {
    const errors = [];

    ctx.handlers = ctx.handlers.map((fn) => async () => {
      try {
        await fn();
      } catch (err) {
        errors.push(err);
      }
    });

    await next();

    if (errors.length > 0) {
      throw new AggregateError(errors, `${errors.length} handler(s) failed`);
    }
  };
}

module.exports = { CollectErrorsPlugin };
