# Android Studio and AGP compatibility hotfix — 3.6.2


> **Re-audit note (2026-07-20):** Version recommendations in this document are historical and must not be treated as a current compatibility guarantee. Confirm the official current Android Studio/AGP/Gradle requirements and the Kotlin Multiplatform/Compose compatibility matrix before changing the pinned toolchain.


## Reported failure

Android Studio rejected Android Gradle Plugin `9.2.1` and reported `9.0.0-alpha06` as its newest supported version. This is an IDE/toolchain compatibility failure; it is not caused by application Kotlin code.

## Applied compatibility profile

| Component | Version | Reason |
|---|---:|---|
| Android Gradle Plugin | `9.0.0-alpha06` | Exact maximum reported by the installed Android Studio |
| Gradle wrapper | `9.1.0` | AGP 9.0 baseline and within the Kotlin 2.4.10 supported Gradle range |
| Kotlin | `2.4.10` | Preserved |
| Compose Multiplatform | `1.11.1` | Preserved |
| compileSdk / targetSdk | `36` | Preserved |
| JDK | `17` | Required |

The Gradle distribution checksum is pinned to the official Gradle 9.1.0 binary checksum.

## Important production note

`9.0.0-alpha06` is a preview AGP. It removes the immediate IDE compatibility block but is not the preferred long-term production baseline. The recommended stable route is:

1. Install Android Studio Panda 3 or newer.
2. Use stable AGP `9.1.0`.
3. Use Gradle `9.3.1`.
4. Run the full Android/KMP build, lint, release/R8, UI, and device test matrix.

AGP `9.2.1` was not retained because the official Kotlin Multiplatform compatibility table for Kotlin `2.4.10` currently lists AGP support through `9.1.0`, and Gradle support through `9.5.0`.

## Local recovery steps

From a newly extracted directory:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\tools\show-project-versions.ps1
.\tools\fix-gradle-sync-windows.ps1
```

Then in Android Studio:

1. Open the exact `IoT-Offline-Toolbox-3.6.2` directory.
2. Set Gradle JDK to 17.
3. Disable Gradle Offline mode for the first sync.
4. Use **File > Sync Project with Gradle Files**.
5. If the old AGP version is still shown, close Android Studio and delete only the project-local `.gradle` and `.kotlin` directories before reopening.

## Verification status

The version/catalog/wrapper changes are statically verified. A complete Gradle sync/build still requires successful access to Google Maven, Maven Central, the Gradle distribution service, and the platform toolchains.
