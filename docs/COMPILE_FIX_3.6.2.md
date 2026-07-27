# Kotlin/Compose compile hotfix — 3.6.2

## Scope

This release addresses the concrete `:composeApp:compileKotlinDesktop` errors reported from Android Studio on Windows.

## Corrected compiler failures

1. Removed explicit `androidx.compose.foundation.layout.weight` imports. The modifier is resolved through `RowScope`/`ColumnScope` at its valid call sites.
2. Precomputed the localized close-menu description before entering the non-composable `semantics` lambda.
3. Opted the mobile shell into `ExperimentalMaterial3Api` for `TopAppBar`.
4. Changed Ktor channel buffering from a `Long` argument to the supported `Int` argument.
5. Added a `List<@Composable (Modifier) -> Unit>` grid API and typed generated device/template cards explicitly.
6. Replaced invalid `mqttWebSocket` capability references with `mqttWebSocketClient`.
7. Added `kotlin.native.ignoreDisabledTargets=true` so Windows builds do not emit the expected iOS simulator target warning. macOS CI remains responsible for iOS execution.

## Verification status

- Source-level regression audit: **Verified**
- Structural/static project audit: **Verified after checksum regeneration**
- Full Gradle compile in this environment: **Blocked** because the Gradle distribution cannot be downloaded from `services.gradle.org`.
- User-side Gradle compile: **Required** to confirm all platform compiler tasks with the local Android Studio toolchain.
