'use strict';

/**
 * Consensus plugin tests.
 * Run standalone: node --experimental-worker test-consensus.js
 * Or via parallel suite: node test-parallel.js
 */

const assert = require('assert');
const ConditionallyExecute = require('../');
const { MultiThreadedPlugin } = require('../src/plugins/multi-threaded');

describe('MultiThreadedPlugin', function () {
  this.timeout(10000); // consensus involves worker threads

  it('should execute onTrue when majority votes true', async function () {
    let branch = null;

    await new ConditionallyExecute()
      .use(MultiThreadedPlugin({ nodes: 3 }))
      .condition(true)
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();

    assert.equal(branch, 'true');
  });

  it('should execute onFalse when majority votes false', async function () {
    let branch = null;

    await new ConditionallyExecute()
      .use(MultiThreadedPlugin({ nodes: 3 }))
      .condition(false)
      .onTrue(() => { branch = 'true'; })
      .onFalse(() => { branch = 'false'; })
      .execute();

    assert.equal(branch, 'false');
  });

  it('should work with 5 nodes', async function () {
    let called = false;

    await new ConditionallyExecute()
      .use(MultiThreadedPlugin({ nodes: 5 }))
      .condition(true)
      .onTrue(() => { called = true; })
      .execute();

    assert.equal(called, true);
  });

  it('should work with jitter enabled (chaos mode)', async function () {
    let called = false;

    await new ConditionallyExecute()
      .use(MultiThreadedPlugin({ nodes: 3, jitter: true }))
      .condition(true)
      .onTrue(() => { called = true; })
      .execute();

    assert.equal(called, true);
  });

  it('should compose with other middleware', async function () {
    const log = [];

    await new ConditionallyExecute()
      .use(async (ctx, next) => { log.push('outer-in'); await next(); log.push('outer-out'); })
      .use(MultiThreadedPlugin({ nodes: 3 }))
      .use(async (ctx, next) => { log.push('inner-in'); await next(); log.push('inner-out'); })
      .condition(true)
      .onTrue(() => { log.push('handler'); })
      .execute();

    assert.deepEqual(log, ['outer-in', 'inner-in', 'handler', 'inner-out', 'outer-out']);
  });

  it('should throw on even node count', function () {
    assert.throws(
      () => MultiThreadedPlugin({ nodes: 4 }),
      /odd/
    );
  });

  it('should throw on node count < 3', function () {
    assert.throws(
      () => MultiThreadedPlugin({ nodes: 1 }),
      /≥ 3/
    );
  });

  it('should timeout when workers are too slow', async function () {
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(MultiThreadedPlugin({ nodes: 3, timeout: 1 })) // 1ms — impossible
        .condition(true)
        .onTrue(() => {})
        .execute(),
      /timed out/
    );
  });
});
