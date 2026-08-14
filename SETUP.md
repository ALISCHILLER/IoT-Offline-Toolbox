# Setup and verification

## Prerequisites

- Locally installed JDK 17 for the Gradle toolchain
- Android Studio with AGP `9.1.0` support; Android SDK 36 and Build Tools 36.0.0
- Xcode on macOS for iOS simulator/framework tasks
- Node.js is managed by the Kotlin/JS toolchain unless a local policy overrides it
- Network access to official Google Maven, Maven Central, Gradle Plugin Portal, and the pinned Gradle distribution, or a verified populated cache


> This checkout uses the stable AGP `9.1.0` line with Gradle `9.3.1`, Kotlin `2.4.10`, and JDK 17. The versions are compatibility-matrix aligned, but the project must still complete the full Android/KMP build, test, lint, packaging, and runtime matrix before release.

## Open the project

Open the repository root containing `settings.gradle.kts`; do not open only `androidApp` or `composeApp`.

```bash
cd IoT-Offline-Toolbox-3.6.2
```

Use a locally installed Gradle JDK 17. `gradle.properties` sets `org.gradle.java.installations.auto-download=false`, so a missing toolchain fails explicitly instead of being downloaded silently. Disable Gradle Offline mode for the first synchronization unless all dependencies and the exact Gradle distribution are already verified in cache. If Gradle reports that it is using a previously auto-provisioned JDK from the user Gradle cache, point Android Studio Gradle JDK (or `JAVA_HOME`) at a local JDK 17 installation for reproducible/offline builds.

## Repository policy

Project settings allow only:

- Google Maven
- Maven Central
- Gradle Plugin Portal for plugins

Module-level repositories are rejected. Machine-level Gradle init scripts can still inject mirrors; see [docs/GRADLE_REPOSITORY_RECOVERY.md](docs/GRADLE_REPOSITORY_RECOVERY.md).

## Dependency-free audit

```bash
bash tools/run_quality_checks.sh --audit
```

This checks source/configuration structure, version coherence, packages, secrets, protocol/repository invariants, XML/TOML/JSON/YAML shape, UI source contracts, contrast tokens, scripts, documentation, and source checksums. It does not compile Kotlin.

## Current-source runtime harnesses

```bash
bash tools/run_quality_checks.sh --static
```

This includes `--audit` plus current-source Kotlin harnesses for:

- transport security, headers, CIDR/ports, payload codecs, and redaction
- MQTT, CoAP, DNS-SD, and SSDP codecs/parsers
- responsive-layout policy
- actual Desktop TCP/UDP loopback implementation

`kotlinc` and a compatible local `kotlinx-coroutines-core-jvm.jar` are required. These harnesses are targeted evidence, not a replacement for Gradle target builds.

## Gradle verification

```bash
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

Convenience modes:

```bash
bash tools/run_quality_checks.sh --jvm-android
bash tools/run_quality_checks.sh --web
bash tools/run_quality_checks.sh --all
```

## Run targets

### Desktop

```bash
./gradlew :composeApp:run
```

### Android

Build/install `androidApp` from Android Studio or:

```bash
./gradlew :androidApp:installDebug
```

### Browser

Use the Kotlin/Compose browser run tasks exposed by the installed toolchain, or build production bundles with the commands above.

### iOS

Open `iosApp/iosApp.xcodeproj` after the shared framework tasks resolve. Configure a development team only in local protected settings; no signing identity is committed.

## Security configuration

No API key, password, keystore, provisioning profile, or signing secret is required for normal local operation. Do not commit:

```text
.env
local.properties
keystore.properties
*.jks
*.keystore
*.p12
*.mobileprovision
```

The app permits OS-level Android cleartext because local embedded devices may require HTTP. Shared application policy blocks public cleartext by default. The unsafe override is operator-controlled and cannot be enabled by backup import.

## Packaging verification

```bash
python3 tools/generate_source_checksums.py
python3 tools/static_audit.py
zip -r IoT-Offline-Toolbox-3.6.2.zip IoT-Offline-Toolbox-3.6.2
unzip -t IoT-Offline-Toolbox-3.6.2.zip
sha256sum IoT-Offline-Toolbox-3.6.2.zip
```

Exclude `.gradle`, `.kotlin`, build outputs, IDE metadata, local properties, caches, and signing material.

## Troubleshooting Gradle model failures

On Windows, inspect project versions and user-level Gradle injection without modifying files:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\tools\show-project-versions.ps1
.\tools\fix-gradle-sync-windows.ps1
```

Apply only after reviewing the report:

```powershell
.\tools\fix-gradle-sync-windows.ps1 -Apply
```

## Release gate

A successful static audit or harness does not prove Android, iOS, Desktop, JS, or Wasm production readiness. Complete every applicable item in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

## Author

Developed and maintained by  
[ALISCHILLER](https://github.com/ALISCHILLER) — **MSA**

Email: [solimaniali90@gmail.com](mailto:solimaniali90@gmail.com)
