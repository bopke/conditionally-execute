'use strict';

/**
 * GrpcConsensusPlugin — enterprise-grade distributed execution with quorum agreement.
 *
 * Architecture:
 *   - Each "node" is a gRPC server running ConditionallyExecuteNode service.
 *   - The coordinator (your app) calls Execute() on ALL nodes simultaneously.
 *   - Each node runs its locally-registered handler and reports success/failure.
 *   - If fewer than `quorum` nodes confirm successful execution → QuorumError.
 *   - All communication is via Protobuf over HTTP/2 (gRPC).
 *
 * Why gRPC?
 *   - Strongly typed via Protobuf schema
 *   - HTTP/2 multiplexing — all nodes called in a single round-trip
 *   - Industry standard (used by Kubernetes, etcd, Consul)
 *   - Appropriate for an if-statement replacement library
 *
 * @example
 * const { GrpcConsensusPlugin, startGrpcNode } = require('conditionally-execute/plugins/grpc-consensus');
 *
 * // Start nodes (in separate processes in production; here for demo):
 * const n1 = await startGrpcNode(50051, { deploy: () => console.log('node1: deploying') });
 * const n2 = await startGrpcNode(50052, { deploy: () => console.log('node2: deploying') });
 * const n3 = await startGrpcNode(50053, { deploy: () => console.log('node3: deploying') });
 *
 * await new ConditionallyExecute()
 *   .use(GrpcConsensusPlugin({
 *     nodes: ['localhost:50051', 'localhost:50052', 'localhost:50053'],
 *     handlerName: 'deploy',
 *     quorum: 2,       // at least 2/3 must succeed
 *   }))
 *   .condition(isReadyForDeploy)
 *   .onTrue(() => console.log('coordinator: quorum reached, deploy confirmed'))
 *   .execute();
 *
 * await Promise.all([n1.close(), n2.close(), n3.close()]);
 */

const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');
const { randomUUID } = require('crypto');

// ---------------------------------------------------------------------------
// Load proto definition
// ---------------------------------------------------------------------------

// Proto lives at monorepo root (../../../../proto/) — shared with the Java module.
const PROTO_PATH = path.join(__dirname, '..', '..', '..', '..', 'proto', 'conditionally_execute.proto');

const packageDef = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});

const protoDescriptor = grpc.loadPackageDefinition(packageDef);
const { ConditionallyExecuteNode } = protoDescriptor.conditionally_execute;

// ---------------------------------------------------------------------------
// Custom error types
// ---------------------------------------------------------------------------

class QuorumError extends Error {
  /**
   * @param {number} reached
   * @param {number} required
   * @param {number} total
   * @param {import('./grpc-consensus').NodeResult[]} results
   */
  constructor(reached, required, total, results) {
    super(`Quorum not reached: ${reached}/${total} nodes succeeded (required ${required})`);
    this.name = 'QuorumError';
    this.reached = reached;
    this.required = required;
    this.total = total;
    this.nodeResults = results;
  }
}

// ---------------------------------------------------------------------------
// Node server
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} NodeServer
 * @property {string} nodeId
 * @property {number} port
 * @property {() => void} close
 */

/**
 * Start a gRPC ConditionallyExecuteNode server on the given port.
 *
 * @param {number} port
 * @param {Record<string, () => void | Promise<void>>} handlers  Named handler map.
 * @param {object} [options]
 * @param {string} [options.nodeId]  Custom node ID (defaults to `node-${port}`)
 * @returns {Promise<NodeServer>}
 */
async function startGrpcNode(port, handlers = {}, options = {}) {
  const nodeId = options.nodeId || `node-${port}`;

  const server = new grpc.Server();

  server.addService(ConditionallyExecuteNode.service, {
    /**
     * Execute a handler on this node.
     */
    execute(call, callback) {
      const { condition, handler_name, metadata } = call.request;
      const handler = handlers[handler_name];

      if (!handler) {
        return callback(null, {
          node_id: nodeId,
          executed: false,
          branch: condition ? 'onTrue' : 'onFalse',
          duration_ms: 0,
          error: `Handler '${handler_name}' not registered on ${nodeId}`,
        });
      }

      const branch = condition ? 'onTrue' : 'onFalse';
      const start = performance.now();

      // Only execute the handler if it matches the active branch
      // (handlers are registered per branch on the node — if condition is true,
      //  we call the onTrue handler; if false, the onFalse handler)
      // For simplicity: handler_name is the true-branch handler name.
      // Node skips execution when condition doesn't match.
      if (!condition) {
        return callback(null, {
          node_id: nodeId,
          executed: false,
          branch: 'onFalse',
          duration_ms: 0,
          error: '',
        });
      }

      Promise.resolve()
        .then(() => handler(metadata))
        .then(() => {
          callback(null, {
            node_id: nodeId,
            executed: true,
            branch,
            duration_ms: performance.now() - start,
            error: '',
          });
        })
        .catch((err) => {
          callback(null, {
            node_id: nodeId,
            executed: false,
            branch,
            duration_ms: performance.now() - start,
            error: err.message || String(err),
          });
        });
    },

    /**
     * Health check — returns node status and registered handler names.
     */
    health(call, callback) {
      callback(null, {
        node_id: nodeId,
        status: 'ok',
        handlers: Object.keys(handlers),
      });
    },
  });

  await new Promise((resolve, reject) => {
    server.bindAsync(
      `0.0.0.0:${port}`,
      grpc.ServerCredentials.createInsecure(),
      (err, boundPort) => {
        if (err) return reject(err);
        resolve(boundPort);
      }
    );
  });

  return {
    nodeId,
    port,
    close() {
      return new Promise((resolve) => server.tryShutdown(resolve));
    },
  };
}

// ---------------------------------------------------------------------------
// Coordinator client helpers
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} NodeResult
 * @property {string} address
 * @property {boolean} success
 * @property {import('@grpc/grpc-js').ServiceError|null} rpcError
 * @property {object|null} response
 */

/**
 * Call Execute on a single gRPC node.
 * @param {string} address  e.g. "localhost:50051"
 * @param {object} request
 * @param {number} deadlineMs
 * @returns {Promise<NodeResult>}
 */
function callNode(address, request, deadlineMs) {
  return new Promise((resolve) => {
    const client = new ConditionallyExecuteNode(
      address,
      grpc.credentials.createInsecure()
    );

    const deadline = new Date(Date.now() + deadlineMs);

    client.execute(request, { deadline }, (err, response) => {
      client.close();

      if (err) {
        resolve({ address, success: false, rpcError: err, response: null });
      } else {
        resolve({
          address,
          success: !response.error && response.executed,
          rpcError: null,
          response,
        });
      }
    });
  });
}

// ---------------------------------------------------------------------------
// Plugin factory
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} GrpcConsensusOptions
 * @property {string[]} nodes        gRPC node addresses, e.g. ['localhost:50051', 'localhost:50052']
 * @property {string} handlerName    Name of the handler to invoke on each node (must be registered)
 * @property {number} [quorum]       Minimum successful node responses required (default: majority)
 * @property {number} [timeout=5000] Per-node RPC deadline in ms
 * @property {boolean} [verbose=false] Log per-node results to stdout
 */

/**
 * Creates a GrpcConsensusPlugin middleware.
 * Broadcasts Execute() to all nodes, verifies quorum, then proceeds.
 *
 * @param {GrpcConsensusOptions} options
 * @returns {import('../index').Middleware}
 */
function GrpcConsensusPlugin(options) {
  const {
    nodes,
    handlerName,
    quorum = Math.floor(nodes.length / 2) + 1,
    timeout = 5000,
    verbose = false,
  } = options;

  if (!Array.isArray(nodes) || nodes.length === 0) {
    throw new Error('GrpcConsensusPlugin: nodes must be a non-empty array of gRPC addresses');
  }
  if (typeof handlerName !== 'string' || !handlerName) {
    throw new Error('GrpcConsensusPlugin: handlerName is required');
  }
  if (quorum > nodes.length) {
    throw new Error(`GrpcConsensusPlugin: quorum (${quorum}) cannot exceed node count (${nodes.length})`);
  }

  return async function grpcConsensusMiddleware(ctx, next) {
    const requestId = randomUUID();

    const request = {
      request_id: requestId,
      condition: ctx.condition,
      handler_name: handlerName,
      metadata: {},
    };

    // Fan out — call all nodes simultaneously
    const results = await Promise.all(
      nodes.map((addr) => callNode(addr, request, timeout))
    );

    const succeeded = results.filter((r) => r.success).length;

    if (verbose || process.env.CE_GRPC_DEBUG) {
      for (const r of results) {
        const icon = r.success ? '✅' : '❌';
        const detail = r.rpcError
          ? `RPC error: ${r.rpcError.message}`
          : r.response
            ? `branch=${r.response.branch} duration=${r.response.duration_ms?.toFixed(2)}ms`
            : 'no response';
        console.log(`[GrpcConsensusPlugin] ${icon} ${r.address}: ${detail}`);
      }
      console.log(
        `[GrpcConsensusPlugin] quorum: ${succeeded}/${nodes.length} ` +
        `(required ${quorum}) → ${succeeded >= quorum ? 'PASSED' : 'FAILED'}`
      );
    }

    if (succeeded < quorum) {
      throw new QuorumError(succeeded, quorum, nodes.length, results);
    }

    // Quorum reached — execute coordinator-side handlers
    await next();
  };
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = {
  GrpcConsensusPlugin,
  startGrpcNode,
  QuorumError,
};
