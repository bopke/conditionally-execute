/* eslint-disable no-console, no-constant-condition */
'use strict';

/**
 * Performance benchmark: native `if` vs ConditionallyExecute
 *
 * Expected result: native `if` is faster.
 * Expected conclusion: worth it anyway for the readability gains.
 */

const ConditionallyExecute = require('./src/index.js');

const ITERATIONS = 100_000;

function noop() {}

// --- Benchmark runner ---

async function bench(name, fn) {
  // warmup
  for (let i = 0; i < 1000; i++) await fn();

  const start = performance.now();
  for (let i = 0; i < ITERATIONS; i++) await fn();
  const elapsed = performance.now() - start;

  console.log(`${name.padEnd(40)} ${elapsed.toFixed(2).padStart(8)} ms  (${(elapsed / ITERATIONS * 1000).toFixed(3)} μs/op)`);
}

// --- Benchmarks ---

async function main() {
  console.log(`\nconditionally-execute benchmark — ${ITERATIONS.toLocaleString()} iterations\n`);
  console.log('─'.repeat(60));

  await bench('native if (true branch)', async () => {
    if (true) { noop(); }
  });

  await bench('native if (false branch)', async () => {
    if (false) { noop(); } else { noop(); }
  });

  await bench('ConditionallyExecute (true)', async () => {
    await new ConditionallyExecute()
      .condition(true)
      .onTrue(noop)
      .execute();
  });

  await bench('ConditionallyExecute (false)', async () => {
    await new ConditionallyExecute()
      .condition(false)
      .onFalse(noop)
      .execute();
  });

  await bench('ConditionallyExecute (default)', async () => {
    await new ConditionallyExecute()
      .onTrue(noop)
      .execute();
  });

  console.log('');

  // Sync variants (no Promise overhead)
  await bench('native if (true branch, sync ctx)', () => {
    if (true) { noop(); }
  });

  await bench('ConditionallyExecute.executeSync (true)', () => {
    new ConditionallyExecute()
      .condition(true)
      .onTrue(noop)
      .executeSync();
  });

  await bench('ConditionallyExecute.executeSync (false)', () => {
    new ConditionallyExecute()
      .condition(false)
      .onFalse(noop)
      .executeSync();
  });

  console.log('─'.repeat(60));
  console.log('\n⚠️  native if is faster. Worth it for the DX gains.\n');
}

main().catch(console.error);
