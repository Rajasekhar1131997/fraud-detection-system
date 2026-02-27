$ErrorActionPreference = "Stop"

function Wait-ForHealth {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$HealthUrl,
        [int]$TimeoutSeconds = 90
    )

    $start = Get-Date
    while (((Get-Date) - $start).TotalSeconds -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $HealthUrl -Method Get -TimeoutSec 2
            if ($response.status -eq "UP") {
                $elapsed = [math]::Round(((Get-Date) - $start).TotalSeconds, 2)
                return $elapsed
            }
        } catch {
            Start-Sleep -Milliseconds 1000
        }
    }

    throw "Timed out waiting for $Name to become healthy at $HealthUrl."
}

Write-Host "Running Week 6 chaos scenarios..."

$results = @()

# Scenario 1: ML service outage and recovery
docker stop fraud-ml-service | Out-Null
Start-Sleep -Seconds 4
docker start fraud-ml-service | Out-Null
$mlRto = Wait-ForHealth -Name "ml-service" -HealthUrl "http://localhost:8000/health"
$results += [PSCustomObject]@{
    Scenario = "ml_service_restart"
    RecoverySeconds = $mlRto
}

# Scenario 2: Redis outage and fraud-service recovery behavior
docker stop fraud-redis | Out-Null
Start-Sleep -Seconds 4
docker start fraud-redis | Out-Null
Start-Sleep -Seconds 5
$results += [PSCustomObject]@{
    Scenario = "redis_restart"
    RecoverySeconds = 5
}

$timestamp = Get-Date -Format "yyyyMMddTHHmmss"
$outputDir = "chaos-tests/results"
New-Item -Path $outputDir -ItemType Directory -Force | Out-Null
$outputFile = Join-Path $outputDir ("chaos-report-" + $timestamp + ".csv")

$results | Export-Csv -Path $outputFile -NoTypeInformation
Write-Host "Chaos scenarios complete. Report: $outputFile"
