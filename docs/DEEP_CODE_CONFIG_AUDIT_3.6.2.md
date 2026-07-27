# Deep code and configuration audit — 3.6.2


> **2026-07-20 re-audit note:** This report is retained as historical evidence. Its completion/readiness statements are superseded by [`DEEP_AUDIT_REVISION_2026-07-20.md`](DEEP_AUDIT_REVISION_2026-07-20.md) and [`FINAL_AUDIT_REPORT.md`](FINAL_AUDIT_REPORT.md). The current status is **not production-ready**; full Gradle and platform verification remain blocked/not executed.


Audit date: 2026-07-18

## Scope

```text
Project type: Kotlin Multiplatform + Compose Multiplatform
Modules: composeApp, androidApp, iosApp
Targets: Android, Desktop JVM, iOS arm64/simulator arm64, JS, Wasm
Analysis scope: full source archive
```

The audit covered every Kotlin/Gradle source file, Android resources/manifest/network policy, Version Catalog, Gradle wrapper/settings/properties, GitHub Actions, Dependabot, iOS xcconfig/plist/Xcode project, browser resources, ProGuard, scripts, tests, documentation, and packaging rules.

## Corrected high-impact findings

### Gradle settings ordering

`pluginManagement` was moved to the first settings block. Repository declarations now use only official sources and module-level repositories remain forbidden.

Status: **Statically reviewed**. Full settings evaluation remains blocked by unavailable Gradle distribution DNS.

### HTTP redirect-policy bypass

Automatic redirects were disabled. A URL that passed secure/local validation can no longer silently follow to a public cleartext destination. Store success is limited to status `200..299`.

Status: **Covered by source audit and common tests; Gradle tests not executed here**.

### Concurrent Store mutation

Store mutation is serialized so two concurrent operations cannot compute state from the same stale snapshot and overwrite each other.

Status: **Statically reviewed; full coroutine tests require Gradle**.

### UDP/TCP boundary semantics

Timeout is an error rather than an empty successful UDP response. Platform readers capture one byte beyond the configured limit where possible so exact-limit data is not mislabeled as truncated and over-limit data is.

Status: **Actual Desktop loopback harness passed**; Android/iOS runtime not executed.

### Persistence boundary enforcement

Collection count, child count, field length, payload length, and total serialized document size are enforced centrally for direct writes, restored data, and backup imports. Invalid persisted values are quarantined with redacted previews. Imported backups cannot enable public cleartext.

Status: **Current-source repository tests added; Gradle test task not executed here**.

### Protocol parser hardening

- MQTT: canonical Remaining Length, overflow prevention, strict fixed flags, UTF-8, payload cap, CONNACK/SUBACK/PUBACK and packet IDs
- CoAP: datagram/option caps, option-value ranges, exact packet correlation, bounded encoding/decoding
- DNS-SD: count limits, exact RDATA boundaries, compression safety, trailing-byte rejection
- SSDP: successful response status only, bounded token-valid headers, control-character and injection rejection

Status: **Independent current-source protocol harness passed**.

### Payload expansion and codecs

Variable expansion is single-pass and output-bounded. Base64 padding is strict. HEX prefix recognition is token-boundary aware. Byte conversions share a 1 MiB cap.

Status: **Independent current-source core harness passed**.

### Cancellation and resource lifecycle

Kotlin cancellation is rethrown in iOS TCP/UDP/reachability paths and Ktor cleanup. Selectors, sockets, multicast locks, and multicast sockets are closed on normal completion and setup failure.

Status: **Statically reviewed; signed-device runtime required**.

### Diagnostic redaction

Credentials, query secrets, authorization/cookie/API-key headers, private-key blocks, URLs, response headers, close reasons, and errors are redacted before UI/durable state.

Status: **Independent core harness and source audit passed**.

## Configuration findings and final state

| Area | Final state | Evidence status |
|---|---|---|
| Gradle wrapper | 9.1.0 HTTPS distribution with official pinned SHA-256 and timeout | Verified statically |
| Plugin repositories | Google, Maven Central, Plugin Portal | Verified statically |
| Dependency repositories | Google and Maven Central; project repos rejected | Verified statically |
| JVM/toolchain | Java/Kotlin 17 | Verified statically |
| Android | SDK 36, min 24, release minify/shrink, Auto Backup disabled | Verified statically |
| Cleartext | OS layer enabled for local embedded devices; shared policy blocks public cleartext by default | Verified statically and harnessed |
| iOS | Bundle/version centralized; local-network usage disclosure; no committed signing team | Verified statically |
| CI | Audit, wrapper, dependency review, Desktop, Android, browser, iOS jobs | Verified statically; workflow run not available |
| CI static reproducibility | `--audit` has no `kotlinc` dependency | Verified by execution |
| Version | 3.6.2 / build 38 across central properties, Android, iOS, Desktop, About/backup | Verified statically |
| Secrets/signing | No packaged environment, key, keystore, provisioning, or local property file | Verified during packaging |

## Remaining release gates

The following were not executed in this delivery environment:

- Gradle dependency resolution and target compilation
- Android debug/release APK/AAB, lint, R8, emulator/device runtime
- iOS simulator framework/test and signed-device local networking
- JS/Wasm browser tests and bundles
- screenshot matrix, TalkBack, VoiceOver, keyboard-only navigation, real-display contrast
- signing, store metadata, store upload, production rollback drill

## Classification

```text
Production candidate — full Gradle, visual runtime, physical-device,
accessibility, signing, and store verification required
```


## 3.6.2 toolchain compatibility addendum

The application source and runtime behavior remain those reviewed in the deep 3.6.0 audit. The 3.6.2 patch corrects the build-tool compatibility baseline:

- Android Gradle Plugin: `9.0.0-alpha06`
- Gradle: `9.1.0`
- Kotlin: `2.4.10`
- Compose Multiplatform: `1.11.1`
- JDK: `17`

The prior `AGP 9.2.1 / Gradle 9.6.1` pair was removed because it exceeded both the reported Android Studio ceiling and the documented Kotlin Multiplatform 2.4.10 compatibility range. Full Gradle resolution remains blocked in the delivery environment by DNS and must be repeated locally.
