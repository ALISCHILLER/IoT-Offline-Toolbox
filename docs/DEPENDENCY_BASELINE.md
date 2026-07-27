# Dependency baseline — 2026-07-15

| Component | Pinned version | Delivery note |
|---|---:|---|
| Kotlin | 2.4.10 | Current selected stable compiler line |
| Compose Multiplatform Gradle plugin | 1.11.1 | Plugin line used by the project |
| Compose runtime/foundation/UI/resources | 1.10.1 | Explicit direct coordinates matching the libraries selected by plugin 1.11.1 |
| Compose Material 3 | 1.9.0 | Explicit plugin-compatible Material 3 coordinate |
| Material Icons Extended | 1.7.3 | Upstream-frozen compatibility pin; migrate existing `Icons.*` usage to Material Symbols resources before removal |
| Android Gradle Plugin | 9.0.0-alpha06 | Compatibility pin for the reported Android Studio; AGP 9.0 requires JDK 17 and Gradle 9.1.0 or newer |
| Gradle | 9.1.0 | Compatible with AGP 9.0 and Kotlin Multiplatform 2.4.10; distribution SHA-256 pinned in wrapper properties |
| Ktor | 3.5.1 | HTTP, WebSocket, Darwin/CIO/OkHttp/JS engines and Ktor Network |
| kotlinx.coroutines | 1.11.0 | Shared concurrency and test support |
| kotlinx.serialization | 1.11.0 | JSON state and backup serialization |
| Multiplatform Settings | 1.3.0 | Local key/value persistence |
| AndroidX Activity Compose | 1.13.0 | Android Compose entry point |

Dependency updates must pass the full CI matrix and representative protocol runtime tests. Do not accept an automated major-version update solely because resolution succeeds.
