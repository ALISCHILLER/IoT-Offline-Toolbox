# Professional completion report — IoT Offline Toolbox 3.6.2


> **2026-07-20 re-audit note:** This report is retained as historical evidence. Its completion/readiness statements are superseded by [`DEEP_AUDIT_REVISION_2026-07-20.md`](DEEP_AUDIT_REVISION_2026-07-20.md) and [`FINAL_AUDIT_REPORT.md`](FINAL_AUDIT_REPORT.md). The current status is **not production-ready**; full Gradle and platform verification remain blocked/not executed.


Date: 2026-07-19  
Project type: Compose Multiplatform / Kotlin Multiplatform  
Architecture: shared UDF-style store with common protocol/domain/UI code and platform adapters  
Active targets: Android, iOS arm64/simulator arm64, Desktop JVM, JS browser, Wasm browser  
Analysis scope: complete uploaded source archive

## Delivery summary

The original archive had sound page structure but a partial translation mechanism and shallow operator controls in several workbenches. This pass changed 38 existing files and added 9 source/resource files plus this completion report, without deleting project files or changing package/application identifiers.

## Completed areas

### Localization and RTL

- Replaced all remaining manual `tr(en, fa)` calls in shared UI.
- Added `commonMain/composeResources/values/strings.xml` and `values-fa/strings.xml`.
- English/Persian key count: 687 / 687.
- Missing referenced keys: 0.
- Added dynamic locale implementations for Android, iOS, Desktop, and shared JS/Wasm web.
- Language and layout direction apply and persist immediately.
- Persian selects RTL by default; the operator can override direction in Settings.

### HTTP workbench

- Methods: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS.
- Query editor with UTF-8 percent encoding.
- Header and cookie editors with bounded parsing and control-character rejection.
- Authentication: None, Basic, Bearer, API key.
- Body modes: None, Raw, JSON, `application/x-www-form-urlencoded`.
- Payload-template loading and saved profile/history shortcuts.
- Per-request connect/request/socket timeout.
- Optional redirects with a hard limit of 10.
- Per-hop scheme, host, and cleartext-policy validation.
- Cross-origin redirect blocking when credentials, sensitive headers, or cookies are present.
- Embedded URL credentials rejected.
- Engine-managed `Host`, `Content-Length`, `Transfer-Encoding`, and `Connection` headers rejected.
- Redacted cURL preview covering headers, cookies, auth, sensitive query values, form values, and JSON/raw bodies.
- Bounded response channel, truncation evidence, header redaction, formatted JSON/raw output, final URL, content type, status, elapsed time, and byte count.

### Port and socket tooling

- Port numbers, ranges, comma/semicolon/whitespace input, and exclusions using `!`.
- Service aliases for common web, remote access, directory, database, IoT, messaging, mail, telemetry, container, and industrial protocols.
- Presets: Recommended, Web, IoT, Remote, Databases, Messaging, Mail, Well-known.
- Configurable maximum port count.
- Result states: Open, Closed, Filtered, Error.
- Result counters and filters with service hints and latency/error evidence.
- TCP/UDP common-port shortcuts and payload templates.
- Browser targets return explicit unsupported/error states instead of misleading closed-port states.

### Settings and adaptive UI

- System theme or manual light/dark mode.
- Compact mode wired into shared page/card/action/field spacing.
- English/Persian, RTL, and advanced-options visibility.
- Default HTTP timeout and redirect policy.
- Default network timeout, concurrency, maximum ports, discovery hosts, and response bytes.
- History/log retention and public-cleartext policy.
- Immediate persistence for language/appearance; staged validation and save for numeric/safety controls.
- Existing drawer/rail/sidebar and responsive pane architecture retained.

### Security and privacy hardening

- Response/request evidence remains bounded.
- Durable histories use redacted cURL rather than raw credentials.
- Query, body, cookie, authorization, API-key, and URL-userinfo secret paths are covered.
- Redirects are manual on all Ktor engines, with origin-aware credential boundaries.
- Public cleartext remains disabled unless explicitly enabled.

## Compatibility preserved

- Package prefix and namespaces remain under `com.msa`.
- Android application ID and iOS bundle identifiers were not changed.
- Existing Ktor, Compose, Multiplatform Settings, serialization, and coroutine stacks were retained.
- Toolchain versions were not upgraded.
- Persistence schema version and existing serialized defaults remain backward compatible through default fields and repository normalization.

## Verification matrix

| Check | Status | Evidence |
|---|---|---|
| XML/JSON/TOML/YAML/config static audit | Verified | `tools/static_audit.py` |
| UI contrast and source contracts | Verified | 44 contrast pairs, 9 contracts |
| English/Persian resource parity | Verified | 687/687, no missing `Res.string` reference |
| Core security/parser/payload runtime harness | Verified | `FINAL_CORE_RUNTIME_HARNESS_PASSED` |
| MQTT/CoAP/DNS-SD/SSDP harness | Verified | `PROTOCOL_RUNTIME_HARNESS_PASSED` |
| Responsive-layout harness | Verified | `RESPONSIVE_LAYOUT_RUNTIME_HARNESS_PASSED` |
| Actual Desktop TCP/UDP loopback harness | Verified | `DESKTOP_NETWORK_RUNTIME_HARNESS_PASSED` |
| Full Gradle/KMP compilation | Blocked | Gradle wrapper distribution cannot resolve `services.gradle.org` in this environment |
| Android/iOS/Web/Desktop visual runtime | Not executed | Requires platform toolchains/devices/browser runtime |
| Signed release/store validation | Not executed | Requires signing identities and store accounts |

## Production statement

Status: **Statically hardened production candidate; build verification required.**

No claim is made that all platform binaries compile or run until the Gradle matrix and platform smoke tests are executed in a networked development environment.
