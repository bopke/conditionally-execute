<div align="center">

# conditionally-execute

**Enterprise-grade if-statement replacement — now in two languages**

[![Node.js CI](https://github.com/bopke/conditionally-execute/actions/workflows/nodejs.yml/badge.svg)](https://github.com/bopke/conditionally-execute/actions/workflows/nodejs.yml)
[![Java CI](https://github.com/bopke/conditionally-execute/actions/workflows/java.yml/badge.svg)](https://github.com/bopke/conditionally-execute/actions/workflows/java.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

> Lets you abandon `if` keyword. In any language. Across the wire.

</div>

---

## Repository layout

This is a polyglot monorepo. The same conditional-execution model is
implemented in two languages, sharing a single gRPC schema so the
distributed-consensus plugin can interop across runtimes.

```
.
├── packages/
│   ├── js/        # JavaScript / Node.js implementation
│   └── java/      # Java (JDK 25) implementation
├── proto/         # Shared gRPC schema for both modules
└── .github/workflows/
```

| Package | Language | Status | Docs |
|---|---|---|---|
| [`packages/js`](packages/js) | JavaScript (Node ≥ 18) | published on npm | [README](packages/js/README.md) |
| [`packages/java`](packages/java) | Java (JDK 25) | new in this release | [README](packages/java/README.md) |

Both modules expose the same API surface:

- Fluent builder (`condition`, `onTrue`, `onFalse`, `onError`, `use`)
- Middleware system (`.use(middleware)`) for cross-cutting concerns
- Sync and async execution (`execute` / `executeSync`)
- Plugin family: `TimeoutPlugin`, `RetryPlugin`, `AuditLogPlugin`,
  `CollectErrorsPlugin`, `DryRunPlugin`, `MultiThreadedPlugin`,
  `GrpcConsensusPlugin`

The `GrpcConsensusPlugin` in either language can coordinate with nodes
running on the other — they share `proto/conditionally_execute.proto`.

---

## Quick start

### JavaScript

```bash
npm install conditionally-execute
```

```javascript
const ConditionallyExecute = require('conditionally-execute');

await new ConditionallyExecute()
  .condition(user.isAdmin)
  .onTrue(() => grantAccess())
  .onFalse(() => denyAccess())
  .execute();
```

### Java

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.bopke:conditionally-execute:2.0.0")
}
```

```java
import com.bopke.conditionallyexecute.ConditionallyExecute;
import com.bopke.conditionallyexecute.Handler;

new ConditionallyExecute()
    .condition(user.isAdmin())
    .onTrue(Handler.sync(this::grantAccess))
    .onFalse(Handler.sync(this::denyAccess))
    .execute()
    .join();
```

See per-language READMEs for the full API, plugin catalogue, and
performance numbers.

---

## Local development

```bash
# Install JS deps + run JS tests
npm install
npm test

# Build + test Java module (requires JDK 25 toolchain or foojay auto-provisioning)
cd packages/java && gradle test
```

The JS workspace is wired via npm workspaces (`packages/js`). The Java
module is a standalone Gradle project; the foojay-resolver plugin will
auto-provision JDK 25 on first run if a compatible JDK is not already
installed.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All contributions welcome —
please open the PR against `master` and respect the
[Code of Conduct](CODE_OF_CONDUCT.md).

---

## License

MIT © [Michał Kubik](https://github.com/bopke)
