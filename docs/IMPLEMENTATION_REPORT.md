# Implementation report — 3.6.2

Date: 2026-07-18

## Release identity

```text
Brand: MSA
Developer: ALISCHILLER
Version: 3.6.2
Build: 37
Package prefix: com.msa
```

## Purpose of this release

Version 3.6.2 is an Android Studio/AGP compatibility hotfix on top of the deep correctness and configuration hardening delivered in 3.6.0. It preserves the existing feature set, responsive design system, application identifiers, backup schema, and navigation model while strengthening boundaries that static UI-oriented audits did not previously cover.

## Implemented changes

### Build and configuration

- pinned AGP to `9.0.0-alpha06`, exactly matching the maximum reported by the installed Android Studio
- pinned Gradle to `9.1.0` with the official binary distribution checksum
- added an explicit toolchain compatibility gate to the dependency-free audit
- corrected Settings DSL ordering
- centralized official repositories and forbidden module repositories
- pinned Gradle distribution checksum and timeout
- kept configuration cache disabled until verified compatible
- enabled Android KMP host tests and corrected CI task selection
- made static CI independent of a preinstalled Kotlin compiler
- installed Android SDK 36 explicitly in CI

### Transport and protocol code

- disabled redirects and enforced 2xx-only HTTP success
- strict authority/IPv6/port/local-host parsing
- redacted display URLs, errors, headers, and close reasons
- strict header duplicates/control characters/counts
- MQTT, CoAP, DNS-SD, and SSDP defensive bounds and canonical parsing
- explicit TCP/UDP timeout and truncation behavior
- cancellation-safe iOS network paths
- multicast/socket cleanup on failure

### Persistence and payload

- serialized Store mutations
- bounded collections, children, fields, payloads, and documents
- validation on direct write, restore, and import
- corrupted-value quarantine with redacted preview
- backup imports cannot enable unsafe public cleartext
- strict Base64 and HEX parsing
- bounded single-pass variable expansion

### Platform/UI integration

- synchronized Android system-bar icon mode with app dark mode
- retained responsive drawer/rail/sidebar and low-height behavior
- retained English/Persian, RTL, IME, edge-to-edge, and accessible status patterns

### Tests and quality gates

- expanded common tests for malformed/boundary cases
- added current-source core and protocol harnesses
- restored reproducible responsive harness
- added actual Desktop network runtime harness
- expanded static audit contracts for settings, CI, protocols, persistence, redaction, versioning, docs, and package cleanliness

## Compatibility and migration

- No Application ID, namespace, bundle ID, package prefix, backup schema, public navigation destination, or license change.
- Existing valid persisted data remains readable.
- Invalid/oversized persisted data is quarantined and replaced with safe bounded state rather than silently loaded.
- Backup import remains schema version 4; unsafe public-cleartext state is intentionally not imported.

## Verification status

Focused audits and current-source harnesses passed. Full Gradle compilation and platform runtime are blocked/not executed as documented in [VERIFICATION.md](VERIFICATION.md).
