# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Monorepo restructure**: project is now a polyglot monorepo with two
  language packages under `packages/`. The npm package source moved to
  `packages/js/` (entry now resolves via `packages/js/package.json`,
  unchanged for consumers).
- **Java module (`packages/java/`)** — full Java port with 100% feature
  parity against the JavaScript module. Targets JDK 25, built with
  Gradle Kotlin DSL, tested with JUnit 5 + AssertJ (66 tests).
  - Core: `ConditionallyExecute`, `Context`, `Branch`, `Handler`,
    `Middleware`, `Next`, `ConditionallyExecuteError`, `AggregateException`
  - Plugins: `TimeoutPlugin`, `RetryPlugin`, `AuditLogPlugin`,
    `CollectErrorsPlugin`, `DryRunPlugin`, `MultiThreadedPlugin`
    (virtual-thread quorum), `GrpcConsensusPlugin` + `GrpcNodeServer`
  - Async model: `CompletableFuture<Void>` everywhere; sync escape hatch
    via `executeSync()`.
- **Shared gRPC schema** (`proto/conditionally_execute.proto`) — single
  source of truth used by both the JS and Java `GrpcConsensusPlugin`,
  enabling cross-runtime quorum (Java coordinator → JS nodes, etc.).
- **Java CI workflow** (`.github/workflows/java.yml`) running Gradle
  build + tests on Temurin JDK 25.
- **Root README** redesigned as a monorepo overview pointing at per-language docs.
- `ConditionallyExecuteOptions` interface with `initialCondition` and `collectErrors` options (JS)
- `collectErrors` mode: collects all handler errors into an `AggregateError` instead of short-circuiting (JS)
- Full JSDoc documentation on all public members (JS)
- `.prettierrc` code style configuration
- `.editorconfig` for consistent editor settings
- GitHub issue templates (bug report, feature request)
- GitHub PR template
- `dependabot.yml` for automated dependency updates
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`
- `bench.js` — performance benchmark vs native `if`

### Changed
- **Breaking**: `execute()` is now `async` and returns `Promise<void>`
  (previously synchronous — any code that did not `await` execute() was already
  silently ignoring async handlers)
- Condition logic: `this.True` (public, boolean-typed `true` literal) replaced
  with `this._condition` (private boolean). Last `.condition()` call wins.
- `onTrue()` and `onFalse()` now throw `TypeError` immediately for non-function arguments
- CI updated: `actions/checkout@v2` → `v4`, `setup-node@v1` → `v4` (with npm cache)
- CI: removed no-op `npm run build --if-present`, added lint and typecheck steps
- Node.js CI workflow now runs from `packages/js/` working directory (monorepo restructure)
- README: fixed typo in install command (`conditionaly-execute` → `conditionally-execute`)
- README: added full API reference, async examples, default condition documentation
- Node.js support floor raised from 16 (EOL) to 18 (LTS)

### Fixed
- Multi-condition bug: calling `.condition(false).condition(true)` previously
  ignored the second call; it now correctly uses the last-provided value
- Promise-returning handlers were silently dropped; all handlers now properly awaited

## [1.0.0] — Initial release

- `ConditionallyExecute` class with `.condition()`, `.onTrue()`, `.onFalse()`, `.execute()`
- Fluent builder API with arbitrary method ordering
- Basic Mocha test suite
- GitHub Actions CI (Node 16–24)
