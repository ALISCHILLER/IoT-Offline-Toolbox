[CmdletBinding()]
param()

$gradleHome = Join-Path $env:USERPROFILE ".gradle"
$files = @(
    (Join-Path $gradleHome "init.gradle"),
    (Join-Path $gradleHome "init.gradle.kts")
)
$initDirectory = Join-Path $gradleHome "init.d"
if (Test-Path -LiteralPath $initDirectory) {
    $files += Get-ChildItem -LiteralPath $initDirectory -File -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
}

$existing = $files | Select-Object -Unique | Where-Object { Test-Path -LiteralPath $_ }
if (-not $existing) {
    Write-Host "No user-level Gradle init scripts were found."
    exit 0
}

foreach ($file in $existing) {
    Write-Host "`n--- $file ---" -ForegroundColor Cyan
    Select-String -LiteralPath $file -Pattern 'aliyun|maven|repository|repositories' -CaseSensitive:$false |
        ForEach-Object { $_.Line }
}
