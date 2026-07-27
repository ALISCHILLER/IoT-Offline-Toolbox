# Release checklist — 3.6.2 ultimate professional revision

Date of this checklist: 2026-07-26

Version `3.6.2` and build `38`

## Already executed in the delivery environment

- [x] Deep source/resource/capability audit
- [x] UI contrast/source-contract audit
- [x] Core current-source runtime harness
- [x] Protocol current-source runtime harness
- [x] Responsive-policy runtime harness
- [x] Desktop TCP/UDP loopback and prompt socket-cancellation harness
- [x] Operation coordinator ordering/cancellation harness
- [x] Dedicated custom-secret redaction harness
- [x] HTTP redirected-error final target regression contract
- [x] Cross-origin forwarded-body redirect blocking contract
- [x] Payload-template persistence redaction contract
- [x] Shared HTTP validation boundary across UI, Store, and transport
- [x] Strict UTF-8 versus binary response classification with exact bounded bytes
- [x] TCP/UDP/MQTT/CoAP binary-evidence contracts
- [x] Stale-operation state suppression and cancelled-job join ordering
- [x] HEX/Base64 binary evidence views and Bash/PowerShell cURL previews
- [x] Independent per-snapshot rollback attempts after backup-import write failure
- [x] Global SSDP/mDNS operation-deadline contract
- [x] English/Persian resource parity: 704 / 704
- [x] Source checksum manifest regenerated and verified
- [x] Final archive integrity test after packaging
- [x] Secret-file and package-prefix scans

## Required before any production claim

- [ ] Fresh-clone Gradle wrapper download and checksum verification
- [ ] `:composeApp:compileKotlinMetadata`
- [ ] `:composeApp:allTests`
- [ ] Desktop compile and application smoke test
- [ ] Android debug/release build, lint, R8, emulator and physical-device smoke tests
- [ ] JS and Wasm tests, production bundles, CORS/cookie/redirect/browser-header runtime matrix
- [ ] iOS simulator compile/test and signed-device network/entitlement verification
- [ ] HTTP timeout behavior per active Ktor engine
- [ ] Android test proving all traffic goes through `TransportSecurity`
- [ ] malformed/import/upgrade persistence tests through Gradle
- [ ] TalkBack, VoiceOver, keyboard-only, focus order, scaling, and reduced-motion checks
- [ ] dependency and secret scans on the resolved release graph
- [ ] release signing, artifact verification, privacy review, and store submission checks
- [ ] rollback rehearsal using a previous signed release and persisted data backup

## Release blocker recorded here

The command below was attempted and failed before project configuration because the Gradle distribution host could not be resolved:

```bash
./gradlew --offline :composeApp:compileKotlinMetadata --stacktrace
```

See `docs/BUILD_ATTEMPT.txt` and `docs/DEEP_AUDIT_REVISION_2026-07-20.md`.

## Honest status

```text
Not production-ready.
Statically hardened with focused executable harnesses passed.
Full Gradle and platform-runtime verification required.
```
