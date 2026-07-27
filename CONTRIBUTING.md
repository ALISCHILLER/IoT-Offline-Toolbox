# Contributing

Contributions must preserve the local-first, bounded, capability-aware, and explicitly authorized design of IoT Offline Toolbox.

## Pull request requirements

1. Keep business/protocol logic out of Composable functions.
2. Add or update tests for parsers, validation, state reducers, persistence recovery, codecs, and backup compatibility.
3. Do not add analytics, advertising SDKs, hidden outbound requests, or bundled credentials.
4. Do not persist passwords, tokens, private keys, or unfiltered authorization headers.
5. Keep scans, payloads, responses, concurrency, and timeouts bounded.
6. Represent unavailable platform functionality through `PlatformCapabilities`; do not throw placeholder exceptions from user flows.
7. Preserve `com.msa` for every new package and namespace.
8. Preserve the public protocol behavior unless the change includes migration/compatibility notes.
9. Run the appropriate gates:

```bash
bash tools/run_quality_checks.sh --static
bash tools/run_quality_checks.sh --jvm-android
bash tools/run_quality_checks.sh --web
# macOS only
bash tools/run_quality_checks.sh --ios
```

## Code style

- Kotlin official style
- immutable state and unidirectional event flow
- complete implementations; no omitted-code markers or executable TODO placeholders
- explicit operation limits and cancellation ownership
- English/Persian UI strings supplied through the shared localization helper
- accessibility labels for non-text controls and adaptive layout for narrow/wide windows

## Security reports

Do not open a public issue for a vulnerability that exposes credentials, private-network data, signing material, or remote code execution. Follow [SECURITY.md](SECURITY.md).

## Author

Developed and maintained by  
[ALISCHILLER](https://github.com/ALISCHILLER) — **MSA**

Email: [solimaniali90@gmail.com](mailto:solimaniali90@gmail.com)
