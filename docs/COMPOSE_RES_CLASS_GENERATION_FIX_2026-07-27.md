# Compose `Res` class generation compile fix

Date: 2026-07-27

## Observed failure

The Desktop compilation reached Kotlin compilation but every source importing
`com.msa.iotofflinetoolbox.resources.*` failed with `Unresolved reference 'resources'`
and `Unresolved reference 'Res'`.

This is one root failure repeated across the UI. It does not mean hundreds of
resource keys are individually missing. The generated `Res` source set was absent.

## Root cause

The Compose resource extension defaults to `generateResClass = auto`. In Compose
Multiplatform 1.11.1, auto mode inspects the common source-set dependencies and
compares a `group:name:version` string with the expected resources artifact.
Dependencies supplied through a Gradle version catalog can expose their version
through a version constraint while `Dependency.version` is null. The auto check
then returns false and all `Res`/accessor generation tasks are skipped.

The module owns `src/commonMain/composeResources`, so resource generation should
not depend on dependency-shape inference.

## Applied fix

`composeApp/build.gradle.kts` now contains:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "com.msa.iotofflinetoolbox.resources"
    generateResClass = always
}
```

This forces `generateComposeResClass`, resource accessor generation, and resource
collector generation for the configured targets.

## Regression protection

- `tools/static_audit.py` now requires `generateResClass = always`.
- `tools/deep_quality_audit.py` verifies the full Compose resource-generation block.
- `tools/regenerate-compose-resources-windows.ps1` removes stale generated output,
  runs the two primary generation tasks, and optionally compiles Desktop.

## Windows verification

```powershell
.\tools\regenerate-compose-resources-windows.ps1 -CompileDesktop
```

Or run the full sequence manually:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean
.\gradlew.bat :composeApp:generateComposeResClass --rerun-tasks --warning-mode all
.\gradlew.bat :composeApp:generateResourceAccessorsForCommonMain --rerun-tasks --warning-mode all
.\gradlew.bat :composeApp:compileKotlinDesktop --warning-mode all --stacktrace
```

Expected generated source root:

```text
composeApp/build/generated/compose/resourceGenerator/kotlin/
```

Expected package:

```text
com.msa.iotofflinetoolbox.resources
```
