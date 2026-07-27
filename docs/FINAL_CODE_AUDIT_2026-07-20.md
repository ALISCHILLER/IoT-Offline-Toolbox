# Final code audit — IoT Offline Toolbox 3.6.2

Audit date: 2026-07-20  
Audit scope: complete delivered source archive  
Status: **Statically hardened; focused executable harnesses passed; not production-ready**

## Executive conclusion

The complete Kotlin Multiplatform source tree, Gradle configuration, platform source sets, Compose resources, persistence boundary, HTTP/redirect/authentication flow, raw-network adapters, protocol codecs, responsive UI policy, security redaction, backup/import paths, test sources, CI workflows, and release documentation were reviewed again from the previously delivered `Deep-Audited-Full` archive.

This final pass found and corrected additional defects that were not covered by the earlier report:

- HTTP failures occurring after a redirect reported the original URL instead of the actual failing target.
- Cross-origin `307/308` redirects could preserve and forward a request body unless explicitly blocked.
- Secret-like custom names were not handled uniformly in every header/query/form/JSON/CLI path.
- Newly saved payload templates could retain detected secret values locally even though import/export paths redacted them.
- mDNS service-type loops and SSDP receives did not share one strict operation deadline.
- WebSocket/MQTT receive loops needed one total receive-window budget and stronger managed-header/embedded-credential validation.
- Port-range expansion could consume excessive work with heavily overlapping specifications.
- Several user-facing labels still bypassed Compose Multiplatform resources.
- The Desktop network harness used a Kotlin compiler heap that could make a valid test appear hung.
- The static audit still enforced the name of an obsolete redirect helper instead of the current hardened implementation.

The source now passes every dependency-free audit and all five focused executable harnesses available in this environment. A complete Gradle build is still unverified because the Gradle wrapper distribution was not cached and `services.gradle.org` could not be resolved. No Android, iOS, browser, or full Desktop GUI runtime claim is made.

## Project inventory reviewed

```text
Project type: Kotlin Multiplatform + Compose Multiplatform
Modules: composeApp, androidApp, iosApp
Active targets: Android, Desktop JVM, iOS arm64, iOS simulator arm64, JS browser, Wasm browser
Shared UI: Compose UI in commonMain
Networking: Ktor HTTP/WebSocket plus platform TCP/UDP/discovery adapters
Persistence: Multiplatform Settings with bounded serialized documents and versioned JSON backup
Architecture: shared immutable state + ToolboxStore UDF coordinator + repository + platform adapters
Kotlin files: 76
Production Kotlin files: 61
Test Kotlin files: 15
Kotlin lines reviewed: 16,126
Test annotations present: 150
Compose string resources: 692 English / 692 Persian
Packaged source/document files before final archive generation: 169
```

## Final corrections

### 1. HTTP deadline and redirect correctness

- A total request budget is recalculated before every redirect hop.
- Request, connect, and socket timeout values are separately bounded by the remaining total budget.
- Redirect targets are validated again for scheme, host, embedded credentials, and cleartext policy.
- Cross-origin redirects are blocked when they would forward authentication, cookies, sensitive headers, or a preserved request body.
- `303`, and applicable `301/302`, rewrites to `GET` remove the body before cross-origin continuation.
- An error after one or more redirects now reports the actual `finalUrl` and sets `redirected=true`.
- Response capture remains bounded to the configured maximum plus one truncation-detection byte.

### 2. HTTP request construction and cURL evidence

- Query parameters are assembled through `URLBuilder`, preserving the correct query-before-fragment order.
- Basic, Bearer, API-key, Cookie, raw, JSON, and form modes reject conflicting or engine-managed headers.
- API-key names cannot override restricted engine headers.
- cURL output includes one connect timeout and one total timeout only.
- Secret values in URL queries, forms, JSON, headers, cookies, authentication, and custom token/key names are redacted.

### 3. WebSocket and MQTT safety

- `user:password@host` style URL credentials are rejected for HTTP, WebSocket, and MQTT-over-WebSocket.
- Engine-managed WebSocket handshake headers are rejected.
- WebSocket and MQTT receive operations use one total receive window rather than restarting the timeout for each frame.
- Frame/message and pending-buffer limits remain enforced.
- Coroutine cancellation is rethrown instead of being converted into a normal operation failure.

### 4. Port scanning and discovery

- Port specifications support aliases, presets, ranges, exclusions, and de-duplication while enforcing an expansion-work ceiling.
- The configured maximum remains consistent at `1..4096` across UI, settings normalization, Store validation, and platform probes.
- JVM results distinguish open, explicit refusal/closed, timeout/filtered, and unknown/error outcomes.
- iOS no longer labels every generic connection failure as closed.
- SSDP receive timeout is recalculated from the remaining operation deadline.
- mDNS enumeration and per-service queries share one overall deadline, preventing total runtime from multiplying by service-type count.

### 5. Persistence and privacy

- Profiles remove sensitive headers before local persistence.
- History, logs, errors, URLs, response headers, backup content, and corruption snapshots pass through mandatory redaction.
- Newly saved payload templates now redact detected secret values before local persistence and notify the operator when sanitization occurred.
- Imported templates already used the same redaction rule; normal save/import/export behavior is now consistent.
- Backups are bounded to 8 MiB, schema-validated, count-validated, normalized before durable writes, and prevented from enabling public cleartext automatically.
- Import rollback remains best-effort and is documented as a residual platform-storage risk.

### 6. Localization, RTL, and UI consistency

- English and Persian resource files contain exactly 692 matching keys.
- No locale-only key or formatting-placeholder mismatch was found.
- Legacy `tr(en, fa)` calls are absent.
- Immediate locale switching and RTL/LTR application remain platform-specific through `expect/actual` locale environments.
- Additional direct labels, including protocol-client labeling and HTTP/network/realtime evidence labels, now use Compose resources.
- Raw server payloads, protocol bytes, endpoint data, and arbitrary remote error text are intentionally not translated because changing them would corrupt technical evidence.

### 7. Source and release hygiene

- Every Kotlin package starts with `com.msa`.
- All four `expect` declarations have target implementations through Android, iOS, Desktop, and web source-set hierarchy as applicable.
- Python audit tools compile; shell harnesses pass `bash -n`; XML, JSON, and TOML files parse successfully.
- No production `TODO()`, `GlobalScope`, `printStackTrace`, or `println` was found.
- No `.env`, keystore, provisioning profile, `local.properties`, Google service secret file, or convincing hardcoded production credential was found.
- Source checksum generation and archive integrity verification are release gates.

## Verification executed

| Check | Status | Evidence |
|---|---|---|
| Deep resource/source/capability audit | Verified | 692 English + 692 Persian, zero errors |
| UI contrast/source-contract audit | Verified | 44 contrast pairs, 10 contracts, zero errors |
| Secret-redaction runtime harness | Verified | explicit pass marker |
| Core current-source runtime harness | Verified | explicit pass marker |
| Protocol codec runtime harness | Verified | explicit pass marker |
| Responsive-layout runtime harness | Verified | explicit pass marker |
| Desktop TCP/UDP loopback harness | Verified | explicit pass marker |
| Python/shell/XML/JSON/TOML syntax checks | Verified | completed successfully |
| Package-prefix and expect/actual scans | Verified | no missing/invalid result |
| Gradle metadata compilation | Blocked | wrapper distribution DNS failure before project configuration |
| Common/target Gradle tests | Not executed | Gradle could not start |
| Android build/lint/R8/runtime | Not executed | Android toolchain/device required |
| iOS framework/simulator/device runtime | Not executed | macOS/Xcode/signing/entitlement required |
| JS/Wasm production bundle/browser matrix | Not executed | Gradle/browser environment required |
| Desktop Compose GUI smoke test | Not executed | full Gradle dependency graph required |
| TalkBack/VoiceOver/keyboard runtime | Not executed | platform runtime required |

Detailed command output is stored in `docs/final-verification-logs/`.

## Gradle build attempt

Executed command:

```bash
./gradlew --offline :composeApp:compileKotlinMetadata --stacktrace
```

Observed result:

```text
Downloading https://services.gradle.org/distributions/gradle-9.1.0-bin.zip
java.net.UnknownHostException: services.gradle.org
EXIT=1
```

This failure occurred inside the Gradle wrapper before project configuration and before Kotlin compilation. It neither proves nor disproves source-set compilation. The full Gradle build remains a mandatory external gate.

## Residual risks and deferred work

1. **Preview Android toolchain** — AGP remains `9.0.0-alpha06`. It was preserved to avoid an unverified toolchain migration. A stable migration must be performed as one compatibility matrix across Android Studio, AGP, Gradle, Kotlin, Compose, KSP/plugins, and CI.
2. **No resolved dependency graph** — dependency vulnerability and license scans cannot be considered complete until Gradle resolves the actual release graph.
3. **Platform runtime gaps** — browser CORS/cookies/redirect behavior, Darwin timeout behavior, Android cleartext enforcement, iOS multicast entitlement, and signed-device sockets still require real targets.
4. **Accessibility runtime** — source-level semantics and contrast pass, but TalkBack, VoiceOver, keyboard focus, scaling, and reduced-motion behavior are not runtime-verified.
5. **Large source files** — `HttpScreen.kt`, `ToolboxStore.kt`, `CommonComponents.kt`, `RealtimeScreen.kt`, `NetworkToolsScreen.kt`, and `App.kt` remain large. Splitting them without a working full build would add unnecessary regression risk; modularization is deferred.
6. **Technical diagnostics language** — application labels and known operator messages are localized; arbitrary server/protocol/library error text remains verbatim by design.
7. **Best-effort storage rollback** — a second storage failure during rollback can still leave partial platform persistence; transactional storage would require a persistence-engine migration.

## Required release commands

On a networked workstation with JDK 17 and the supported Android toolchain:

```bash
./gradlew --stop
./gradlew clean
./gradlew :composeApp:compileKotlinMetadata
./gradlew :composeApp:allTests
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:desktopTest
./gradlew :composeApp:run
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:lintDebug
./gradlew :androidApp:lintRelease
./gradlew :composeApp:jsBrowserTest
./gradlew :composeApp:wasmJsBrowserTest
./gradlew :composeApp:jsBrowserProductionWebpack
./gradlew :composeApp:wasmJsBrowserProductionWebpack
```

On macOS with Xcode:

```bash
./gradlew :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64
```

## Final classification

```text
Deep static/source review: Verified
Focused executable harnesses: Verified
Full Gradle compilation: Blocked before project configuration
Platform UI/device runtime: Not executed
Production readiness: Not production-ready
Release classification: Statically hardened development/release candidate requiring full build and platform gates
```
