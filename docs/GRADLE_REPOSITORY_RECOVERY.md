# Gradle repository recovery

## Symptom

Android Studio reports `Failed building KotlinMPPGradleModel`, and the first actionable request targets a third-party mirror such as:

```text
https://maven.aliyun.com/repository/google/...
502 Bad Gateway
```

Later Kotlin/Compose compiler failures are commonly secondary. Once a repository that supplied module metadata fails, the rest of that resolution attempt can no longer complete consistently.

## Project repository policy

`settings.gradle.kts` declares only the required official repositories and uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS` so individual modules cannot silently add mirrors.

A user- or machine-level Gradle init script can still alter repository behavior before project settings take effect. Recovery therefore inspects `%USERPROFILE%\.gradle` rather than pretending the project can fully control the machine.

## Review-first Windows recovery

Open PowerShell in the project root:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\tools\show-project-versions.ps1
.\tools\fix-gradle-sync-windows.ps1
```

The first repair run is **review-only**. When Aliyun injection is detected it lists the affected files, changes nothing, and exits with instructions.

After reviewing the complete init scripts, apply the repair:

```powershell
.\tools\fix-gradle-sync-windows.ps1 -Apply
```

The apply mode:

1. copies each affected init script to a timestamped backup;
2. moves the complete affected script to a timestamped disabled path;
3. stops Gradle daemons;
4. removes project-local `.gradle` and `.kotlin` model caches;
5. removes only the dependency cache entries implicated by the reported failure;
6. refreshes dependencies from the project repository policy.

To skip the dependency refresh while still applying reviewed file/cache changes:

```powershell
.\tools\fix-gradle-sync-windows.ps1 -Apply -SkipRefresh
```

## Important risk

An init script containing Aliyun rules may also contain required enterprise proxy, certificate, authentication, or plugin configuration. The tool intentionally does not edit individual Gradle statements. Review the script and restore required non-mirror configuration into a clean init script when necessary.

## Android Studio checks

- Open the repository root, not an individual module.
- Set Gradle JDK to JDK 17 or newer.
- Disable Gradle Offline mode for the first synchronization.
- Run **Sync Project with Gradle Files**.
- Confirm `show-project-versions.ps1` reports application version `3.6.2` and the expected version catalog.

## Manual locations

```text
%USERPROFILE%\.gradle\init.gradle
%USERPROFILE%\.gradle\init.gradle.kts
%USERPROFILE%\.gradle\init.d\
```

A managed corporate repository may be retained only when it reliably proxies every required upstream and is part of the organization's reviewed build policy. A public mirror must not be the sole source for a production build.
