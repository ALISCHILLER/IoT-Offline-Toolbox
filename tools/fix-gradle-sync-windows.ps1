[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$SkipRefresh
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Get-MirrorInitFiles {
    $gradleHome = Join-Path $env:USERPROFILE ".gradle"
    $candidates = @(
        (Join-Path $gradleHome "init.gradle"),
        (Join-Path $gradleHome "init.gradle.kts")
    )
    $initDirectory = Join-Path $gradleHome "init.d"
    if (Test-Path -LiteralPath $initDirectory) {
        $candidates += Get-ChildItem -LiteralPath $initDirectory -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '\.(gradle|gradle\.kts)$' } |
            Select-Object -ExpandProperty FullName
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        $file = Get-Item -LiteralPath $candidate
        $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop
        if ($content -match '(?i)aliyun|maven\.aliyun\.com') { $file }
    }
}

function Disable-MirrorInitFile([System.IO.FileInfo]$File) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $backupPath = "$($File.FullName).aliyun-disabled-$timestamp.bak"
    $disabledPath = "$($File.FullName).disabled-$timestamp"
    Copy-Item -LiteralPath $File.FullName -Destination $backupPath -Force
    Move-Item -LiteralPath $File.FullName -Destination $disabledPath -Force
    Write-Host "Disabled: $($File.FullName)" -ForegroundColor Yellow
    Write-Host "Backup:   $backupPath"
    Write-Host "Moved to: $disabledPath"
}

Write-Step "Checking Java"
& java -version
if ($LASTEXITCODE -ne 0) {
    throw "Java was not found. Configure Android Studio Gradle JDK to JDK 17 or newer."
}

$gradleHome = Join-Path $env:USERPROFILE ".gradle"
Write-Step "Scanning user-level Gradle init scripts for Aliyun repository injection"
$mirrorFiles = @(Get-MirrorInitFiles)
if ($mirrorFiles.Count -eq 0) {
    Write-Host "No Aliyun repository injection was detected." -ForegroundColor Green
} else {
    Write-Host "Detected mirror injection in:" -ForegroundColor Yellow
    $mirrorFiles | ForEach-Object { Write-Host "  $($_.FullName)" }
}

if (-not $Apply) {
    Write-Host "`nReview-only mode: no files, caches, or Gradle daemons were changed." -ForegroundColor Green
    if ($mirrorFiles.Count -gt 0) {
        Write-Host "Re-run with -Apply after reviewing the listed scripts:"
        Write-Host "  .\tools\fix-gradle-sync-windows.ps1 -Apply"
        exit 2
    }
    Write-Host "Use -Apply only when you intentionally want cache cleanup and dependency refresh."
    exit 0
}

$mirrorFiles | ForEach-Object { Disable-MirrorInitFile $_ }

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Step "Stopping Gradle daemons"
& .\gradlew.bat --stop

Write-Step "Removing project-local Gradle model caches"
foreach ($cache in @((Join-Path $projectRoot ".gradle"), (Join-Path $projectRoot ".kotlin"))) {
    if (Test-Path -LiteralPath $cache) {
        Remove-Item -LiteralPath $cache -Recurse -Force
        Write-Host "Removed $cache"
    }
}

Write-Step "Removing only failed dependency cache entries"
$failedCacheEntries = @(
    (Join-Path $gradleHome "caches\modules-2\files-2.1\androidx.compose.ui\ui-tooling-preview"),
    (Join-Path $gradleHome "caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-scripting-compiler-embeddable"),
    (Join-Path $gradleHome "caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-compose-compiler-plugin-embeddable")
)
foreach ($cacheEntry in $failedCacheEntries) {
    if (Test-Path -LiteralPath $cacheEntry) {
        Remove-Item -LiteralPath $cacheEntry -Recurse -Force
        Write-Host "Removed $cacheEntry"
    }
}

if (-not $SkipRefresh) {
    Write-Step "Refreshing dependencies from official repositories"
    & .\gradlew.bat help --refresh-dependencies --stacktrace --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle dependency refresh failed. Inspect the first 'Could not GET' URL in the output."
    }
}

Write-Host "`nGradle repository repair completed." -ForegroundColor Green
Write-Host "Disable Gradle Offline mode in Android Studio, then run Sync Project with Gradle Files."
