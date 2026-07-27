# Changelog

## 3.6.2 Compose resource generation compile fix - 2026-07-27

### Desktop/KMP compile correction

- Forced Compose Multiplatform resource accessor generation with `generateResClass = always`.
- Fixed the project-wide `Unresolved reference resources` / `Unresolved reference Res` failure seen by `:composeApp:compileKotlinDesktop`.
- Preserved the public generated package `com.msa.iotofflinetoolbox.resources`.
- Added static and deep-audit contracts so the project cannot silently return to dependency-shape-based `auto` generation.
- Added `tools/regenerate-compose-resources-windows.ps1` for deterministic clean regeneration and optional Desktop compilation.

### Verification status

- The attached workstation log proves Gradle reached Desktop Kotlin compilation and that the failure was the missing generated `Res` source set.
- Source/resource/UI audits and focused runtime harnesses remain executable after the Gradle configuration fix.
- A successful full Gradle compilation must be re-run on the workstation because this environment cannot download the Gradle distribution.


## 3.6.2 complete professional finalization - 2026-07-26

### Cancellation, concurrency and stale-state safety

- Added a dedicated `OperationCoordinator` that serializes user-triggered operations, keeps cancelled jobs in the operation tail until cleanup completes, and prevents obsolete operations from publishing late state into a newer screen.
- Corrected the manual-cancellation race where clearing the active job too early could let a successor start before the previous socket operation had actually released its resources.
- Added a cancellable JVM socket bridge for Android and Desktop. Coroutine cancellation now closes the active `Socket` or `DatagramSocket`, allowing blocked reads to terminate promptly instead of waiting for the configured network timeout.
- Added executable regression coverage proving successor ordering, single cancellation reporting, stale-result suppression, TCP cancellation latency, peer closure, and TCP/UDP loopback behavior.

### Binary protocol evidence and persistence hardening

- Made TCP and UDP response rendering binary-safe across Android, Desktop, and iOS; raw bytes remain available as HEX and malformed/binary data is no longer coerced into misleading replacement-character text.
- Made MQTT PUBLISH decoding content-aware with bounded binary HEX evidence and explicit binary/truncated metadata.
- Added CoAP Content-Format mapping for text, link-format, XML, octet-stream, EXI, JSON, CBOR, and unknown formats before response decoding.
- Hardened persisted-device, endpoint-profile, template, history, and log validation. Invalid local records are quarantined instead of silently entering runtime state.
- Preserved independent best-effort rollback for every backup collection so a failed restore of one section cannot skip restoration attempts for the others.
- Replaced the iOS TCP `MutableList<Byte>` accumulator with a fixed bounded `ByteArray`, avoiding per-byte boxing while preserving the same response and truncation limits.

### Verification status

- Deep resource/source/capability audit: passed, 704 / 704 locale keys, zero errors.
- UI audit: passed, 44 contrast pairs and 13 source contracts.
- Six executable harnesses passed independently: core, secret redaction, operation coordinator, protocol, responsive layout, and real Desktop TCP/UDP/cancellation.
- Independent scan: 81 Kotlin files, 16,947 Kotlin lines, 166 `@Test` annotations, zero production `TODO()`, `GlobalScope`, non-null assertions, package-prefix violations, or missing `actual` names.
- Full Gradle compilation remains blocked before project configuration because neither the wrapper host nor the direct GitHub distribution host resolves in this environment.
- Android/iOS/Desktop UI/JS/Wasm runtime, accessibility, signing, and store release remain required before any production claim.


## 3.6.2 ultimate professional completion - 2026-07-26

### HTTP correctness, evidence and maintainability

- Added a single request-validation boundary shared by the HTTP UI, Store and transport client, covering URL credentials, cleartext policy, methods, timeouts, redirects, headers, browser restrictions, cookies, authentication, body formats and a 1 MiB request-body limit.
- Retained exact bounded HTTP response bytes and added strict UTF-8 validation so malformed or declared-binary bodies are presented safely through HEX/Base64 instead of corrupted text.
- Added redacted Bash and PowerShell cURL previews with shell-correct quoting, query-before-fragment handling and regression coverage.
- Added regression tests for text/binary response classification, exact response bytes, API-key conflicts, browser capability policy, request-size limits and shell quoting.
- Strengthened backup-import recovery so every persisted snapshot is restored independently after a write failure.
- Expanded UI quality contracts for shared HTTP validation, binary evidence views and dual-shell previews.
- Increased exact Compose resource parity to 704 English / 704 Persian keys.

### Verification status

- Deep resource/source/capability audit: passed, 704 / 704 locale keys, zero errors.
- UI audit: passed, 44 contrast pairs and 13 source contracts.
- Core, protocol, responsive, Desktop loopback-network and secret-redaction harnesses: passed independently.
- Full Gradle compilation remains blocked before project configuration because the Gradle distribution host is unavailable in this environment.
- Platform runtime, accessibility, signing and store verification remain required before any production claim.



## 3.6.2 Ktor channel cancellation compile hotfix - 2026-07-21

### Compile fix

- Updated both `ByteReadChannel.cancel()` calls in `HttpToolClient.kt` to the Ktor 3.5-compatible `cancel(null)` signature.
- Preserved redirect cleanup and bounded-response cleanup behavior without introducing a cancellation failure cause.
- Re-ran deep resource, UI, secret-redaction, core, protocol, responsive-layout, and Desktop network harnesses.

### Verification status

- Source and focused executable audits pass.
- Full Gradle compilation must still be confirmed on the user's workstation because this environment cannot resolve the Gradle distribution host.


## 3.6.2 final code audit revision - 2026-07-20

### Additional correctness and privacy fixes

- Applied one total HTTP timeout budget across redirect hops and blocked cross-origin redirects that would preserve credentials, cookies, sensitive headers, or request bodies.
- Corrected failed redirected requests so `finalUrl` identifies the actual failing target and `redirected` remains accurate.
- Rejected embedded URL credentials and engine-managed WebSocket handshake headers across HTTP/WebSocket/MQTT paths.
- Added bounded WebSocket/MQTT receive windows, port-spec expansion work limits, and globally bounded SSDP/mDNS discovery deadlines.
- Unified custom secret-name detection across headers, query strings, forms, JSON, command-line text, profiles, history, logs, cURL, and backups.
- Prevented newly saved payload templates from persisting detected secret values locally.
- Corrected remaining direct UI labels and increased exact resource parity to 692 English / 692 Persian keys.
- Updated runtime harness memory settings, source audits, regression tests, verification records, and release documentation.

### Verification status

- Deep audit: passed, 692 / 692 locale keys, zero errors.
- UI audit: passed, 44 contrast pairs and 10 source contracts.
- Core, protocol, responsive, Desktop loopback-network, and secret-redaction harnesses: passed independently.
- Full Gradle compilation remains blocked before project configuration by an unavailable Gradle distribution host.
- The project remains not production-ready until Gradle, platform runtime, accessibility, signing, and release gates pass.


## 3.6.2 deep re-audit revision - 2026-07-20

### Correctness and security fixes

- Fixed Kotlin source corruption discovered in `ToolboxStore.kt` and added a scanner for multiline ordinary-string corruption and stray apostrophes.
- Completed localization of user-facing Store operations and increased exact Compose resource parity to 687 English / 687 Persian keys. Raw protocol payloads and server-originated technical data remain unmodified by design.
- Added separate HTTP request/connect/socket timeout models and platform capability gates; unsupported controls are no longer presented as effective on Darwin or browser engines.
- Added browser-forbidden request-header validation, browser cookie/WebSocket-header restrictions, and store-level enforcement independent of UI validation.
- Expanded mandatory secret detection to custom header names such as `X-Client-Secret` and `X-Custom-Token`, including profile, cURL, redirect, log, and generic-history paths.
- Fixed duplicate cURL timeout switches and added executable regression coverage.
- Made the saved port-scan limit consistent with the UI and validation boundary (`1..4096`) instead of accepting 65535 and silently clamping it later.
- Removed the ineffective user-configurable redaction flag; redaction is always enabled.
- Improved port-result classification: JVM timeout -> filtered, explicit refusal -> closed, unknown failure -> error; iOS no longer reports every generic failure as closed.
- Documented the residual Android OS-level cleartext risk and the browser/CORS capability boundaries.

### Verification status

- Deep resource/capability/source audit: passed, 687 / 687 locale keys, 0 reported errors.
- UI audit: passed, 44 contrast pairs and 9 source contracts.
- Core, protocol, responsive-layout, Desktop loopback-network, and dedicated secret-redaction harnesses: passed.
- Full Gradle compilation remains blocked before project configuration because the wrapper distribution host is unavailable in this environment.
- Android, iOS, Desktop UI, JS/Wasm browser runtime, accessibility, signing, and store release are not declared verified.


## 3.6.2 - 2026-07-19

### Professional completeness and localization pass

- Replaced the partial `tr(en, fa)` helper with generated Compose Multiplatform resources: 687 English and 687 Persian entries with exact key parity.
- Added platform locale environments for Android, iOS, Desktop, and shared JS/Wasm web targets, including immediate RTL/LTR and resource refresh.
- Expanded the HTTP workbench with query parameters, cookies, Basic/Bearer/API-key authentication, raw/JSON/form bodies, payload templates, separate request/connect/socket timeout controls, redirect policy, redacted cURL, formatted response views, and richer response metadata.
- Added manual bounded redirect handling that revalidates every hop, blocks credential-bearing cross-origin redirects, rejects embedded URL credentials, and prevents manual engine-owned headers.
- Expanded port scanning with service aliases, exclusions, ranges, eight presets, configurable scan limits, service hints, and open/closed/filtered/error result filtering.
- Expanded TCP/UDP consoles with common protocol port shortcuts and payload templates.
- Rebuilt settings around system/manual theme, compact density, language, RTL, advanced controls, HTTP defaults, scan/response limits, history/log retention, and cleartext policy.
- Added secret-aware query/form/JSON cURL redaction and strict UTF-8 percent encoding.
- Added common tests for HTTP request construction, redirect behavior, credential boundaries, port aliases, parser limits, and resource parity checks.
- Preserved package IDs, application IDs, persistence schema version, protocol contracts, and current toolchain versions.

### Verification status

- Dependency-free source/configuration/UI audits and all five focused runtime harnesses pass.
- English/Persian resource parity and all `Res.string` references pass.
- Full Gradle compilation is blocked before configuration because this environment cannot resolve the Gradle distribution host; platform builds remain required on a networked workstation.

## 3.6.2 - 2026-07-18

### Compile correctness hotfix

- Removed obsolete explicit imports of the scoped Compose `weight` modifier.
- Moved localized semantics text out of the non-composable semantics lambda.
- Added the required Material 3 experimental API opt-in for the mobile top app bar.
- Corrected the Ktor `readBuffer` argument to the supported `Int` overload.
- Added a typed-list overload for dynamically generated composable card grids.
- Corrected MQTT capability references to `mqttWebSocketClient`.
- Suppressed expected disabled iOS target warnings on non-macOS hosts.
- Added static regression checks for every compiler error fixed in this release.


## 3.6.2 - 2026-07-18

### Android Studio compatibility hotfix

- Pinned Android Gradle Plugin to `9.0.0-alpha06`, matching the maximum version reported by the installed Android Studio.
- Changed the Gradle wrapper distribution from `9.6.1` to `9.1.0` and pinned the official SHA-256 checksum.
- Kept Kotlin `2.4.10`, Compose Multiplatform `1.11.1`, SDK 36, application identifiers, source code, UI, persistence schema, and protocol behavior unchanged.
- Added a reproducible toolchain compatibility audit so unsupported AGP/Gradle combinations fail the project audit.
- Added `docs/ANDROID_STUDIO_AGP_COMPATIBILITY.md` with upgrade and recovery guidance.

> This package is an IDE compatibility build because AGP `9.0.0-alpha06` is a preview. For the stable production baseline, update Android Studio and use stable AGP `9.1.0` with Gradle `9.3.1`.

## 3.6.0 - 2026-07-18

### Deep code and configuration audit

- Corrected `settings.gradle.kts` ordering so `pluginManagement` is the first settings block.
- Centralized official repositories and kept project repositories forbidden.
- Split quality execution into dependency-free `--audit` and compiler-backed `--static` modes so CI does not assume `kotlinc` is preinstalled.
- Corrected Android KMP host-test CI to execute `testAndroidHostTest` and installed Android SDK 36 explicitly.
- Added a reproducible current-source Desktop TCP/UDP runtime harness.

### Protocol and transport correctness

- Disabled automatic HTTP redirects and classified only 2xx responses as success.
- Hardened URL authority parsing, bracketed IPv6 handling, port validation, local/public cleartext classification, and displayed URL redaction.
- Enforced strict custom-header names, duplicate rejection, control-character rejection, and input bounds.
- Hardened MQTT fixed headers, canonical Remaining Length, UTF-8, payload caps, CONNACK/SUBACK/PUBACK, QoS, and packet-ID validation.
- Hardened CoAP datagram/option counts, option values, correlation, exact decoding, and response mapping.
- Hardened DNS-SD count/RDATA/name parsing and rejected trailing or overlong packet data.
- Hardened SSDP status/header parsing and rejected malformed or injected responses.
- Converted TCP/UDP timeout and truncation behavior into explicit bounded results.
- Preserved coroutine cancellation in iOS networking and ensured multicast/socket cleanup on failure.

### Persistence, payload, and privacy

- Serialized Store mutations to prevent lost updates under concurrent operations.
- Added central collection, field, payload, and 8 MiB persisted-document limits for direct writes, restores, and imports.
- Added corruption quarantine and validation of decoded persisted data.
- Prevented backup imports from enabling public-cleartext override.
- Added strict Base64 padding, safer HEX prefix handling, bounded variable expansion, and unified 1 MiB payload limits.
- Redacted credentials, tokens, cookies, authorization headers, response headers, URLs, close reasons, and errors before entering UI/durable state.

### Platform and UI configuration

- Synchronized Android status/navigation bar icon appearance with application dark mode.
- Retained the adaptive drawer/rail/sidebar, low-height, split-screen, IME, edge-to-edge, RTL, and Material 3 design system.
- Centralized release version `3.6.0` and build `36` across Android, iOS, Desktop, About, and backup metadata.

### Verification status

- Dependency-free source/configuration/UI audit passed.
- Core, protocol, responsive, and actual Desktop network harnesses passed.
- Full Gradle target compilation remains blocked in the delivery environment before project configuration because `services.gradle.org` cannot be resolved.
- Visual runtime, signed-device networking, assistive-technology, signing, and store delivery remain external release gates.

## 3.5.0 - 2026-07-18

### Final security, correctness, and release audit

- Centralized case-insensitive URL normalization and scheme validation for HTTP, WebSocket, and MQTT-over-WebSocket paths.
- Fixed local-host classification so public DNS names beginning with `fc` or `fd` are not mistaken for IPv6 ULA addresses.
- Added precise IPv6 ULA (`fc00::/7`) and link-local (`fe80::/10`) checks, zone-aware handling, bracketed IPv6 parsing, user-info removal, and port-safe host extraction.
- Reused the shared host parser in SSDP-to-device persistence instead of a separate IPv4-oriented parser.
- Preserved structured cancellation in iOS TCP, UDP, and reachability socket flows.
- Removed prefilled third-party HTTP, WebSocket, and MQTT endpoints; local examples are now non-submitting placeholders.
- Added defensive bounds for custom header counts and port-parser limits before input expansion.
- Added regression tests for transport normalization, public-host false positives, IPv6 ranges, bracketed addresses, and invalid parser limits.
- Added a dependency-free runtime harness that compiles and executes the current transport security, header, IPv4/CIDR, port, and redaction sources.
- Extended the static audit and CI to enforce transport, cancellation, endpoint-default, and runtime-harness contracts.
- Centralized release version `3.5.0` with build number `35` across Android, iOS, Desktop, About, and backup metadata.

### Verification status

- Static/source/configuration audits and the independent current-source core harness were executed successfully.
- Full Gradle target compilation remains blocked in the delivery environment before project configuration because the Gradle distribution host cannot be resolved.
- Android/iOS/browser visual runtime, assistive-technology, signing, and store delivery remain explicit external release gates.

## 3.4.0 - 2026-07-15

### UI/UX deep redesign

- Added a centralized MSA Material 3 light/dark design system with semantic success, warning, and information tokens.
- Added a full typography hierarchy, shared shapes, icon mapping, and reusable section/status/metric/action components.
- Replaced text-glyph navigation with grouped Material icon navigation across drawer, rail, and sidebar shells.
- Replaced intrusive success dialogs with Snackbar feedback while retaining a dedicated blocking error dialog.
- Rebuilt the dashboard as an operator command center with metrics, workflows, capabilities, privacy guidance, and recent activity.
- Reworked every operational screen for clearer request/result hierarchy, validation, platform capability feedback, and selectable technical evidence.
- Added consistent loading, empty, disabled, success, warning, error, destructive, and cancellation presentation.
- Added non-interactive information pills and removed no-op chip affordances.
- Added narrow-container fallback for status actions and selectable key/value evidence.
- Added a deterministic UI quality audit covering 44 WCAG color pairs and nine source-level interaction/layout contracts.
- Centralized release version `3.4.0` with build number `34`.

## 3.3.0 - 2026-07-15

### Responsive and adaptive UI

- Added one shared responsive window profile for compact, medium and expanded widths.
- Added explicit low-height landscape handling for phones, tablets and resizable desktop windows.
- Added adaptive drawer, scrollable navigation rail and desktop sidebar modes.
- Added bounded maximum content width for very large displays.
- Converted fixed page padding, card padding and vertical density to responsive values.
- Added responsive field rows that automatically choose one, two or more columns from available width.
- Added responsive card grids and request/response panes for settings, HTTP and backup workflows.
- Made discovery, realtime and profile tabs horizontally scrollable.
- Reduced multiline editor height in low-height windows while preserving scroll access.
- Added responsive tests for phone portrait, compact-landscape boundaries, split-screen, tablet portrait/landscape, sidebar boundaries, short desktop and ultra-wide layouts.
- Added safe-drawing insets for edge-to-edge rail/sidebar shells and shared IME padding for focused forms.
- Set a practical resizable Desktop window baseline and minimum size.


## 3.2.0 - 2026-07-15

### Release coherence

- Centralized the application version as `3.2.0` with build number `32`.
- Aligned Android `versionName/versionCode`, Desktop package version, iOS marketing/build versions, About UI, and backup metadata.
- Added deterministic version-coherence checks to the static release audit.

### Reliability

- Replaced silent persistence fallback with redacted corrupted-record quarantine and one-time recovery notices.
- Added normalization for restored settings and enforced mandatory durable redaction.
- Changed reachability fallback to honor one overall deadline instead of multiplying the requested timeout per probe port.
- Selected active multicast-capable interfaces for Android/Desktop mDNS joins.
- Converted durable write failures into controlled UI errors with in-memory continuity and non-recursive ephemeral diagnostics.
- Made legacy-key migration lazy so valid current data is never displaced or falsely quarantined by a corrupt legacy fallback.

### MQTT correctness

- Added strict MQTT 3.1.1 SUBACK and PUBACK parsing.
- Rejected failed subscriptions, unsupported granted QoS, zero/mismatched packet IDs, and missing QoS 1 acknowledgements.
- Separated transport connection state from operation acknowledgement state.
- Kept inbound QoS 1 PUBACK, fragmentation handling, and bounded keep-alive behavior.

### Transport security

- Added a shared transport policy for HTTP, WebSocket, and MQTT-over-WebSocket.
- Allowed cleartext only for local/private destinations by default and surfaced a security warning.
- Blocked public cleartext destinations unless the operator explicitly enables the unsafe override.
- Kept HTTPS/WSS unrestricted by the application policy.

### UI, localization, and accessibility

- Added English/Persian language selection and effective RTL behavior.
- Localized primary navigation, settings, diagnostics, discovery, HTTP, realtime, payload, inventory, logs, guide, and profile workflows.
- Added adaptive field/action layouts for narrow and wide windows.
- Added semantic headings, labeled menu actions, minimum touch targets, operation live-region semantics, and capability state descriptions.

### Testing and delivery

- Expanded common tests for transport policy, persistence corruption recovery, current-over-legacy precedence, MQTT acknowledgements, headers, HTTP, and protocol codecs.
- Corrected the DNS-SD query test to decode the complete two-byte record type.
- Expanded CI to include wrapper validation, dependency review, Android debug/release/lint/unit tasks, Desktop/shared tests, browser tests/bundles, and iOS simulator tasks.
- Reworked quality scripts so static review is never reported as a successful Gradle build.
- Added production hardening, setup, architecture, release checklist, recovery, and verification documentation.
- Changed the Windows Gradle mirror repair utility to review-only by default; `-Apply` is now required to modify user-level Gradle configuration.

## 3.1.1 - 2026-07-15

- Centralized official Google Maven, Maven Central, and Gradle Plugin Portal repository declarations.
- Added diagnosis and recovery tooling for user-level Gradle init scripts that inject failing Aliyun mirrors.
- Added targeted dependency-cache cleanup and repository recovery documentation.

## 3.1.0 - 2026-07-15

- Added SSDP/UPnP and Bonjour/mDNS/DNS-SD discovery.
- Added WebSocket, MQTT 3.1.1 over WebSocket, CoAP, TCP, UDP, profiles, payload templates, backup, redaction, and iOS Ktor Network adapters.
- Corrected the AGP 9 Android-KMP target to `androidLibrary`.
- Upgraded the baseline to Kotlin 2.4.10, Compose Multiplatform 1.11.1, AGP 9.2.1, Gradle 9.6.1, and Ktor 3.5.1.

## 2.0.0 - 2026-07-14

- Replaced the generated Compose sample with the initial multiplatform IoT diagnostics workbench.
