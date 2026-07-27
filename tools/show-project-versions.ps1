[CmdletBinding()]
param()

$projectRoot = Split-Path -Parent $PSScriptRoot
$catalog = Join-Path $projectRoot "gradle\libs.versions.toml"
if (-not (Test-Path -LiteralPath $catalog)) {
    throw "Version catalog was not found: $catalog"
}

$properties = Join-Path $projectRoot "gradle.properties"
Write-Host "Application release identity:" -ForegroundColor Cyan
Get-Content -LiteralPath $properties |
    Where-Object { $_ -match '^(appVersion|appVersionCode)=' }

Write-Host "`nProject version catalog:" -ForegroundColor Cyan
Get-Content -LiteralPath $catalog |
    Where-Object { $_ -match '^(agp|kotlin|composeMultiplatform|ktor|kotlinx-coroutines|kotlinx-serialization)\s*=' }

Write-Host "`nIf Android Studio reports different versions, close the project, remove .gradle/.kotlin, and reopen this directory:" -ForegroundColor Yellow
Write-Host $projectRoot
