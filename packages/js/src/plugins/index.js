'use strict';

/**
 * conditionally-execute plugins
 *
 * All first-party plugins in one place.
 *
 * @example
 * const { TimeoutPlugin, RetryPlugin, AuditLogPlugin } = require('conditionally-execute/plugins');
 */

const { TimeoutPlugin, TimeoutError } = require('./timeout');
const { RetryPlugin }                 = require('./retry');
const { DryRunPlugin }                = require('./dry-run');
const { AuditLogPlugin }              = require('./audit-log');
const { CollectErrorsPlugin }         = require('./collect-errors');
const { GrpcConsensusPlugin, startGrpcNode, QuorumError } = require('./grpc-consensus');
const { MultiThreadedPlugin }         = require('./multi-threaded');

module.exports = {
  // Reliability
  TimeoutPlugin,
  TimeoutError,
  RetryPlugin,

  // Observability
  AuditLogPlugin,

  // Control flow
  DryRunPlugin,
  CollectErrorsPlugin,

  // Distributed execution
  GrpcConsensusPlugin,
  startGrpcNode,
  QuorumError,
  MultiThreadedPlugin,
};
