# Release checklist - 3.6.2 full transformation

Date: 2026-08-02

Version `3.6.2` and build `38`

## Executed in this transformation

- [x] Deep source/resource/capability audit
- [x] UI contrast/source-contract audit
- [x] Core current-source runtime harness
- [x] Protocol current-source runtime harness
- [x] Deterministic protocol fuzz harness: 110,000 malformed-input cases
- [x] Responsive-policy runtime harness
- [x] Desktop TCP/UDP loopback and prompt socket-cancellation harness
- [x] Operation coordinator ordering/cancellation harness
- [x] Dedicated custom-secret redaction harness
- [x] MQTT connection-only and QoS 0 publish completion regression coverage
- [x] Android/Desktop cancellable port-probe correction
- [x] English/Persian key and placeholder parity audit
- [x] Untrusted dependency mirrors removed
- [x] Gradle distribution checksum pinned
- [x] Gradle wrapper JAR checksum constrained to official known values
- [x] GitHub Actions pinned to immutable 40-character commit SHAs
- [x] Checkout credentials disabled in every CI checkout step
- [x] Linux Gradle wrapper executable bit restored
- [x] Source checksum manifest regenerated and verified
- [x] Secret-file and package-prefix scans

## Required before any production claim

- [ ] Fresh-clone Gradle 9.3.1 download and checksum verification on a connected machine
- [ ] Regenerate the complete Gradle 9.3.1 wrapper twice and review the wrapper JAR diff
- [ ] `:composeApp:compileKotlinMetadata`
- [ ] `:composeApp:allTests`
- [ ] Desktop compile and application smoke test
- [ ] Android debug/release build, lint, R8, emulator and physical-device smoke tests
- [ ] JS and Wasm tests, production bundles, CORS/cookie/redirect/browser-header runtime matrix
- [ ] iOS simulator compile/test and signed-device network/entitlement verification
- [ ] Android instrumented test proving all app traffic goes through the shared `TransportSecurity` policy
- [ ] HTTP timeout behavior per active Ktor engine
- [ ] malformed/import/upgrade persistence tests through Gradle
- [ ] TalkBack, VoiceOver, keyboard-only, focus order, scaling, RTL/LTR, and reduced-motion checks
- [ ] dependency vulnerability, license, and secret scans on the resolved release graph
- [ ] signed immutable release artifacts, checksums, SBOM/provenance as required, and privacy review
- [ ] rollback rehearsal using a previous signed release and persisted-data backup
- [ ] successful CI run tied to the exact release commit

## Recorded blocker

`bash ./gradlew --version --stacktrace` launched successfully after the executable-bit fix, but Gradle 9.3.1 retrieval stopped with `UnknownHostException: services.gradle.org` before project configuration.

## Honest status

```text
Not production-ready.
Statically hardened with focused executable harnesses passed.
Full Gradle, platform-runtime, accessibility, signing, and release verification required.
```
