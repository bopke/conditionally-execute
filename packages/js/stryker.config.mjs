// @ts-check
/** @type {import('@stryker-mutator/api/core').PartialStrykerOptions} */
const config = {
  testRunner: 'mocha',
  testRunnerNodeArgs: [],
  mocha: {
    spec: ['test/core.js'],
    timeout: 10000,
  },
  mutate: ['src/index.js'],
  reporters: ['progress', 'clear-text', 'html'],
  htmlReporter: {
    fileName: 'reports/mutation/mutation.html',
  },
  thresholds: {
    high: 80,
    low: 60,
    break: 50,
  },
  concurrency: 4,
  timeoutMS: 10000,
  timeoutFactor: 1.5,
};

export default config;
