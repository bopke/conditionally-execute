<div align="center">

# conditionally-execute

**Enterprise-grade if-statement replacement**

[![Node.js CI](https://github.com/bopke/conditionally-execute/actions/workflows/nodejs.yml/badge.svg)](https://github.com/bopke/conditionally-execute/actions/workflows/nodejs.yml)
[![npm version](https://img.shields.io/npm/v/conditionally-execute?color=crimson)](https://www.npmjs.com/package/conditionally-execute)
[![npm downloads](https://img.shields.io/npm/dm/conditionally-execute)](https://www.npmjs.com/package/conditionally-execute)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-blue?logo=typescript)](tsconfig.json)
[![code style: prettier](https://img.shields.io/badge/code_style-prettier-ff69b4.svg)](https://prettier.io)
[![Conventional Commits](https://img.shields.io/badge/Conventional%20Commits-1.0.0-yellow.svg)](https://conventionalcommits.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Node.js >= 18](https://img.shields.io/node/v/conditionally-execute)](package.json)

> Lets you abandon `if` keyword

</div>

---

## Table of Contents

- [Why?](#why)
- [Install](#install)
- [Quick start](#quick-start)
- [API](#api)
  - [`new ConditionallyExecute(options?)`](#new-conditionallyexecuteoptions)
  - [`.condition(value)`](#conditionvalue--this)
  - [`.onTrue(fn)`](#ontruefn--this)
  - [`.onFalse(fn)`](#onfalsefn--this)
  - [`.execute()`](#execute--promisevoid)
- [Advanced usage](#advanced-usage)
  - [Async handlers](#async-handlers)
  - [Multiple handlers](#multiple-handlers)
  - [Default condition](#default-condition)
  - [Collecting errors](#collecting-errors)
- [Performance](#performance)
- [`executeSync()`](#executesync--void)
- [TypeScript](#typescript)
- [Contributing](#contributing)
- [License](#license)

---

## Why?

Because sometimes `if (condition) { ... } else { ... }` is just too readable
and you want your code to look more like a well-considered fluent API.

No, really — fluent conditional execution is useful when you need to:
- Register multiple callbacks per branch
- Mix sync and async handlers transparently
- Pass condition evaluation and handler registration through separate pipeline stages

---

## Install

```bash
npm install conditionally-execute
```

**Requirements**: Node.js ≥ 18.0.0

---

## Quick start

```javascript
const ConditionallyExecute = require('conditionally-execute');

await new ConditionallyExecute()
  .condition(user.isAdmin)
  .onTrue(() => grantAccess())
  .onFalse(() => denyAccess())
  .execute();
```

---

## API

### `new ConditionallyExecute(options?)`

Creates a new instance. Optionally accepts a configuration object.

```typescript
interface ConditionallyExecuteOptions {
  initialCondition?: boolean;  // default: true
  collectErrors?: boolean;     // default: false
}
```

### `.condition(value)` → `this`

Sets the condition. Any value is accepted and coerced to `boolean` via `Boolean()`.
**Last call wins** — calling this multiple times overwrites the previous value.

```javascript
.condition(1 === 1)        // true
.condition(user.isAdmin)   // boolean
.condition('non-empty')    // truthy → true
.condition(0)              // falsy → false
```

### `.onTrue(fn)` → `this`

Registers a handler for the truthy branch. Throws `TypeError` if `fn` is not a function.

### `.onFalse(fn)` → `this`

Registers a handler for the falsy branch. Throws `TypeError` if `fn` is not a function.

### `.execute()` → `Promise<void>`

Executes all handlers for the active branch **concurrently** via `Promise.all`.
Supports async handlers. Must be the last method call in the chain.

### `.executeSync()` → `void`

Executes all handlers for the active branch **synchronously**, in registration order.
Use when all handlers are synchronous and you want minimal overhead (~1.4x native `if`, no Promise allocation).

> ⚠️ If a handler returns a Promise it is **not** awaited. Use `.execute()` for async handlers.

```javascript
new ConditionallyExecute()
  .condition(user.isAdmin)
  .onTrue(() => grantAccess())
  .onFalse(() => denyAccess())
  .executeSync(); // no await needed
```

---

## Advanced usage

### Async handlers

Async handlers are fully supported and properly awaited:

```javascript
await new ConditionallyExecute()
  .condition(await checkPermissions(userId))
  .onTrue(async () => {
    await db.grantAccess(userId);
    await audit.log('access_granted', userId);
  })
  .onFalse(async () => {
    await audit.log('access_denied', userId);
    await notifier.send(userId, 'Access denied');
  })
  .execute();
```

### Multiple handlers

Both `.onTrue()` and `.onFalse()` can be called multiple times.
All registered handlers for the active branch run **concurrently**:

```javascript
await new ConditionallyExecute()
  .condition(isDeployment)
  .onTrue(() => slack.notify('Deployment started'))
  .onTrue(() => dashboard.setStatus('deploying'))
  .onTrue(() => metrics.increment('deployments.started'))
  .onFalse(() => metrics.increment('deployments.skipped'))
  .execute();
```

### Default condition

If `.condition()` is never called, the default is `true`:

```javascript
// onTrue always fires
await new ConditionallyExecute()
  .onTrue(() => console.log('this always runs'))
  .execute();
```

### Collecting errors

By default, the first handler rejection short-circuits `execute()`.
Set `collectErrors: true` to run all handlers regardless and collect failures:

```javascript
const ce = new ConditionallyExecute({ collectErrors: true });

try {
  await ce
    .condition(true)
    .onTrue(async () => { throw new Error('handler 1 failed'); })
    .onTrue(async () => { throw new Error('handler 2 failed'); })
    .execute();
} catch (err) {
  // AggregateError: 2 handler(s) failed
  console.log(err.errors); // [Error: handler 1 failed, Error: handler 2 failed]
}
```

### Refactoring guide

```javascript
// Before
if (condition) {
  doSomething();
} else {
  doSomethingElse();
}

// After
await new ConditionallyExecute()
  .condition(condition)
  .onTrue(() => doSomething())
  .onFalse(() => doSomethingElse())
  .execute();
```

---

## Performance

```
conditionally-execute benchmark — 100,000 iterations

────────────────────────────────────────────────────────────
native if (true branch)                    8.06 ms  (0.081 μs/op)
native if (false branch)                   7.03 ms  (0.070 μs/op)
ConditionallyExecute.execute (true)       40.51 ms  (0.405 μs/op)   ~5x
ConditionallyExecute.execute (false)      44.11 ms  (0.441 μs/op)   ~6x
ConditionallyExecute.execute (default)    40.54 ms  (0.405 μs/op)   ~5x

native if (true branch, sync ctx)          5.76 ms  (0.058 μs/op)
ConditionallyExecute.executeSync (true)    8.21 ms  (0.082 μs/op)   ~1.4x ✅
ConditionallyExecute.executeSync (false)   9.62 ms  (0.096 μs/op)   ~1.7x ✅
────────────────────────────────────────────────────────────

⚠️  native if is faster. executeSync() closes the gap to ~1.4x.
    Worth it for the DX gains either way.
```

Run benchmarks locally: `node bench.js`

### When to use which

| Use case | Method |
|---|---|
| Any async handler | `.execute()` |
| Multiple concurrent async handlers | `.execute()` |
| All sync handlers, perf-sensitive path | `.executeSync()` |
| You want to live dangerously | `.execute()` (drops Promise on floor if not awaited) |

---

## TypeScript

conditionally-execute is written in TypeScript with strict mode enabled.
Type definitions are included automatically.

```typescript
import { ConditionallyExecute, Handler } from 'conditionally-execute';

const handler: Handler = async () => {
  await doSomething();
};

await new ConditionallyExecute()
  .condition(someCondition)
  .onTrue(handler)
  .execute();
```

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All contributions welcome.
Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## License

MIT © [Michał Kubik](https://github.com/bopke)
