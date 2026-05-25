'use strict';

const ConditionallyExecute = require('../');
const { ConditionallyExecuteError } = ConditionallyExecute;

// ---------------------------------------------------------------------------
// TimeoutError
// ---------------------------------------------------------------------------

class TimeoutError extends ConditionallyExecuteError {
  /**
   * @param {number} ms
   */
  constructor(ms) {
    super(`Handler execution timed out after ${ms}ms`);
    this.name = 'TimeoutError';
    this.ms = ms;
  }
}

// ---------------------------------------------------------------------------
// Plugin factory
// ---------------------------------------------------------------------------

/**
 * TimeoutPlugin — aborts handler execution if it exceeds the given duration.
 *
 * Wraps the downstream middleware chain in a `Promise.race`. If the deadline
 * fires first, throws `TimeoutError`. Compose before other middleware so the
 * timeout covers the entire remaining chain.
 *
 * @param {number} ms  Maximum allowed execution time in milliseconds.
 * @returns {import('../index').Middleware}
 *
 * @example
 * await new ConditionallyExecute()
 *   .use(TimeoutPlugin(3000))
 *   .condition(isReady)
 *   .onTrue(slowHandler)
 *   .execute();
 */
function TimeoutPlugin(ms) {
  if (typeof ms !== 'number' || ms <= 0) {
    throw new TypeError(`TimeoutPlugin: ms must be a positive number, got ${ms}`);
  }

  return async function timeoutMiddleware(ctx, next) {
    await Promise.race([
      next(),
      new Promise((_, reject) =>
        setTimeout(() => reject(new TimeoutError(ms)), ms)
      ),
    ]);
  };
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = { TimeoutPlugin, TimeoutError };
