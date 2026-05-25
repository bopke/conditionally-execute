# conditionally-execute (Java)

Java port of [conditionally-execute](https://github.com/bopke/conditionally-execute) — a composable conditional-execution library with middleware, plugins, and distributed-consensus primitives.

100% feature parity with the JavaScript module living in [`../js`](../js/).

## Requirements

- **JDK 25** (LTS, Sept 2025). The `foojay-resolver-convention` Gradle plugin auto-provisions a matching toolchain on first build if your local JDK is older.
- **Gradle 8.10+** (or just `gradle` on your PATH — the build is wrapper-less by default; run `gradle wrapper` once to commit a wrapper jar if desired)

## Quick start

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.bopke:conditionally-execute:2.0.0")
}
```

```java
import com.bopke.conditionallyexecute.ConditionallyExecute;
import com.bopke.conditionallyexecute.Handler;
import com.bopke.conditionallyexecute.plugins.TimeoutPlugin;
import com.bopke.conditionallyexecute.plugins.RetryPlugin;

new ConditionallyExecute()
    .use(TimeoutPlugin.of(5000))
    .use(RetryPlugin.of(3, RetryPlugin.Backoff.EXPONENTIAL))
    .condition(isHealthy())
    .onTrue(Handler.sync(this::deployToProduction))
    .execute()
    .join();
```

## API

The Java API mirrors the JavaScript API one-to-one with idiomatic Java translations:

| JavaScript | Java |
|---|---|
| `Promise<void>` | `CompletableFuture<Void>` |
| `Error` / `TypeError` | `RuntimeException` / `IllegalArgumentException` |
| `AggregateError` | `AggregateException` (custom, with `errors()` accessor) |
| `Middleware: (ctx, next) => Promise<void>` | `Middleware: BiFunction<Context, Next, CompletableFuture<Void>>` |
| Closure-captured handlers | `Handler` functional interface, plus `Handler.sync(Runnable)` / `Handler.async(Supplier<...>)` factories |
| `worker_threads` (MultiThreadedPlugin) | Virtual threads (JEP 444) |
| `@grpc/grpc-js` | `io.grpc:grpc-netty-shaded` |

### Core

```java
new ConditionallyExecute()
    .condition(boolean | Object | String)   // last call wins; String tries registry first
    .onTrue(Handler | Runnable)
    .onFalse(Handler | Runnable)
    .onError(Consumer<Throwable>)            // or onErrorAsync(Function<Throwable, CompletableFuture<Void>>)
    .use(Middleware)
    .execute();        // CompletableFuture<Void>, runs handlers concurrently
    // OR
    .executeSync();    // void, sequential, no Promise overhead

ConditionallyExecute.register("name", () -> evaluate());
ConditionallyExecute.unregister("name");
ConditionallyExecute.clearRegistry();
```

### Plugins

All plugins live in `com.bopke.conditionallyexecute.plugins`:

- **`TimeoutPlugin.of(long ms)`** — wraps the chain in a deadline, throws `TimeoutError`
- **`RetryPlugin.of(int n)` / `RetryPlugin.of(int n, Backoff)`** — per-handler retry; `Backoff` is `NONE`, `LINEAR`, `EXPONENTIAL`
- **`AuditLogPlugin.of()` / `AuditLogPlugin.of(Consumer<String> logger)`** — structured execution log
- **`CollectErrorsPlugin.of()`** — run all handlers, aggregate failures into `AggregateException`
- **`DryRunPlugin.of()` / `DryRunPlugin.of(Consumer<String> logger)`** — log without executing
- **`MultiThreadedPlugin.of(Options)`** — virtual-thread quorum vote
- **`GrpcConsensusPlugin.of(Options)`** — distributed quorum over gRPC; pair with `GrpcNodeServer.startAsync(port, handlers)`

The `GrpcConsensusPlugin` interops with the JavaScript implementation
through the shared `proto/conditionally_execute.proto` — a Java coordinator
can call JS nodes, and vice versa.

## Testing

```bash
gradle test
```

The test suite mirrors the JavaScript suite (66 tests in JUnit 5 + AssertJ):

- `CoreTest` — basic / extended / coercion / async / sync / validation / middleware / registry / TimeoutPlugin / RetryPlugin / DryRunPlugin / AuditLogPlugin / CollectErrorsPlugin
- `MultiThreadedTest` — virtual-thread quorum tests
- `GrpcConsensusTest` — boots real gRPC servers on ports 52100–52102

## Project structure

```
packages/java/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── src/
    ├── main/java/com/bopke/conditionallyexecute/
    │   ├── ConditionallyExecute.java
    │   ├── Context.java
    │   ├── Branch.java
    │   ├── Handler.java
    │   ├── Middleware.java
    │   ├── Next.java
    │   ├── ConditionallyExecuteError.java
    │   ├── AggregateException.java
    │   └── plugins/
    │       ├── TimeoutPlugin.java   + TimeoutError.java
    │       ├── RetryPlugin.java
    │       ├── AuditLogPlugin.java
    │       ├── CollectErrorsPlugin.java
    │       ├── DryRunPlugin.java
    │       ├── MultiThreadedPlugin.java
    │       ├── GrpcConsensusPlugin.java  + QuorumError.java + NodeResult.java
    │       └── GrpcNodeServer.java
    └── test/java/com/bopke/conditionallyexecute/
        ├── CoreTest.java
        ├── MultiThreadedTest.java
        └── GrpcConsensusTest.java
```

The gRPC proto is **not** in this module — it lives at `proto/conditionally_execute.proto`
at the repository root and is staged into the Java build at compile time.

## License

MIT © [Michał Kubik](https://github.com/bopke)
