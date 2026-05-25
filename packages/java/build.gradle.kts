import com.google.protobuf.gradle.id
import java.time.Duration

plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.4"
}

group = "com.bopke"
version = "2.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    api("io.grpc:grpc-stub:1.66.0")
    api("io.grpc:grpc-protobuf:1.66.0")
    api("com.google.protobuf:protobuf-java:4.28.2")
    implementation("io.grpc:grpc-netty-shaded:1.66.0")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Stage the shared proto into a build directory with Java options appended.
// The canonical proto at ../../proto/conditionally_execute.proto is read-only
// (shared with the JS module) so we copy + augment it here.
val stagedProtoDir = layout.buildDirectory.dir("staged-proto")

val stageProto by tasks.registering {
    val source = file("../../proto/conditionally_execute.proto")
    inputs.file(source)
    outputs.dir(stagedProtoDir)
    doLast {
        val outDir = stagedProtoDir.get().asFile
        outDir.mkdirs()
        val target = outDir.resolve("conditionally_execute.proto")
        val original = source.readText()
        // Inject Java options so generated classes land in our package and as
        // separate files. Idempotent if options already present.
        val javaOptions = """
            |option java_package = "com.bopke.conditionallyexecute.proto";
            |option java_multiple_files = true;
            |option java_outer_classname = "ConditionallyExecuteProto";
            |""".trimMargin()
        val augmented = if (original.contains("java_package"))
            original
        else
            original.replace(
                Regex("^package conditionally_execute;", RegexOption.MULTILINE),
                "package conditionally_execute;\n\n$javaOptions"
            )
        target.writeText(augmented)
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.2"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.66.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("grpc")
            }
            it.dependsOn(stageProto)
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir(stagedProtoDir)
        }
    }
}

// processResources picks up the staged proto dir (it lives under build/ and
// the protobuf plugin maps proto srcDirs into the resources source set).
// Tell Gradle about the implicit dependency, and exclude .proto files from
// the runtime jar — they're not needed at runtime, the generated Java is.
tasks.processResources {
    dependsOn(stageProto)
    exclude("**/*.proto")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
    // Allow long-running gRPC tests
    timeout = Duration.ofMinutes(2)
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        // Generated proto sources and a couple of intentional patterns
        // (e.g. unchecked casts in lambda dispatch) trigger warnings under
        // -Xlint:all; suppress at the compiler level rather than littering
        // @SuppressWarnings everywhere.
        "-Xlint:-processing",
        "-Xlint:-serial",
    ))
    options.encoding = "UTF-8"
}
