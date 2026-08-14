# Dependency baseline 3.6.2 - 2026-08-14


Online freshness checked: 2026-08-14 against official Android, Kotlin, Gradle, and Compose plugin sources; selected versions were retained because they remain inside the documented compatibility set.

This baseline selects stable versions as a compatibility set rather than upgrading every coordinate independently. A version being listed here means it is selected and source-reviewed; it does not mean the complete target matrix has passed.

| Component | Pinned version | Compatibility and delivery note |
|---|---:|---|
| Kotlin / KGP | 2.4.10 | Current selected stable compiler line; official Kotlin documentation lists Gradle 7.6.3-9.5.0 and AGP 8.5.2-9.1.0 as the fully supported range |
| Android Gradle Plugin | 9.1.0 | Stable version at Kotlin 2.4.10's documented fully supported AGP ceiling; replaces preview 9.0.0-alpha06 |
| Gradle distribution | 9.3.1 | Required/default Gradle for AGP 9.1; binary SHA-256 pinned in wrapper properties |
| Gradle wrapper JAR | verified official 8.14.4 JAR | Existing wrapper JAR checksum is official and may launch a newer distribution; regenerate twice with Gradle 9.3.1 on a networked host to refresh every wrapper file |
| JDK | 17 | AGP 9.1 required/default JDK; CI uses Temurin 17 |
| Compose Multiplatform Gradle plugin | 1.11.1 | Stable plugin baseline |
| Compose runtime/foundation/UI/resources | 1.11.1 | Aligned with the Compose Multiplatform 1.11.1 component set to avoid generated-resource and runtime/UI version skew |
| Compose Material 3 | 1.9.0 | Retained pending full target build and visual regression verification |
| Material Icons Extended | 1.7.3 | Upstream-frozen compatibility pin; migrate existing Icons usage to Material Symbols resources before removal |
| Ktor | 3.5.1 | Retained current networking line; HTTP, WebSocket, Darwin/CIO/OkHttp/JS engines and Ktor Network |
| kotlinx.coroutines | 1.11.0 | Shared concurrency and test support |
| kotlinx.serialization | 1.11.0 | JSON state and backup serialization |
| Multiplatform Settings | 1.3.0 | Local key/value persistence |
| AndroidX Activity Compose | 1.13.0 | Android Compose entry point |
| Android compile / target SDK | 36 | CI installs platform 36 and Build Tools 36.0.0 |
| Android minimum SDK | 24 | Preserved application compatibility boundary |

## Official compatibility evidence

- Android Gradle Plugin 9.1 release notes: https://developer.android.com/build/releases/agp-9-1-0-release-notes
- Kotlin Gradle plugin compatibility table: https://kotlinlang.org/docs/gradle-configure-project.html
- Gradle 9.3.1 release notes: https://docs.gradle.org/9.3.1/release-notes.html
- Gradle distribution and wrapper checksums: https://gradle.org/release-checksums/

## Upgrade policy

1. Keep AGP, Gradle, Kotlin, Compose plugin, and JDK changes in explicit compatibility batches.
2. Keep the Compose Gradle plugin and core Compose runtime/foundation/UI/resources artifacts on the same released component version.
3. After dependency resolution, execute Android debug/release/lint, common/Desktop tests, JS/Wasm tests and bundles, and iOS simulator/framework tasks.
4. Run representative HTTP, WebSocket/MQTT, TCP/UDP, CoAP, localization, RTL/LTR, accessibility, persistence, and release/R8 smoke tests.
5. Roll back the complete batch if a target cannot be made green without unrelated suppression or behavior changes.
