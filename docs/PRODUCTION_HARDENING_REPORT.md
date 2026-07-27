# Production hardening report — 3.4.0


> **2026-07-20 re-audit note:** This report is retained as historical evidence. Its completion/readiness statements are superseded by [`DEEP_AUDIT_REVISION_2026-07-20.md`](DEEP_AUDIT_REVISION_2026-07-20.md) and [`FINAL_AUDIT_REPORT.md`](FINAL_AUDIT_REPORT.md). The current status is **not production-ready**; full Gradle and platform verification remain blocked/not executed.


## Scope

This transformation addressed the high/medium findings from the 3.1.1 audit without replacing the established UDF architecture, Ktor stack, persistence engine, navigation model, package identifiers, or Apache-2.0 license.

## Corrected high-risk findings

| Finding | Resolution | Evidence status |
|---|---|---|
| inconsistent release versions | centralized 3.4.0 / build 34 and audit enforcement | Verified statically |
| static audit overstated as build evidence | explicit `--static`, `--jvm-android`, `--web`, `--ios`, `--all` gates | Verified statically |
| silent persistence corruption | redacted quarantine + recovery event + tests | Core harness / source reviewed |
| MQTT rejected/unacknowledged operations reported connected | strict SUBACK/PUBACK parsing, packet-ID matching, acknowledgement result | Core harness / source reviewed |
| multiplied reachability timeout | one deadline across fallback ports | Source reviewed; Desktop implementation exercised |
| unbounded public cleartext behavior | application transport gate, local warning, public block, explicit override | Core harness verified |

## Additional hardening

- strict CR/LF header injection rejection;
- Android/Desktop multicast interface selection;
- bilingual primary UI and effective RTL;
- adaptive narrow/wide technical forms;
- accessibility semantics for navigation/status/headings/capabilities;
- expanded shared/persistence/security/network tests;
- release/lint/browser/iOS CI tasks and artifact/report capture;
- full license text, setup, architecture, release checklist, and third-party review note;
- review-first Gradle recovery with backup and targeted cache handling;
- controlled persistence-write failures that preserve in-memory state and emit non-recursive ephemeral diagnostics instead of crashing;
- lazy current-key-first legacy migration so a valid current record never reads or quarantines a corrupt legacy fallback;
- corrected DNS-SD query-type test decoding to validate the full 16-bit field.

## Responsive and adaptive hardening

- centralized compact/medium/expanded window policy in `commonMain`;
- modal drawer, scrollable navigation rail, and persistent sidebar shells;
- low-height landscape density and rail fallback for short desktop windows;
- container-width-driven fields, cards, buttons, and dual panes;
- safe-drawing insets for Android edge-to-edge rail/sidebar modes and shared IME padding for focused forms;
- scrollable translated tabs and bounded ultra-wide content;
- Desktop minimum resize bounds;
- responsive policy unit tests and an independent execution harness.

Detailed evidence and the required visual matrix are documented in `docs/RESPONSIVE_IMPLEMENTATION.md`.

## UI/UX hardening added in 3.4.0

- centralized light/dark Material 3 design system, typography, shapes, icons, and semantic state tokens;
- grouped icon-based drawer/rail/sidebar navigation;
- Snackbar notice channel and dedicated modal error channel;
- full dashboard and operational-screen hierarchy redesign;
- consistent validation, capability, empty, success, warning, error, destructive, and cancellation states;
- selectable bounded technical evidence and responsive local action fallbacks;
- removal of no-op clickable metadata affordances;
- deterministic audit of 44 WCAG color pairs and nine UI source contracts.

See `docs/UI_UX_DEEP_REDESIGN.md`.

## Behavior preserved

- application ID, namespace, Bundle ID, framework name, and package hierarchy;
- local-first/no-backend operation;
- existing device/profile/template/history/backup concepts;
- browser raw-socket restrictions;
- iOS SSDP/mDNS disabled pending device entitlement verification;
- MQTT QoS 0/1 and CoAP one-shot diagnostic scope;
- Apache License 2.0.

## Verification limitations

The delivery environment could not resolve the Gradle distribution host. Consequently, the project cannot honestly be classified as a fully built, signed, runtime-verified production release here. The source and independent harness evidence support a **Production candidate — Build verification required** classification.

## 3.5.0 final audit extension

- corrected IPv6/local-host transport classification and unified URL validation;
- removed live third-party endpoint defaults;
- preserved iOS structured cancellation;
- bounded parser configuration inputs;
- added current-source core execution and CI enforcement;
- added a complete final audit record in `FINAL_AUDIT_REPORT.md`.

Status: Production candidate; full Gradle, device, visual, accessibility, signing, and store verification remain required.
