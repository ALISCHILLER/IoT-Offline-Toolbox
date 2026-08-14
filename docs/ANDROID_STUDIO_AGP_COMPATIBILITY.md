# Stable Android/KMP toolchain matrix - 3.6.2 hardening candidate

Date: 2026-08-14

## Selected profile

| Component | Selected version | Evidence and reason |
|---|---:|---|
| Android Gradle Plugin | `9.1.0` | Stable version within Kotlin 2.4.10's fully supported AGP range; Android documents Gradle 9.3.1, Build Tools 36.0.0, and JDK 17 for the 9.1 line |
| Gradle distribution | `9.3.1` | Required/default version for AGP 9.1; official binary SHA-256 is pinned |
| Kotlin / KGP | `2.4.10` | Stable line; official Kotlin matrix fully supports Gradle through 9.5.0 and the AGP 9.1 line |
| Compose Multiplatform plugin and core libraries | `1.11.1` | Plugin, runtime, foundation, UI, resources, and tooling-preview are aligned to the same released component set |
| compileSdk / targetSdk | `36` | Preserved |
| JDK | `17` | AGP 9.1 required/default JDK and CI baseline |

The previous `9.0.0-alpha06` profile was an IDE-ceiling workaround. It is retained only in historical audit documents and is no longer the active project configuration.

## Wrapper integrity

`gradle-wrapper.properties` points to `gradle-9.3.1-bin.zip` and pins the official binary SHA-256:

```text
b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
```

The checked-in wrapper JAR has an official Gradle 8.14.4 checksum. Gradle documents that older wrapper files can launch a newer configured distribution. On the first networked verification host, run the wrapper task twice with Gradle 9.3.1 to refresh all wrapper files, then review the binary diff and re-run wrapper validation.

## Required verification before acceptance

```bash
./gradlew --version
./gradlew :composeApp:desktopTest :composeApp:compileKotlinDesktop --stacktrace
./gradlew :composeApp:testAndroidHostTest :androidApp:assembleDebug :androidApp:assembleRelease --stacktrace
./gradlew :androidApp:lintDebug :androidApp:lintRelease --stacktrace
./gradlew :composeApp:jsBrowserTest :composeApp:wasmJsBrowserTest --stacktrace
./gradlew :composeApp:jsBrowserProductionWebpack :composeApp:wasmJsBrowserProductionWebpack --stacktrace
```

On macOS:

```bash
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkReleaseFrameworkIosSimulatorArm64 --stacktrace
```

## Rollback

Revert the toolchain migration commit as one unit. Do not retain AGP 9.1 with the old Gradle distribution, or the new Gradle distribution with preview AGP. No application ID, package, persistence schema, protocol behavior, or user data changes are part of this batch.

## Status

- Version/configuration review: completed
- Official compatibility evidence: recorded
- Distribution checksum: verified against official reference
- Dependency resolution and complete target build: not executed in this environment
- Production readiness: not production-ready
