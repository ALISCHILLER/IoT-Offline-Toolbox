# Security

## Intended use

Use this toolbox only on systems and networks you own or are explicitly authorized to test. Prefer HTTPS/WSS. Do not export diagnostic history until it has been reviewed for sensitive operational data.

## Enforced controls

- URL credentials are rejected.
- Authentication, cookies, API keys, password-like values, private-key blocks, and custom secret/token headers are redacted before cURL, logs, profiles, history, or durable diagnostic storage.
- Redaction is mandatory and cannot be disabled by settings or imported backup data.
- Cross-origin redirects carrying credentials or sensitive headers are rejected.
- Cross-origin redirects that preserve a request body are rejected unless the redirect semantics rewrite the request to a bodyless method.
- HTTP response bytes are bounded and binary payloads are shown as HEX/Base64 instead of being coerced into misleading replacement-character text.
- Backup-import rollback attempts each persisted snapshot independently so one rollback failure cannot suppress all later restoration attempts.
- Browser targets reject browser-managed/forbidden headers and do not claim support for manual `Cookie` headers or arbitrary WebSocket handshake headers.
- Request, response, field, collection, payload, and persisted-document limits are bounded.
- Imported backups cannot enable the public-cleartext override.
- Cancellation and socket cleanup are preserved in platform networking paths reviewed by the audit.
- Android/Desktop cancellation closes the active `Socket`/`DatagramSocket`; a successor waits for the cancelled operation to finish before it can publish state.
- TCP, UDP, MQTT, CoAP, and HTTP retain bounded binary evidence instead of forcing arbitrary bytes through lossy text decoding.

## Residual risks and required release gates

### Android cleartext capability

The Android manifest/network security configuration permits cleartext at the operating-system layer so the application can reach arbitrary local embedded devices. The shared `TransportSecurity` policy blocks public cleartext unless the operator explicitly enables the unsafe override. This is defense in depth, not an OS sandbox: any future network code that bypasses the shared policy could send cleartext traffic. Before release, add an instrumented boundary test that proves every HTTP/WebSocket entry point passes through the same policy.

### Browser platform

Browser CORS, cookie policy, forbidden request headers, redirects, and WebSocket APIs are enforced by the browser and server. The application cannot make browser targets equivalent to JVM/native raw networking.

### Preview build chain

The checkout now pins stable AGP `9.1.0` with Gradle `9.3.1`. This removes the preview-toolchain risk, but no security or release claim should be based on version selection alone until dependencies resolve, the complete Gradle matrix passes, and release artifacts are dependency-scanned.

### Not verified here

- Android/iOS physical-device networking and entitlements
- TalkBack, VoiceOver, keyboard-only, and browser accessibility runtime
- release signing, R8/minification, store artifacts, and store submission
- dependency-resolution vulnerability scan against the fully resolved graph
- full Gradle compile/test/lint/package matrix

## Reporting

Report security issues privately to `solimaniali90@gmail.com`. Do not include credentials, tokens, private customer endpoints, or personal data in a report.

## Author

Developed and maintained by  
[ALISCHILLER](https://github.com/ALISCHILLER) — **MSA**

Email: [solimaniali90@gmail.com](mailto:solimaniali90@gmail.com)
