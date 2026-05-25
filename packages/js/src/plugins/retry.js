'use strict';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * @param {number} attempt  Zero-based attempt index.
 * @param {'none'|'linear'|'exponential'} strategy
 * @returns {number}  Delay in milliseconds.
 */
function backoffDelay(attempt, strategy) {
  switch (strategy) {
    case 'linear':      return attempt * 100;
    case 'exponential': return Math.pow(2, attempt) * 100;
    default:            return 0;
  }
}

/**
 * Wraps a handler function with retry logic.
 * @param {() => void | Promise<void>} fn
 * @param {number} maxRetries
 * @param {'none'|'linear'|'exponential'} backoff
 * @returns {() => Promise<void>}
 */
function wrapWithRetry(fn, maxRetries, backoff) {
  return async function retryWrapper() {
    let lastError;
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await fn();
      } catch (err) {
        lastError = err;
        if (attempt < maxRetries) {
          const delay = backoffDelay(attempt, backoff);
          if (delay > 0) await new Promise((r) => setTimeout(r, delay));
        }
      }
    }
    throw lastError;
  };
}

// ---------------------------------------------------------------------------
// Plugin factory
// ---------------------------------------------------------------------------

/**
 * @typedef {object} RetryOptions
 * @property {'none'|'linear'|'exponential'} [backoff='none']  Backoff strategy between attempts.
 */

/**
 * RetryPlugin — retries each handler individually on failure.
 *
 * Wraps each handler in `ctx.handlers` with retry logic before passing
 * control downstream. Each handler is retried independently up to `n` times.
 *
 * @param {number} n           Maximum number of retries (0 = no retry, try once).
 * @param {RetryOptions} [options]
 * @returns {import('../index').Middleware}
 *
 * @example
 * await new ConditionallyExecute()
 *   .use(RetryPlugin(3, { backoff: 'exponential' }))
 *   .condition(isHealthy)
 *   .onTrue(flakyNetworkCall)
 *   .execute();
 */
function RetryPlugin(n, { backoff = 'none' } = {}) {
  if (typeof n !== 'number' || n < 0 || !Number.isInteger(n)) {
    throw new TypeError(`RetryPlugin: n must be a non-negative integer, got ${n}`);
  }
  if (!['none', 'linear', 'exponential'].includes(backoff)) {
    throw new TypeError(`RetryPlugin: backoff must be 'none', 'linear', or 'exponential', got '${backoff}'`);
  }

  return async function retryMiddleware(ctx, next) {
    ctx.handlers = ctx.handlers.map((fn) => wrapWithRetry(fn, n, backoff));
    await next();
  };
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = { RetryPlugin };
