$ErrorActionPreference = "Stop"

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AuthUsername = "analyst",
    [string]$AuthPassword = "analyst-change-me",
    [string[]]$Profiles = @("capacity_1m_day", "capacity_5m_day", "capacity_10m_day", "spike_10m_day_peak"),
    [string]$OutputDir = "load-tests/reports"
)

if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    throw "k6 is not installed or not available in PATH."
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$timestamp = Get-Date -Format "yyyyMMddTHHmmss"
$index = [System.Collections.Generic.List[object]]::new()

foreach ($profile in $Profiles) {
    Write-Host "Running profile: $profile"
    $summaryPath = Join-Path $OutputDir ("k6-summary-" + $profile + "-" + $timestamp + ".json")

    $env:BASE_URL = $BaseUrl
    $env:AUTH_USERNAME = $AuthUsername
    $env:AUTH_PASSWORD = $AuthPassword
    $env:PROFILE = $profile

    k6 run load-tests/transactions-capacity-test.js --summary-export $summaryPath

    $index.Add([PSCustomObject]@{
        profile = $profile
        summary = $summaryPath
    })
}

$indexPath = Join-Path $OutputDir ("k6-run-index-" + $timestamp + ".json")
$index | ConvertTo-Json | Set-Content -Path $indexPath -Encoding UTF8

Write-Host "Capacity test runs complete. Index: $indexPath"
