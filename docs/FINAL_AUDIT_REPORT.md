# Final audit report — 3.6.2 deep re-audit

> **Final-audit note:** This report is retained as historical evidence and is superseded by [`FINAL_CODE_AUDIT_2026-07-20.md`](FINAL_CODE_AUDIT_2026-07-20.md).

Date: 2026-07-20

## Executive result

The previous completion package contained material defects that were not represented accurately by the earlier report. This re-audit found and corrected source corruption, incomplete localization, ineffective/contradictory settings, incomplete secret detection, browser capability mismatches, duplicated cURL timeout output, and inaccurate port-state classification.

The corrected project passes all dependency-free audits and five focused executable harnesses available in this environment. It is still **not production-ready** because the Gradle distribution could not be obtained, so no full source-set compilation, Gradle tests, platform packaging, visual runtime, accessibility, signing, or store verification is claimed.

## Fixed findings

| Severity | Finding | Resolution | Evidence |
|---|---|---|---|
| Critical | Kotlin syntax corruption in `ToolboxStore.kt` | repaired strings/apostrophe and added corruption scanner | deep audit + focused harness compilation |
| High | custom secret/token headers could reach generic history text | expanded mandatory redaction across raw text, profile, cURL, redirect and history paths | dedicated secret harness + common tests present |
| High | browser UI/store allowed controls the platform cannot honor | explicit platform capabilities and store-level rejection for forbidden headers, cookies, redirects and WebSocket headers | deep audit/source review |
| High | HTTP timeout model did not represent request/connect/socket independently | separate model, defaults, request wiring and capability-aware UI | deep audit/source review; full Ktor runtime still required |
| Medium | port limit accepted 65535 but persisted as 4096 | one `1..4096` boundary across settings/validation/persistence | deep audit |
| Medium | redaction setting was user-visible but forced true | removed ineffective setting; redaction is mandatory | deep audit |
| Medium | JVM/iOS port failures were over-simplified | timeout/refusal/unknown classification corrected within platform limits | source review; Desktop loopback harness |
| Medium | user-facing Store results were partially English | localized operational messages; resources now 687 / 687 | resource audit |
| Low | cURL emitted duplicate timeout arguments | one connect timeout and one max-time argument | common regression test + source audit |

## Residual findings and release risks

| Severity | Finding | Required action |
|---|---|---|
| High release risk | full Gradle configuration/compile/test matrix was not executed | run the required matrix on a networked supported workstation and fix any compiler/dependency errors |
| High release risk | preview AGP `9.0.0-alpha06` remains pinned | validate or migrate the complete Kotlin/Compose/AGP/Gradle/IDE matrix; do not upgrade piecemeal |
| Medium security risk | Android OS-level cleartext is globally permitted for local-device compatibility | prove every network entry point passes through shared transport policy; consider a narrower architecture if product constraints permit |
| Medium platform risk | browser networking is constrained by CORS, browser-managed headers/cookies, redirects and WebSocket APIs | execute a browser/server interoperability matrix; keep unsupported controls disabled |
| Medium platform risk | iOS SSDP/mDNS and network behavior lack signed-device entitlement verification | test on signed physical devices with required local-network/multicast configuration |
| Medium product gap | HTTP tool is not a complete Postman replacement | multipart/file upload, binary export/viewer, proxy, client certificates/mTLS, environments/collections remain outside this delivery |
| Low maintainability | several UI/store files remain very large | split only after the full build is available, preserving contracts and with regression tests |

## Verification summary

See `VERIFICATION.md`. Current status:

```text
Static/dependency-free audits: Passed
Focused runtime harnesses: Passed (5)
Full Gradle build: Blocked before configuration
Platform runtime and accessibility: Not executed
Production readiness: Not production-ready
```

## Rollback

The prior delivered archive remains the baseline. Data compatibility is preserved: no persistence schema version or package/application identifier was changed; the removed redaction setting is tolerated because JSON decoding ignores unknown keys. Restore the prior archive only as a code rollback—do not treat it as safer than this revision, because this audit documents defects present in that baseline.
