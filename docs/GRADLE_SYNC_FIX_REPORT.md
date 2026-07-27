# Gradle sync fix report

## Reported failure

The supplied Android Studio trace failed while building `KotlinMPPGradleModel`. Its first actionable network failure was an HTTP 502 from an Aliyun Google-Maven mirror. The later Kotlin scripting/compiler-plugin errors were downstream resolution failures.

The trace also requested Kotlin `2.3.21` and Jetpack Compose UI `1.10.5`, which do not match this checkout. Version 3.6.2 centrally declares Kotlin `2.4.10` and Compose Multiplatform `1.11.1`. The reported trace therefore came from stale model data, a different/older checkout, or machine-level dependency substitution.

## Project-side hardening

- centralized plugin and dependency repositories;
- official repositories only;
- project repositories rejected;
- pinned Gradle distribution SHA-256;
- central application/version properties;
- deterministic script that prints the opened checkout versions;
- review-first machine recovery tool.

## Required local action

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\tools\show-project-versions.ps1
.\tools\fix-gradle-sync-windows.ps1
```

Review the detected files. Only then run:

```powershell
.\tools\fix-gradle-sync-windows.ps1 -Apply
```

Open exactly the `IoT-Offline-Toolbox-3.6.2` directory and synchronize with Offline mode disabled.

## Verification status

The repository configuration and recovery tool were statically reviewed. PowerShell execution was not available in the delivery environment. Full dependency resolution was separately blocked because the delivery environment could not resolve `services.gradle.org`.
