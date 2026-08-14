# Verification record - 3.6.2

Last updated: 2026-08-14

This file is the canonical current verification record. Historical transformation reports and generated attempt logs are intentionally not packaged.

## Pinned toolchain baseline

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.1.0 |
| Gradle distribution | 9.3.1 |
| Kotlin | 2.4.10 |
| Compose Multiplatform plugin and libraries | 1.11.1 |
| Java toolchain | 17 |

The versions are pinned for reproducibility. Compatibility is source-reviewed; successful dependency resolution and target compilation remain required before release.

## Executed successfully

| Verification | Result |
|---|---|
| `python3 tools/static_audit.py` | Passed; 0 warnings, 0 errors after checksum regeneration |
| `python3 tools/deep_quality_audit.py` | Passed; 658 English / 658 Persian live resource keys and source-quality contracts |
| `python3 tools/architecture_audit.py` | Passed; ports/adapters direction, composition root, service boundaries, persistence interface segregation, pure-policy placement, facade/service ceilings, atomic state and commonMain platform isolation |
| `python3 tools/ui_quality_audit.py` | Passed; contrast and UI source contracts |
| `bash tools/core_runtime_harness.sh` | `FINAL_CORE_RUNTIME_HARNESS_PASSED` |
| `bash tools/secret_redactor_runtime_harness.sh` | `SECRET_REDACTOR_RUNTIME_HARNESS_PASSED` |
| `bash tools/operation_coordinator_runtime_harness.sh` | `OPERATION_COORDINATOR_RUNTIME_HARNESS_PASSED` |
| `bash tools/application_architecture_runtime_harness.sh` | `APPLICATION_ARCHITECTURE_RUNTIME_HARNESS_PASSED`; deterministic runtime, inventory normalization/redaction and journal retention |
| `bash tools/store_facade_compile_harness.sh` | `STORE_FACADE_COMPILE_HARNESS_PASSED`; actual facade plus application services type-compiled against adapter stubs |
| `bash tools/repository_compile_harness.sh` | `REPOSITORY_COMPILE_HARNESS_PASSED`; actual persistence adapter/model/port structure type-compiled against dependency stubs |
| `bash tools/protocol_runtime_harness.sh` | `PROTOCOL_RUNTIME_HARNESS_PASSED` |
| `bash tools/protocol_fuzz_runtime_harness.sh` | `PROTOCOL_FUZZ_HARNESS_PASSED cases=110000 seed=deterministic` |
| `bash tools/responsive_runtime_harness.sh` | `RESPONSIVE_LAYOUT_RUNTIME_HARNESS_PASSED` |
| `bash tools/desktop_network_runtime_harness.sh` | `DESKTOP_NETWORK_RUNTIME_HARNESS_PASSED`, including real TCP/UDP loopback and prompt cancellation |
| GitHub Actions supply-chain audit | All remote actions use full 40-character commit SHAs; checkout credentials disabled |
| GitHub Actions major-tag comparison (2026-08-03) | Every pinned SHA exactly matched its declared official major tag (`v3`, `v4`, or `v5`) |
| Android host configuration contract | Manifest permissions, backup/RTL/theme flags, launcher export, system-only TLS trust, and explicit local cleartext capability passed |
| iOS host syntax/configuration | `Info.plist` and Xcode project passed `plutil -lint`; Swift host files passed `swiftc -parse` |
| README showcase integrity | Source-faithful PNG exists and is structurally verified at 1280x820 |
| Gradle distribution checksum contract | Gradle 9.3.1 binary SHA-256 pinned and statically verified |
| Gradle wrapper JAR integrity contract | Checked-in JAR matches an approved official Gradle checksum |
| Source SHA-256 manifest | Regenerated and verified against the transformed source tree |
| Git diff whitespace validation | Passed |
| `./tools/run_quality_checks.sh --static` components | All four audits plus the core, redaction, coordinator, application, Store compile, repository compile, protocol, fuzz, responsive, and Desktop-network harnesses passed independently; long single invocations may exceed the external per-call execution limit |
| Delivery cleanup contract | Obsolete reports/logs, duplicate license/architecture copies, redundant helper, broken local links, and unused localized resources are rejected |

## Architecture properties verified

- Compose screens depend on feature intent contracts, not persistence or concrete transport adapters.
- `ToolboxStore` is a small UDF facade; navigation, diagnostics, protocol, payload, three inventory aggregates, journal, settings/backup and initial-state recovery policies are separate services.
- Persistence uses narrow application ports; only adapter conformance and the composition boundary use the full aggregate.
- Pure transport/request policy is outside `core/network`; Ktor/socket adapters depend inward on `core/policy`.
- Concrete production adapters and the owned coroutine scope are constructed only by `ToolboxComposition.kt`.
- Store state publication uses atomic flow updates; clock and ID generation are injectable, and the repository uses the injected application clock for deterministic timestamps.
- Dependency-direction checks reject adapter-to-UI/Store coupling, broad persistence shortcuts in feature services, and platform APIs in common application policy.
- Recovery evidence, persisted templates/profiles, logs and history are redacted at their owning boundaries.

## Runtime regressions fixed and covered

- MQTT connection-only operation no longer waits for an unrelated receive timeout after CONNACK.
- MQTT QoS 0 publish-only operation no longer waits for a PUBACK that the protocol does not send.
- Android and Desktop port probes close an in-flight socket promptly when their coroutine is cancelled; Android/Desktop/iOS broad socket failures explicitly preserve `CancellationException`.
- Protocol fuzzing rejects malformed MQTT, CoAP, DNS-SD, and SSDP inputs through controlled exceptions rather than unexpected crashes.

## Desktop Kotlin compile regression discovered on 2026-08-14

A connected Windows workstation reached `:composeApp:compileKotlinDesktop` and exposed eight compiler diagnostics caused by four missing adapter imports after the ports/adapters refactor. The application contracts already existed in `core/port/ProtocolClientPorts.kt`; the affected production adapters referenced the port types in their class declarations without importing them.

The source fix restores the canonical imports in `HttpToolClient.kt`, `RealtimeClients.kt`, and `CoapCodec.kt`. The static compile-regression audit now also requires these four canonical imports and verifies that each production adapter explicitly implements its expected operation-client port. The isolated protocol harness was updated to remove the adapter-only CoAP port import from its codec-only compile slice.

The Store facade compile harness now compiles the **real** `core/port` contracts and `ToolboxComposition.kt` against narrow adapter/repository compile stubs. Its former synthetic operation-client contract copy was removed because a duplicated test-only interface can mask production contract drift. Static audit now rejects reintroducing those synthetic port declarations. Generic `expect`/`actual` coverage also verifies every top-level common declaration across Android, Desktop, iOS, JS, and Wasm, accepting the intentional shared `webMain` actuals for both browser targets.

After the source fix, static/deep/architecture/UI audits and all dependency-free compile/runtime harnesses pass independently in this environment. A connected workstation rerun of `:composeApp:compileKotlinDesktop` is still required to prove the full Gradle Desktop source set after this fix.

## Desktop compiler follow-up - 2026-08-14

A connected Windows Gradle run progressed through project configuration and Compose resource generation and then reported three Kotlin language errors: `HttpToolClient`, `WebSocketToolClient`, and `MqttWebSocketClient` repeated `allowPublicCleartext = false` on overriding methods. Kotlin inherits defaults from the interface declaration and forbids redeclaring them on the override. The implementation defaults were removed; the defaults remain on `HttpOperationClient`, `WebSocketOperationClient`, and `MqttOperationClient`, so call-site behavior is unchanged.

The same log also reported a deprecated `compose.uiTooling` accessor in `androidApp`; the dependency is now declared through the version catalog as `org.jetbrains.compose.ui:ui-tooling` at the existing Compose `1.11.1` version. The Gradle JDK auto-provisioning message is an environment warning, not a Kotlin compile failure. The repository intentionally does not add an automatic JDK download resolver because its release contract requires reproducible/offline builds; use a locally installed JDK 17 as the Gradle JDK.

After these source fixes, all dependency-free audits and compile/runtime harnesses pass independently. The exact full Gradle Desktop compile must still be rerun on the connected Windows workstation before it can be marked Passed.

## Online compatibility re-check - 2026-08-14

Official Kotlin documentation still lists KGP/Kotlin `2.4.0-2.4.10` as fully supported with Gradle `7.6.3-9.5.0` and AGP `8.5.2-9.1.0`. Android's AGP 9.1 release notes specify Gradle `9.3.1` and JDK `17` for the 9.1 line. The `org.jetbrains.compose` `1.11.1` plugin remains a published JetBrains Compose release. The selected project profile therefore remains `Kotlin 2.4.10 + AGP 9.1.0 + Gradle 9.3.1 + JDK 17 + Compose 1.11.1`; no dependency bump is mixed into this compile-stabilization phase.

## Final deep KMP/build-contract stabilization - 2026-08-14

The post-override-fix tree received an additional build-surface pass focused on failures that lightweight compile slices can otherwise miss. Gradle's incubating type-safe project-accessor preview was removed because the project has only one project dependency and gains no material value from an incubating generated accessor. `androidApp` now uses the stable `project(":composeApp")` dependency form.

The duplicate JS/Wasm raw-network capability adapter was consolidated into `webMain`; JS and Wasm now contain only their target-specific `actual` factory and platform label. The shared `BrowserNetworkProbe` is compiled and executed by a dedicated runtime harness.

A new dependency-free `kmp_contract_audit.py` now checks project-owned import resolution, effective source-set declaration collisions, the exact parameter/return/suspend contract of all four protocol adapters against `ProtocolClientPorts.kt`, and the full `NetworkProbe` surface for Android, Desktop, iOS and the shared browser implementation. On the current tree it resolves 550 internal project imports and reports zero contract errors.

The repository's documented local-JDK policy is now explicit in `gradle.properties` with `org.gradle.java.installations.auto-download=false`. This prevents silent Gradle JDK provisioning; a local JDK 17 must be selected on development/CI hosts. This is a reproducibility policy and does not substitute for compiling the Gradle project.

All independent static/deep/architecture/KMP-contract/UI audits and the Store/Composition, repository, browser, protocol, 110,000-case fuzz, responsive and real Desktop-network harnesses pass on this tree. A connected workstation must still rerun the full Gradle target matrix before Build/Compile can be marked Passed.

## Gradle wrapper attempt

Command executed:

```bash
bash ./gradlew --version --stacktrace
```

The wrapper script launched successfully and attempted to download Gradle 9.3.1. The environment could not resolve `services.gradle.org`, so the wrapper stopped with `java.net.UnknownHostException` before project configuration. The distribution was not cached. This concise record replaces the previously committed generated attempt log.

This is recorded as **Blocked before project configuration**, not as a project build failure and not as a pass.

## Verification orchestration note

The official runner is sequential. Its individual commands all passed on this tree, but the hosted execution tool terminates long single calls before the complete sequence can print its final status line. This is an environment orchestration limit, not a failing project assertion; each audit and harness result above was captured independently.

## What the focused evidence proves

The dependency-free harnesses compile and execute selected current project sources for application-service policy, facade type contracts, validation, redaction, operation ordering, protocol codecs, responsive policy, parser robustness, and the real Desktop TCP/UDP adapter. They provide meaningful regression evidence without resolving the Gradle dependency graph.

They do not replace source-set compilation, Android/iOS/browser packaging, UI execution, accessibility testing, signing, or a successful CI run.

## Not executed / not verified

- Gradle settings and dependency resolution
- KMP metadata/common Gradle tests
- Desktop Gradle compilation after the 2026-08-14 override/default-argument hotfix and application visual smoke test
- Android debug/release APK or AAB, lint, R8, emulator, and physical-device runtime
- JS/Wasm tests and production bundles in a browser
- iOS simulator framework/test and signed-device runtime
- screenshot/visual-regression and accessibility runtime matrices
- resolved dependency vulnerability and license scan
- release signing, provenance, store artifacts, rollback rehearsal, and store submission
- a successful GitHub Actions run for the transformed commit

## Required connected-workstation commands

```bash
./gradlew --stop
./gradlew clean
./gradlew :composeApp:compileKotlinMetadata
./gradlew :composeApp:allTests
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:desktopTest
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease
./gradlew :androidApp:lintDebug :androidApp:lintRelease
./gradlew :composeApp:jsBrowserTest :composeApp:wasmJsBrowserTest
./gradlew :composeApp:jsBrowserProductionWebpack :composeApp:wasmJsBrowserProductionWebpack
```

On macOS with Xcode:

```bash
./gradlew :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64
```

After a connected Gradle run, regenerate the wrapper files twice with Gradle 9.3.1 and re-run the entire matrix.

## Status

```text
Source Review: Statically Reviewed
Dependency-free audits: Passed
Application architecture audit: Passed
Application facade/services + real port/composition compile harness: Passed
Persistence adapter compile harness: Passed
Focused Kotlin runtime harnesses: Passed
Gradle wrapper launch: Verified
Gradle distribution retrieval: Blocked by DNS
Gradle build and source-set tests: Not Executed
Platform runtime: Not Executed
Production Readiness: Not production-ready (production candidate after remaining gates)
```
