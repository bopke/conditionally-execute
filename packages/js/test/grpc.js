'use strict';

const assert = require('assert');
const ConditionallyExecute = require('../');
const { GrpcConsensusPlugin, startGrpcNode, QuorumError } = require('../src/plugins/grpc-consensus');

// Use high ports to avoid conflicts
const PORTS = [52100, 52101, 52102];

describe('GrpcConsensusPlugin', function () {
  this.timeout(15000);

  let nodes = [];

  before(async function () {
    // Start 3 gRPC nodes, each with local handlers
    nodes = await Promise.all(
      PORTS.map((port, i) =>
        startGrpcNode(port, {
          deploy: () => { /* handler runs on node */ },
          failing: () => { throw new Error(`node ${i} handler failed`); },
        })
      )
    );
  });

  after(async function () {
    await Promise.all(nodes.map((n) => n.close()));
  });

  it('should execute handler on all nodes and pass quorum', async function () {
    let coordinatorRan = false;

    await new ConditionallyExecute()
      .use(GrpcConsensusPlugin({
        nodes: PORTS.map((p) => `localhost:${p}`),
        handlerName: 'deploy',
        quorum: 2,
      }))
      .condition(true)
      .onTrue(() => { coordinatorRan = true; })
      .execute();

    assert.equal(coordinatorRan, true);
  });

  it('should skip coordinator handler when condition is false', async function () {
    let coordinatorRan = false;

    await new ConditionallyExecute()
      .use(GrpcConsensusPlugin({
        nodes: PORTS.map((p) => `localhost:${p}`),
        handlerName: 'deploy',
        quorum: 1, // condition=false → nodes return executed=false → quorum fails
      }))
      .condition(false)
      .onTrue(() => { coordinatorRan = true; })
      .execute()
      .catch(() => {}); // expected: quorum not reached

    assert.equal(coordinatorRan, false);
  });

  it('should throw QuorumError when quorum is not reached', async function () {
    // Use a port with no running node → all RPC calls fail → quorum not reached
    await assert.rejects(
      () => new ConditionallyExecute()
        .use(GrpcConsensusPlugin({
          nodes: ['localhost:59998', 'localhost:59999'],
          handlerName: 'deploy',
          quorum: 1,
          timeout: 500,
        }))
        .condition(true)
        .onTrue(() => {})
        .execute(),
      (err) => {
        assert.ok(err instanceof QuorumError, `Expected QuorumError, got ${err.constructor.name}: ${err.message}`);
        assert.equal(err.reached, 0);
        assert.equal(err.required, 1);
        return true;
      }
    );
  });

  it('should expose node results on QuorumError', async function () {
    let caughtError = null;

    await new ConditionallyExecute()
      .use(GrpcConsensusPlugin({
        nodes: ['localhost:59997'],
        handlerName: 'deploy',
        quorum: 1,
        timeout: 300,
      }))
      .condition(true)
      .onTrue(() => {})
      .onError((err) => { caughtError = err; })
      .execute();

    assert.ok(caughtError instanceof QuorumError);
    assert.ok(Array.isArray(caughtError.nodeResults));
    assert.equal(caughtError.nodeResults.length, 1);
    assert.equal(caughtError.nodeResults[0].address, 'localhost:59997');
  });

  it('should compose with other middleware', async function () {
    const log = [];

    await new ConditionallyExecute()
      .use(async (ctx, next) => { log.push('outer-in'); await next(); log.push('outer-out'); })
      .use(GrpcConsensusPlugin({
        nodes: PORTS.slice(0, 2).map((p) => `localhost:${p}`),
        handlerName: 'deploy',
        quorum: 1,
      }))
      .use(async (ctx, next) => { log.push('inner-in'); await next(); log.push('inner-out'); })
      .condition(true)
      .onTrue(() => { log.push('coordinator'); })
      .execute();

    assert.deepEqual(log, ['outer-in', 'inner-in', 'coordinator', 'inner-out', 'outer-out']);
  });

  it('should throw on invalid options', function () {
    assert.throws(() => GrpcConsensusPlugin({ nodes: [], handlerName: 'x' }), /non-empty/);
    assert.throws(() => GrpcConsensusPlugin({ nodes: ['a'], handlerName: '' }), /handlerName/);
    assert.throws(() => GrpcConsensusPlugin({ nodes: ['a'], handlerName: 'x', quorum: 5 }), /cannot exceed/);
  });
});
