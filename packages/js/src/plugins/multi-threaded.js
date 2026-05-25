'use strict';

/**
 * MultiThreadedPlugin — distributed consensus for your if-statements.
 *
 * Spawns N worker threads acting as independent consensus nodes. Each node
 * receives the condition value and casts a vote. The majority vote determines
 * which branch executes. Communication is via Node.js worker_threads
 * MessageChannel (in-process RPC — production-grade architecture 🫡).
 *
 * @example
 * const { MultiThreadedPlugin } = require('conditionally-execute/plugins/multi-threaded');
 *
 * await new ConditionallyExecute()
 *   .use(MultiThreadedPlugin({ nodes: 5 }))
 *   .condition(userIsAdmin)
 *   .onTrue(() => grantAccess())
 *   .onFalse(() => denyAccess())
 *   .execute();
 * // 3/5 nodes must agree before any handler runs.
 */

const { Worker } = require('worker_threads');

// ---------------------------------------------------------------------------
// Worker node source (runs in each thread)
// ---------------------------------------------------------------------------

// Each node simulates an independent evaluation. In a real distributed system,
// each node would consult its own local state store, replica, or quorum peer.
// Here: votes deterministically with optional jitter for chaos testing.
const NODE_WORKER_CODE = /* javascript */ `
const { parentPort, workerData } = require('worker_threads');

const { condition, nodeId, jitter } = workerData;

function evaluate() {
  // Deterministic evaluation — real nodes might check local state here
  return Boolean(condition);
}

const vote = evaluate();

// Simulate network RTT variance when jitter is enabled
const delay = jitter ? Math.floor(Math.random() * 50) : 0;

setTimeout(() => {
  parentPort.postMessage({ nodeId, vote });
}, delay);
`;

// ---------------------------------------------------------------------------
// Vote collection
// ---------------------------------------------------------------------------

/**
 * Spawn N worker threads and collect their votes.
 * @param {unknown} condition
 * @param {number} nodeCount
 * @param {number} timeoutMs
 * @param {boolean} jitter
 * @returns {Promise<boolean[]>}
 */
async function collectVotes(condition, nodeCount, timeoutMs, jitter) {
  return new Promise((resolve, reject) => {
    const votes = [];
    const workers = [];
    let settled = false;

    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      workers.forEach((w) => w.terminate());
      reject(new Error(`MultiThreadedPlugin: vote collection timed out after ${timeoutMs}ms`));
    }, timeoutMs);

    for (let i = 0; i < nodeCount; i++) {
      const worker = new Worker(NODE_WORKER_CODE, {
        eval: true,
        workerData: { condition, nodeId: i, jitter },
      });

      workers.push(worker);

      worker.on('message', ({ vote }) => {
        if (settled) return;
        votes.push(vote);

        if (votes.length === nodeCount) {
          settled = true;
          clearTimeout(timer);
          workers.forEach((w) => w.terminate());
          resolve(votes);
        }
      });

      worker.on('error', (err) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        workers.forEach((w) => w.terminate());
        reject(err);
      });
    }
  });
}

// ---------------------------------------------------------------------------
// Plugin factory
// ---------------------------------------------------------------------------

/**
 * @typedef {object} ConsensusOptions
 * @property {number} [nodes=3]        Number of consensus nodes. Must be odd (≥ 3).
 * @property {number} [timeout=2000]   Max ms to wait for all votes before aborting.
 * @property {boolean} [jitter=false]  Add random latency per node (chaos testing).
 * @property {boolean} [verbose=false] Log vote results to stdout.
 */

/**
 * Creates a MultiThreadedPlugin middleware for ConditionallyExecute.
 *
 * @param {ConsensusOptions} [options]
 * @returns {import('../index').Middleware}
 */
function MultiThreadedPlugin(options = {}) {
  const { nodes = 3, timeout = 2000, jitter = false, verbose = false } = options;

  if (!Number.isInteger(nodes) || nodes < 3) {
    throw new Error('MultiThreadedPlugin: nodes must be an integer ≥ 3');
  }
  if (nodes % 2 === 0) {
    throw new Error('MultiThreadedPlugin: nodes must be odd to guarantee a clear majority');
  }

  return async function consensusMiddleware(ctx, next) {
    const votes = await collectVotes(ctx.condition, nodes, timeout, jitter);

    const trueVotes = votes.filter(Boolean).length;
    const falseVotes = nodes - trueVotes;
    const consensus = trueVotes > falseVotes;

    if (verbose || process.env.CE_CONSENSUS_DEBUG) {
      // eslint-disable-next-line no-console
      console.log(
        `[MultiThreadedPlugin] ${nodes} nodes voted: ` +
        `${trueVotes} true / ${falseVotes} false → consensus=${consensus}`
      );
    }

    // Override execution context with consensus result
    ctx.condition = consensus;
    ctx.branch = consensus ? 'onTrue' : 'onFalse';
    ctx.handlers = consensus ? ctx._onTrue : ctx._onFalse;

    await next();
  };
}

module.exports = { MultiThreadedPlugin, collectVotes };
