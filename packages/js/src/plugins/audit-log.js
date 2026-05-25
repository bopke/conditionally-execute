'use strict';

/**
 * @typedef {object} AuditLogOptions
 * @property {(entry: AuditLogEntry) => void} [logger=console.log]  Custom log sink.
 */

/**
 * @typedef {object} AuditLogEntry
 * @property {string}  timestamp   ISO 8601 timestamp.
 * @property {boolean} condition   Resolved condition value.
 * @property {string}  branch      Active branch ('onTrue' | 'onFalse').
 * @property {number}  handlers    Number of handlers that ran.
 * @property {number}  durationMs  Execution duration in milliseconds.
 */

/**
 * AuditLogPlugin — logs execution metadata after the handler chain completes.
 *
 * Wraps the downstream chain and emits a structured log entry after `next()`
 * resolves. The log sink defaults to `console.log` but can be replaced with
 * any function (e.g. a structured logger, metrics emitter, etc.).
 *
 * @param {AuditLogOptions} [options]
 * @returns {import('../index').Middleware}
 *
 * @example
 * await new ConditionallyExecute()
 *   .use(AuditLogPlugin())
 *   .condition(isReady)
 *   .onTrue(deploy)
 *   .execute();
 * // → [2026-05-16T...] ConditionallyExecute: condition=true branch=onTrue handlers=1 duration=4.20ms
 *
 * @example
 * // Custom logger
 * .use(AuditLogPlugin({ logger: (entry) => metrics.record('ce_execution', entry) }))
 */
function AuditLogPlugin({ logger } = {}) {
  const log = typeof logger === 'function' ? logger : console.log; // eslint-disable-line no-console

  return async function auditLogMiddleware(ctx, next) {
    const start = performance.now();
    await next();
    const durationMs = performance.now() - start;

    /** @type {AuditLogEntry} */
    const entry = {
      timestamp: new Date().toISOString(),
      condition: ctx.condition,
      branch: ctx.branch,
      handlers: ctx.handlers.length,
      durationMs,
    };

    log(
      `[${entry.timestamp}] ConditionallyExecute: ` +
      `condition=${entry.condition} branch=${entry.branch} ` +
      `handlers=${entry.handlers} duration=${entry.durationMs.toFixed(2)}ms`
    );
  };
}

module.exports = { AuditLogPlugin };
