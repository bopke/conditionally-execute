#!/usr/bin/env node
'use strict';

/**
 * Parallel test suite runner.
 *
 * Spawns each test file as a separate child process (via `npx mocha`),
 * running all suites concurrently. Aggregates results and exits 1 if
 * any suite fails.
 *
 * Usage: node test-parallel.js
 *
 * Because even your test suite deserves distributed execution.
 */

const { spawn } = require('child_process');
const path = require('path');

const TEST_SUITES = [
  { name: 'core        ', file: 'test/core.js' },
  { name: 'multi-thread', file: 'test/multi-threaded.js' },
  { name: 'grpc        ', file: 'test/grpc.js' },
];

const startTime = Date.now();

console.log(`\n🚀 Running ${TEST_SUITES.length} test suites in parallel...\n`);
console.log('─'.repeat(60));

const jobs = TEST_SUITES.map(({ name, file }) => {
  return new Promise((resolve) => {
    const chunks = [];

    const proc = spawn('npx', ['--yes', 'mocha', '--timeout', '10000', file], {
      cwd: path.resolve(__dirname, '..'),
      env: { ...process.env, FORCE_COLOR: '1' },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    proc.stdout.on('data', (d) => chunks.push(d));
    proc.stderr.on('data', (d) => chunks.push(d));

    proc.on('close', (code) => {
      resolve({
        name,
        file,
        success: code === 0,
        output: Buffer.concat(chunks).toString('utf8'),
      });
    });

    proc.on('error', (err) => {
      resolve({ name, file, success: false, output: err.message });
    });
  });
});

Promise.all(jobs).then((results) => {
  for (const r of results) {
    const icon = r.success ? '✅' : '❌';
    console.log(`\n${icon} [${r.name}] ${r.file}`);
    console.log('─'.repeat(60));
    console.log(r.output.split('\n').map((l) => '  ' + l).join('\n'));
  }

  const elapsed = ((Date.now() - startTime) / 1000).toFixed(2);
  const passed = results.filter((r) => r.success).length;
  const failed = results.length - passed;

  console.log('─'.repeat(60));
  console.log(`\n📊 ${passed}/${results.length} suites passed in ${elapsed}s (ran concurrently)\n`);

  process.exit(failed > 0 ? 1 : 0);
});
