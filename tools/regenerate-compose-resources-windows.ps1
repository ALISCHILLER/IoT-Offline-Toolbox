[CmdletBinding()]
param(
    [switch]$SkipClean,
    [switch]$CompileDesktop
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Invoke-GradleStep([string]$Title, [string[]]$Arguments) {
    Write-Host "`n==> $Title" -ForegroundColor Cyan
    & .\gradlew.bat @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Title failed with exit code $LASTEXITCODE."
    }
}

Invoke-GradleStep "Stopping Gradle daemons" @("--stop")

if (-not $SkipClean) {
    $generatedResources = Join-Path $projectRoot "composeApp\build\generated\compose\resourceGenerator"
    if (Test-Path -LiteralPath $generatedResources) {
        Remove-Item -LiteralPath $generatedResources -Recurse -Force
        Write-Host "Removed stale generated Compose resources: $generatedResources" -ForegroundColor Yellow
    }
}

Invoke-GradleStep "Generating the Compose Res class" @(
    ":composeApp:generateComposeResClass",
    "--rerun-tasks",
    "--warning-mode", "all"
)

Invoke-GradleStep "Generating common resource accessors" @(
    ":composeApp:generateResourceAccessorsForCommonMain",
    "--rerun-tasks",
    "--warning-mode", "all"
)

if ($CompileDesktop) {
    Invoke-GradleStep "Compiling the Desktop target" @(
        ":composeApp:compileKotlinDesktop",
        "--warning-mode", "all",
        "--stacktrace"
    )
}

Write-Host "`nCompose resource generation completed successfully." -ForegroundColor Green
Write-Host "Generated package: com.msa.iotofflinetoolbox.resources"
Write-Host "Generated class:   Res"
