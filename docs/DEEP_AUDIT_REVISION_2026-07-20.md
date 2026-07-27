# Deep audit revision — IoT Offline Toolbox 3.6.2

> **Final-audit note:** This report is retained as historical evidence and is superseded by [`FINAL_CODE_AUDIT_2026-07-20.md`](FINAL_CODE_AUDIT_2026-07-20.md).

Audit date: 2026-07-20

## Project identification

```text
Project type: Kotlin Multiplatform + Compose Multiplatform
Modules: composeApp, androidApp, iosApp
Active targets: Android, Desktop JVM, iOS arm64/simulator arm64, JS browser, Wasm browser
UI sharing: Compose UI in commonMain
Networking: Ktor plus platform TCP/UDP/discovery adapters
Persistence: Multiplatform Settings-backed serialized state
Architecture: shared model/repository/store + platform adapters + Compose screens
Analysis scope: full delivered source archive
```

## Audit method

The re-audit compared the previously delivered ZIP against a fresh extraction, inspected Gradle/source-set/configuration files, scanned all Kotlin/resources for syntax/resource/capability inconsistencies, reviewed HTTP and port-tool data paths, added focused regression tests/harnesses, and re-ran all dependency-free checks. No result is described as a Gradle build or platform runtime unless it actually ran.

## Material defects discovered in the previous delivery

1. `ToolboxStore.kt` contained malformed Kotlin strings/apostrophe introduced during the prior transformation.
2. The claim that all user-facing text had moved to resources was too broad; multiple Store/runtime messages were still English.
3. HTTP request/connect/socket timeout behavior was not modeled independently and unsupported platform controls were not surfaced honestly.
4. Browser targets could be offered headers/cookies/redirect/WebSocket options that browser APIs do not allow.
5. Custom secret header names could bypass the generic raw-history redaction path.
6. cURL generation could emit duplicated timeout switches.
7. Port-scan maximum validation and persistence contradicted each other.
8. A user-facing redaction setting was ineffective because the value was always forced on.
9. Port-result classification collapsed distinct timeout/refusal/unknown failures.
10. Several earlier documentation statements overstated verification and toolchain certainty.

## Corrections applied

- Repaired Kotlin corruption and added an automated source scanner for the same defect class.
- Localized operational Store messages and brought resources to exact 687 English / 687 Persian key and placeholder parity.
- Added request/connect/socket timeout fields, defaults, request wiring, and per-platform capability flags.
- Added browser-forbidden header detection and store-level rejection; browser manual cookie, redirect, and custom WebSocket-header capabilities are disabled.
- Made redaction mandatory and expanded custom secret/token/password header detection across every reviewed persistence/display path.
- Prevented credential-bearing cross-origin redirect propagation.
- Fixed cURL timeout generation and added regression tests.
- Unified scan maximum to `1..4096` and corrected platform port-state mapping.
- Kept raw protocol payloads and server-originated technical bodies unchanged to avoid falsifying diagnostic evidence.
- Updated authoritative documentation and marked historical reports as non-current where necessary.

## Executed evidence

See `VERIFICATION.md` for exact commands and results. Five focused harnesses pass: core, protocol, responsive policy, Desktop real loopback networking, and dedicated secret redaction. Static/deep/UI audits pass after checksum regeneration.

## Blocked evidence

The Gradle wrapper could not download Gradle 9.1.0 because `services.gradle.org` was not resolvable. Failure occurred before project configuration. Therefore Kotlin source-set compilation, common tests, lint, packaging, and platform builds are not verified in this environment.

## Honest release classification

```text
Not production-ready
Statically hardened
Focused executable regression evidence available
Full build and platform verification required
```

## Project metrics at packaging

- Kotlin files: 74
- Kotlin lines: 15,685
- Common-test `@Test` annotations: 131
- Compose resource keys: 687 English / 687 Persian
- Files changed or added by this re-audit: 39
- Largest remaining files: `HttpScreen.kt` 961 lines, `ToolboxStore.kt` 849 lines, `CommonComponents.kt` 831 lines, `RealtimeScreen.kt` 803 lines

The large files are a maintainability finding, not proof of a runtime defect. They were not aggressively split while the complete Gradle build remained unavailable.

## Changed/added files

- `ARCHITECTURE.md`
- `CHANGELOG.md`
- `README.md`
- `RELEASE_CHECKLIST.md`
- `SECURITY.md`
- `SETUP.md`
- `composeApp/src/androidMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.android.kt`
- `composeApp/src/commonMain/composeResources/values-fa/strings.xml`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/SecretRedactor.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/data/ToolboxRepository.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/model/Models.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpRequestTools.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/network/HttpToolClient.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/core/store/ToolboxStore.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/HttpScreen.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/RealtimeScreen.kt`
- `composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/screens/SettingsScreen.kt`
- `composeApp/src/commonTest/kotlin/com/msa/iotofflinetoolbox/core/data/SecretRedactorTest.kt`
- `composeApp/src/commonTest/kotlin/com/msa/iotofflinetoolbox/core/data/ToolboxRepositoryTest.kt`
- `composeApp/src/commonTest/kotlin/com/msa/iotofflinetoolbox/core/network/HttpRequestToolsTest.kt`
- `composeApp/src/desktopMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.desktop.kt`
- `composeApp/src/iosMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.ios.kt`
- `composeApp/src/jsMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.js.kt`
- `composeApp/src/wasmJsMain/kotlin/com/msa/iotofflinetoolbox/core/network/PlatformNetwork.wasmJs.kt`
- `docs/ANDROID_STUDIO_AGP_COMPATIBILITY.md`
- `docs/ARCHITECTURE.md`
- `docs/BUILD_ATTEMPT.txt`
- `docs/DEEP_AUDIT_REVISION_2026-07-20.md`
- `docs/DEEP_CODE_CONFIG_AUDIT_3.6.0.md`
- `docs/DEEP_CODE_CONFIG_AUDIT_3.6.2.md`
- `docs/FINAL_AUDIT_REPORT.md`
- `docs/PRODUCTION_HARDENING_REPORT.md`
- `docs/PROFESSIONAL_COMPLETION_REPORT.md`
- `docs/SOURCE_SHA256SUMS.txt`
- `docs/VERIFICATION.md`
- `tools/deep_quality_audit.py`
- `tools/run_quality_checks.sh`
- `tools/secret_redactor_runtime_harness.sh`
