'use strict';

// ---------------------------------------------------------------------------
// Custom error types
// ---------------------------------------------------------------------------

class ConditionallyExecuteError extends Error {
  constructor(message) {
    super(message);
    this.name = 'ConditionallyExecuteError';
  }
}

// ---------------------------------------------------------------------------
// Named condition registry
// ---------------------------------------------------------------------------

/** @type {Map<string, () => unknown>} */
const _registry = new Map();

// ---------------------------------------------------------------------------
// Main class
// ---------------------------------------------------------------------------

/**
 * @typedef {() => void | Promise<void>} Handler
 */

/**
 * @typedef {object} ExecutionContext
 * @property {boolean}   condition  Current condition value (may be mutated by middleware).
 * @property {string}    branch     Active branch: 'onTrue' | 'onFalse'.
 * @property {Handler[]} handlers   Active handlers for this branch (may be mutated by middleware).
 * @property {Handler[]} _onTrue    All registered onTrue handlers.
 * @property {Handler[]} _onFalse   All registered onFalse handlers.
 */

/**
 * @callback Middleware
 * @param {ExecutionContext} ctx
 * @param {() => Promise<void>} next
 * @returns {Promise<void>}
 */

/**
 * ConditionallyExecute — composable conditional execution.
 *
 * Core provides: condition, onTrue, onFalse, onError, use(), execute(), executeSync().
 * Everything else (timeout, retry, dryRun, audit log, etc.) is a plugin via .use().
 *
 * @example
 * const { TimeoutPlugin, RetryPlugin } = require('./plugins');
 *
 * await new ConditionallyExecute()
 *   .use(TimeoutPlugin(5000))
 *   .use(RetryPlugin(3, { backoff: 'exponential' }))
 *   .condition(isHealthy)
 *   .onTrue(deployToProduction)
 *   .execute();
 */
class ConditionallyExecute {
  constructor() {
    /** @private @type {boolean} */
    this._condition = true;
    /** @private @type {Handler[]} */
    this._onTrue = [];
    /** @private @type {Handler[]} */
    this._onFalse = [];
    /** @private @type {Middleware[]} */
    this._middlewares = [];
    /** @private @type {((err: Error) => void | Promise<void>)|null} */
    this._errorHandler = null;
  }

  // -------------------------------------------------------------------------
  // Static API
  // -------------------------------------------------------------------------

  /**
   * Register a named condition for reuse across instances.
   * @param {string} name
   * @param {() => unknown} fn
   */
  static register(name, fn) {
    if (typeof name !== 'string') throw new TypeError(`register() expects a string name, got ${typeof name}`);
    if (typeof fn !== 'function') throw new TypeError(`register() expects a function evaluator, got ${typeof fn}`);
    _registry.set(name, fn);
  }

  /** @param {string} name */
  static unregister(name) {
    _registry.delete(name);
  }

  static clearRegistry() {
    _registry.clear();
  }

  // -------------------------------------------------------------------------
  // Builder API
  // -------------------------------------------------------------------------

  /**
   * Sets the condition. Coerced to boolean. Last call wins.
   * Accepts a named condition string registered via `ConditionallyExecute.register()`.
   * Defaults to `true` if never called.
   * @param {unknown} condition
   * @returns {this}
   */
  condition(condition) {
    if (_registry.has(condition)) {
      this._condition = Boolean(_registry.get(condition)());
    } else {
      this._condition = Boolean(condition);
    }
    return this;
  }

  /**
   * Registers a handler for the truthy branch.
   * @param {Handler} func
   * @returns {this}
   * @throws {TypeError}
   */
  onTrue(func) {
    if (typeof func !== 'function') {
      throw new TypeError(`onTrue() expects a function, got ${typeof func}`);
    }
    this._onTrue.push(func);
    return this;
  }

  /**
   * Registers a handler for the falsy branch.
   * @param {Handler} func
   * @returns {this}
   * @throws {TypeError}
   */
  onFalse(func) {
    if (typeof func !== 'function') {
      throw new TypeError(`onFalse() expects a function, got ${typeof func}`);
    }
    this._onFalse.push(func);
    return this;
  }

  /**
   * Registers an error handler. Called instead of throwing when execution fails.
   * @param {(err: Error) => void | Promise<void>} fn
   * @returns {this}
   */
  onError(fn) {
    if (typeof fn !== 'function') {
      throw new TypeError(`onError() expects a function, got ${typeof fn}`);
    }
    this._errorHandler = fn;
    return this;
  }

  /**
   * Installs a middleware. Middleware receives `(ctx, next)` and can inspect or
   * mutate `ctx.condition`, `ctx.branch`, and `ctx.handlers` before/after execution.
   * Middleware composes in registration order.
   *
   * @param {Middleware} middleware
   * @returns {this}
   * @example
   * .use(async (ctx, next) => {
   *   console.log('before:', ctx.branch);
   *   await next();
   *   console.log('after');
   * })
   */
  use(middleware) {
    if (typeof middleware !== 'function') {
      throw new TypeError(`use() expects a function middleware, got ${typeof middleware}`);
    }
    this._middlewares.push(middleware);
    return this;
  }

  // -------------------------------------------------------------------------
  // Execution
  // -------------------------------------------------------------------------

  /**
   * Executes all active-branch handlers concurrently via `Promise.all`.
   * Runs the full middleware chain first.
   * @returns {Promise<void>}
   */
  async execute() {
    /** @type {ExecutionContext} */
    const ctx = {
      condition: this._condition,
      branch: this._condition ? 'onTrue' : 'onFalse',
      handlers: this._condition ? [...this._onTrue] : [...this._onFalse],
      _onTrue: this._onTrue,
      _onFalse: this._onFalse,
    };

    const dispatch = async (i) => {
      if (i < this._middlewares.length) {
        await this._middlewares[i](ctx, () => dispatch(i + 1));
      } else {
        await Promise.all(ctx.handlers.map((fn) => fn()));
      }
    };

    try {
      await dispatch(0);
    } catch (err) {
      if (this._errorHandler) {
        await this._errorHandler(err);
        return;
      }
      throw err;
    }
  }

  /**
   * Executes all active-branch handlers synchronously, in registration order.
   * No middleware support. No Promise overhead. Use when handlers are sync
   * and performance matters.
   * @returns {void}
   */
  executeSync() {
    const handlers = this._condition ? this._onTrue : this._onFalse;
    for (let i = 0; i < handlers.length; i++) {
      handlers[i]();
    }
  }
}

ConditionallyExecute.ConditionallyExecuteError = ConditionallyExecuteError;

module.exports = ConditionallyExecute;
