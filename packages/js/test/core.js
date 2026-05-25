'use strict';

const assert = require('assert');
const ConditionallyExecute = require('../');
const { TimeoutPlugin, TimeoutError } = require('../src/plugins/timeout');
const { RetryPlugin }                 = require('../src/plugins/retry');
const { DryRunPlugin }                = require('../src/plugins/dry-run');
const { AuditLogPlugin }              = require('../src/plugins/audit-log');
const { CollectErrorsPlugin }         = require('../src/plugins/collect-errors');

// ---------------------------------------------------------------------------
// Basic functionality
// ---------------------------------------------------------------------------

describe('basic functionality', function () {
  it('should execute onFalse when condition is falsy', async function () {
    let wasOnTrueExecuted = false;
    let wasOnFalseExecuted = false;

    await new ConditionallyExecute()
      .onTrue(() => { wasOnTrueExecuted = true; })
      .onFalse(() => { wasOnFalseExecuted = true; })
      .condition(1 === 2)
      .execute();

    assert.equal(wasOnFalseExecuted, true);
    assert.equal(wasOnTrueExecuted, false);
  });

  it('should execute onTrue when condition is truthy', async function () {
    let wasOnTrueExecuted = false;
    let wasOnFalseExecuted = false;

    await new ConditionallyExecute()
      .onTrue(() => { wasOnTrueExecuted = true; })
      .onFalse(() => { wasOnFalseExecuted = true; })
      .condition(1 === 1)
      .execute();

    assert.equal(wasOnFalseExecuted, false);
    assert.equal(wasOnTrueExecuted, true);
  });

  it('should execute onTrue when no condition is set (default true)', async function () {
    let wasOnTrueExecuted = false;
    let wasOnFalseExecuted = false;

    await new ConditionallyExecute()
      .onTrue(() => { wasOnTrueExecuted = true; })
      .onFalse(() => { wasOnFalseExecuted = true; })
      .execute();

    assert.equal(wasOnFalseExecuted, false);
    assert.equal(wasOnTrueExecuted, true);
  });
});

// ---------------------------------------------------------------------------
// Extended functionality
// ---------------------------------------------------------------------------

describe('extended functionality', function () {
  it('should execute all onFalse functions when condition is falsy', async function () {
    let wasOnTrueExecuted = false;
    let wasOnFalseExecuted = false;
    let wasSecondOnFalseExecuted = false;

    await new ConditionallyExecute()
      .onTrue(() => { wasOnTrueExecuted = true; })
      .onFalse(() => { wasOnFalseExecuted = true; })
      .onFalse(() => { wasSecondOnFalseExecuted = true; })
      .condition(1 === 2)
      .execute();

    assert.equal(wasOnFalseExecuted, true);
    assert.equal(wasSecondOnFalseExecuted, true);
    assert.equal(wasOnTrueExecuted, false);
  });

  it('should execute all onTrue functions when condition is truthy', async function () {
    let wasOnTrueExecuted = false;
    let wasSecondOnTrueExecuted = false;
    let wasOnFalseExecuted = false;

    await new ConditionallyExecute()
      .onTrue(() => { wasOnTrueExecuted = true; })
      .onTrue(() => { wasSecondOnTrueExecuted = true; })
      .onFalse(() => { wasOnFalseExecuted = true; })
      .execute();

    assert.equal(wasOnFalseExecuted, false);
    assert.equal(wasOnTrueExecuted, true);
    assert.equal(wasSecondOnTrueExecuted, true);
  });

  it('should execute all onTrue functions when no condition is set', async function () {
    let wasOnTrueExecuted = false;
    let wasSecondOnTrueExecuted = false;
    let wasOnFalseExecuted = false;

    await new ConditionallyExecute()
      .onTrue(() => { wasOnTrueExecuted = true; })
      .onTrue(() => { wasSecondOnTrueExecuted = true; })
      .onFalse(() => { wasOnFalseExecuted = true; })
      .execute();

    assert.equal(wasOnFalseExecuted, false);
    assert.equal(wasOnTrueExecuted, true);
    assert.equal(wasSecondOnTrueExecuted, true);
  });
});

// ---------------------------------------------------------------------------
// condition() semantics
// ---------------------------------------------------------------------------

describe('condition() semantics', function () {
  it('should coerce non-boolean truthy values to true', async function () {
    let branch = null;

    await new ConditionallyExecute()
      .condition('non-empty string')
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();

    assert.equal(branch, 'true');
  });

  it('should coerce non-boolean falsy values to false', async function () {
    for (const falsy of [0, '', null, undefined, NaN]) {
      let branch = null;
      await new ConditionallyExecute()
        .condition(falsy)
        .onTrue(() => { branch = 'true'; })
        .onFalse(() => { branch = 'false'; })
        .execute();
      assert.equal(branch, 'false', `Expected false for condition(${String(falsy)})`);
    }
  });

  it('should use last condition() call when called multiple times', async function () {
    let branch = null;

    await new ConditionallyExecute()
      .condition(false)
      .condition(true)   // last call wins
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();

    assert.equal(branch, 'true');
  });
});

// ---------------------------------------------------------------------------
// Async handlers
// ---------------------------------------------------------------------------

describe('async handlers', function () {
  it('should await async onTrue handlers', async function () {
    let result = false;

    await new ConditionallyExecute()
      .condition(true)
      .onTrue(async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        result = true;
      })
      .execute();

    assert.equal(result, true);
  });

  it('should await async onFalse handlers', async function () {
    let result = false;

    await new ConditionallyExecute()
      .condition(false)
      .onFalse(async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        result = true;
      })
      .execute();

    assert.equal(result, true);
  });

  it('should run multiple async handlers concurrently', async function () {
    const order = [];

    await new ConditionallyExecute()
      .condition(true)
      .onTrue(async () => {
        await new Promise((resolve) => setTimeout(resolve, 20));
        order.push('slow');
      })
      .onTrue(async () => {
        await new Promise((resolve) => setTimeout(resolve, 5));
        order.push('fast');
      })
      .execute();

    assert.equal(order.length, 2);
    assert.ok(order.includes('slow'));
    assert.ok(order.includes('fast'));
  });

  it('should propagate rejections from async handlers', async function () {
    await assert.rejects(
      () => new ConditionallyExecute()
        .condition(true)
        .onTrue(async () => { throw new Error('boom'); })
        .execute(),
      /boom/
    );
  });
});

// ---------------------------------------------------------------------------
// executeSync()
// ---------------------------------------------------------------------------

describe('executeSync()', function () {
  it('should execute onTrue synchronously when condition is true', function () {
    let called = false;
    new ConditionallyExecute()
      .condition(true)
      .onTrue(() => { called = true; })
      .executeSync();
    assert.equal(called, true);
  });

  it('should execute onFalse synchronously when condition is false', function () {
    let called = false;
    new ConditionallyExecute()
      .condition(false)
      .onFalse(() => { called = true; })
      .executeSync();
    assert.equal(called, true);
  });

  it('should not execute onFalse when condition is true (sync)', function () {
    let called = false;
    new ConditionallyExecute()
      .condition(true)
      .onTrue(() => {})
      .onFalse(() => { called = true; })
      .executeSync();
    assert.equal(called, false);
  });

  it('should execute multiple handlers in registration order (sync)', function () {
    const order = [];
    new ConditionallyExecute()
      .condition(true)
      .onTrue(() => { order.push(1); })
      .onTrue(() => { order.push(2); })
      .onTrue(() => { order.push(3); })
      .executeSync();
    assert.deepEqual(order, [1, 2, 3]);
  });
});

// ---------------------------------------------------------------------------
// Input validation
// ---------------------------------------------------------------------------

describe('input validation', function () {
  it('should throw TypeError when onTrue receives a non-function', function () {
    assert.throws(
      () => new ConditionallyExecute().onTrue('not a function'),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /onTrue/); return true; }
    );
  });

  it('should throw TypeError when onFalse receives a non-function', function () {
    assert.throws(
      () => new ConditionallyExecute().onFalse(42),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /onFalse/); return true; }
    );
  });

  it('should throw TypeError for null passed to onTrue', function () {
    assert.throws(
      () => new ConditionallyExecute().onTrue(null),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /onTrue/); return true; }
    );
  });

  it('should throw TypeError when use() receives a non-function', function () {
    assert.throws(
      () => new ConditionallyExecute().use('not a middleware'),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /use/); return true; }
    );
  });

  it('should throw TypeError when onError() receives a non-function', function () {
    assert.throws(
      () => new ConditionallyExecute().onError(123),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /onError/); return true; }
    );
  });
});

// ---------------------------------------------------------------------------
// TimeoutPlugin
// ---------------------------------------------------------------------------

describe('TimeoutPlugin', function () {
  it('should throw TimeoutError when handler exceeds timeout', async function () {
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(TimeoutPlugin(50))
        .condition(true)
        .onTrue(async () => new Promise((r) => setTimeout(r, 200)))
        .execute(),
      (err) => {
        assert.ok(err instanceof TimeoutError);
        assert.equal(err.name, 'TimeoutError');
        assert.match(err.message, /50ms/);
        return true;
      }
    );
  });

  it('should not throw when handler completes within timeout', async function () {
    let ran = false;
    await new ConditionallyExecute()
      .use(TimeoutPlugin(500))
      .condition(true)
      .onTrue(async () => {
        await new Promise((r) => setTimeout(r, 10));
        ran = true;
      })
      .execute();
    assert.equal(ran, true);
  });

  it('should throw TypeError for invalid ms argument', function () {
    assert.throws(() => TimeoutPlugin(0), /positive number/);
    assert.throws(() => TimeoutPlugin(-1), /positive number/);
    assert.throws(() => TimeoutPlugin('500'), /positive number/);
  });
});

// ---------------------------------------------------------------------------
// RetryPlugin
// ---------------------------------------------------------------------------

describe('RetryPlugin', function () {
  it('should retry failing handlers up to n times', async function () {
    let attempts = 0;
    await new ConditionallyExecute()
      .use(RetryPlugin(2))
      .condition(true)
      .onTrue(async () => {
        attempts++;
        if (attempts < 3) throw new Error('transient failure');
      })
      .execute();
    assert.equal(attempts, 3); // 1 initial + 2 retries — not 2, not 4
  });

  it('should throw after exhausting retries', async function () {
    let attempts = 0;
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(RetryPlugin(1))
        .condition(true)
        .onTrue(() => { attempts++; throw new Error('always fails'); })
        .execute(),
      /always fails/
    );
    assert.equal(attempts, 2); // exactly 2: 1 initial + 1 retry (not 1, not 3)
  });

  it('should not retry when n is 0', async function () {
    let attempts = 0;
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(RetryPlugin(0))
        .condition(true)
        .onTrue(() => { attempts++; throw new Error('fail'); })
        .execute(),
      /fail/
    );
    assert.equal(attempts, 1); // exactly 1 — no retries
  });

  it('should apply exponential backoff between retries', async function () {
    let attempts = 0;
    const times = [];
    await new ConditionallyExecute()
      .use(RetryPlugin(2, { backoff: 'exponential' }))
      .condition(true)
      .onTrue(async () => {
        times.push(Date.now());
        attempts++;
        if (attempts < 3) throw new Error('transient');
      })
      .execute();
    assert.equal(attempts, 3);
    // exponential: attempt 0→1: 100ms, attempt 1→2: 200ms
    assert.ok(times[1] - times[0] >= 90, `backoff too short: ${times[1] - times[0]}ms`);
    assert.ok(times[2] - times[1] >= 180, `backoff too short: ${times[2] - times[1]}ms`);
  });

  it('should apply linear backoff between retries', async function () {
    let attempts = 0;
    const times = [];
    await new ConditionallyExecute()
      .use(RetryPlugin(2, { backoff: 'linear' }))
      .condition(true)
      .onTrue(async () => {
        times.push(Date.now());
        attempts++;
        if (attempts < 3) throw new Error('transient');
      })
      .execute();
    assert.equal(attempts, 3);
    // linear: attempt 0→1: 0ms, attempt 1→2: 100ms
    assert.ok(times[2] - times[1] >= 90, `linear backoff too short: ${times[2] - times[1]}ms`);
  });

  it('should throw TypeError for invalid arguments', function () {
    assert.throws(() => RetryPlugin(-1), /non-negative integer/);
    assert.throws(() => RetryPlugin(1.5), /non-negative integer/);
    assert.throws(() => RetryPlugin(1, { backoff: 'random' }), /backoff/);
  });
});

// ---------------------------------------------------------------------------
// DryRunPlugin
// ---------------------------------------------------------------------------

describe('DryRunPlugin', function () {
  it('should not execute handlers', async function () {
    let called = false;
    await new ConditionallyExecute()
      .use(DryRunPlugin())
      .condition(true)
      .onTrue(() => { called = true; })
      .execute();
    assert.equal(called, false);
  });

  it('should log what would have run', async function () {
    const logs = [];
    const origLog = console.log;
    console.log = (...args) => logs.push(args.join(' '));
    try {
      await new ConditionallyExecute()
        .use(DryRunPlugin())
        .condition(false)
        .onFalse(() => {})
        .onFalse(() => {})
        .execute();
    } finally {
      console.log = origLog;
    }
    assert.equal(logs.length, 1);
    assert.match(logs[0], /DryRun/);
    assert.match(logs[0], /2/);
    assert.match(logs[0], /onFalse/);
  });
});

// ---------------------------------------------------------------------------
// onError()
// ---------------------------------------------------------------------------

describe('onError()', function () {
  it('should call onError instead of throwing when handler fails', async function () {
    let caughtError = null;
    await new ConditionallyExecute()
      .condition(true)
      .onTrue(() => { throw new Error('handler blew up'); })
      .onError((err) => { caughtError = err; })
      .execute();
    assert.ok(caughtError instanceof Error);
    assert.match(caughtError.message, /handler blew up/);
  });

  it('ConditionallyExecuteError should have correct name property', function () {
    const err = new ConditionallyExecute.ConditionallyExecuteError('test');
    assert.equal(err.name, 'ConditionallyExecuteError');
    assert.equal(err.message, 'test');
    assert.ok(err instanceof Error);
  });

  it('should call onError with TimeoutError on timeout', async function () {
    let caughtError = null;
    await new ConditionallyExecute()
      .use(TimeoutPlugin(30))
      .condition(true)
      .onTrue(async () => new Promise((r) => setTimeout(r, 200)))
      .onError((err) => { caughtError = err; })
      .execute();
    assert.ok(caughtError instanceof TimeoutError);
  });
});

// ---------------------------------------------------------------------------
// Middleware (.use())
// ---------------------------------------------------------------------------

describe('middleware (.use())', function () {
  it('should call middleware before handler execution', async function () {
    const log = [];

    await new ConditionallyExecute()
      .use(async (ctx, next) => {
        log.push('before');
        await next();
        log.push('after');
      })
      .condition(true)
      .onTrue(() => { log.push('handler'); })
      .execute();

    assert.deepEqual(log, ['before', 'handler', 'after']);
  });

  it('should expose correct branch in ctx', async function () {
    let capturedCtx = null;
    await new ConditionallyExecute()
      .use(async (ctx, next) => { capturedCtx = ctx; await next(); })
      .condition(false)
      .onTrue(() => {})
      .onFalse(() => {})
      .execute();
    assert.equal(capturedCtx.branch, 'onFalse');
    assert.equal(capturedCtx.condition, false);
  });

  it('should allow middleware to override condition', async function () {
    let branch = null;

    const overrideToFalse = async (ctx, next) => {
      ctx.condition = false;
      ctx.branch = 'onFalse';
      ctx.handlers = ctx._onFalse;
      await next();
    };

    await new ConditionallyExecute()
      .use(overrideToFalse)
      .condition(true)
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();

    assert.equal(branch, 'false');
  });

  it('should compose multiple middlewares in order', async function () {
    const log = [];

    await new ConditionallyExecute()
      .use(async (ctx, next) => { log.push('mw1-in'); await next(); log.push('mw1-out'); })
      .use(async (ctx, next) => { log.push('mw2-in'); await next(); log.push('mw2-out'); })
      .condition(true)
      .onTrue(() => { log.push('handler'); })
      .execute();

    assert.deepEqual(log, ['mw1-in', 'mw2-in', 'handler', 'mw2-out', 'mw1-out']);
  });
});

// ---------------------------------------------------------------------------
// AuditLogPlugin
// ---------------------------------------------------------------------------

describe('AuditLogPlugin', function () {
  it('should log to the provided logger after execution', async function () {
    const logs = [];

    await new ConditionallyExecute()
      .use(AuditLogPlugin({ logger: (msg) => logs.push(msg) }))
      .condition(true)
      .onTrue(() => {})
      .execute();

    assert.equal(logs.length, 1);
    assert.match(logs[0], /ConditionallyExecute/);
    assert.match(logs[0], /condition=true/);
    assert.match(logs[0], /branch=onTrue/);
    assert.match(logs[0], /handlers=1/);
    assert.match(logs[0], /duration=/);
  });

  it('should not log when plugin is not used', async function () {
    const logs = [];
    const original = console.log;
    console.log = (...args) => logs.push(args.join(' '));

    try {
      await new ConditionallyExecute()
        .condition(true)
        .onTrue(() => {})
        .execute();
    } finally {
      console.log = original;
    }

    assert.equal(logs.length, 0);
  });
});

// ---------------------------------------------------------------------------
// Named condition registry
// ---------------------------------------------------------------------------

describe('named condition registry', function () {
  afterEach(function () {
    ConditionallyExecute.clearRegistry();
  });

  it('should evaluate a registered condition by name', async function () {
    ConditionallyExecute.register('alwaysTrue', () => true);

    let branch = null;
    await new ConditionallyExecute()
      .condition('alwaysTrue')
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();

    assert.equal(branch, 'true');
  });

  it('should NOT use registry for non-string condition values', async function () {
    // Register string keys whose string representation matches truthy/falsy values.
    // If typeof check is removed, passing a non-string would still miss the Map key
    // (Map is type-strict). This test verifies boolean false is coerced, not registry-looked-up.
    ConditionallyExecute.register('false', () => true); // would flip branch if used
    let branch = null;
    await new ConditionallyExecute()
      .condition(false) // boolean, not string — must NOT hit registry
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();
    assert.equal(branch, 'false'); // Boolean(false) = false, registry not consulted
  });

  it('should support dynamic registered conditions', async function () {
    let value = false;
    ConditionallyExecute.register('dynamic', () => value);

    let branch = null;

    await new ConditionallyExecute()
      .condition('dynamic')
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();
    assert.equal(branch, 'false');

    value = true;
    await new ConditionallyExecute()
      .condition('dynamic')
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();
    assert.equal(branch, 'true');
  });

  it('should clear registry via clearRegistry()', async function () {
    // register a condition that returns FALSE — so if clear() did nothing, branch would be 'false'
    ConditionallyExecute.register('myCondition', () => false);
    ConditionallyExecute.clearRegistry();

    let branch = null;
    await new ConditionallyExecute()
      .condition('myCondition') // not in registry after clear → Boolean('myCondition') = true
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();
    assert.equal(branch, 'true'); // proves clear() actually removed the entry
  });

  it('should remove a single entry via unregister()', async function () {
    ConditionallyExecute.register('gone', () => false);
    ConditionallyExecute.register('stays', () => false);
    ConditionallyExecute.unregister('gone');

    // 'gone' is no longer in registry → Boolean('gone') = true
    let branch = null;
    await new ConditionallyExecute()
      .condition('gone')
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();
    assert.equal(branch, 'true');

    // 'stays' still resolves via registry → false
    await new ConditionallyExecute()
      .condition('stays')
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();
    assert.equal(branch, 'false');
  });

  it('should throw TypeError for invalid register() arguments with useful messages', function () {
    assert.throws(
      () => ConditionallyExecute.register(123, () => {}),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /string/); return true; }
    );
    assert.throws(
      () => ConditionallyExecute.register('name', 'not-a-fn'),
      (err) => { assert.ok(err instanceof TypeError); assert.match(err.message, /function/); return true; }
    );
  });
});

// ---------------------------------------------------------------------------
// CollectErrorsPlugin
// ---------------------------------------------------------------------------

describe('CollectErrorsPlugin', function () {
  it('should collect all handler errors into AggregateError', async function () {
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(CollectErrorsPlugin())
        .condition(true)
        .onTrue(() => { throw new Error('err1'); })
        .onTrue(() => { throw new Error('err2'); })
        .execute(),
      (err) => {
        assert.ok(err instanceof AggregateError);
        assert.equal(err.errors.length, 2);
        assert.match(err.errors[0].message, /err1/);
        assert.match(err.errors[1].message, /err2/);
        assert.match(err.message, /2 handler/);
        return true;
      }
    );
  });

  it('should not throw when all handlers succeed', async function () {
    let count = 0;
    await new ConditionallyExecute()
      .use(CollectErrorsPlugin())
      .condition(true)
      .onTrue(() => { count++; })
      .onTrue(() => { count++; })
      .execute();
    assert.equal(count, 2);
  });

  it('should not include successful handlers in error list', async function () {
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(CollectErrorsPlugin())
        .condition(true)
        .onTrue(() => { /* succeeds */ })
        .onTrue(() => { throw new Error('only-this-fails'); })
        .execute(),
      (err) => {
        assert.ok(err instanceof AggregateError);
        assert.equal(err.errors.length, 1);
        assert.match(err.errors[0].message, /only-this-fails/);
        return true;
      }
    );
  });
});
