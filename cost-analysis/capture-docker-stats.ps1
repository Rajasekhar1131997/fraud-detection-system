$ErrorActionPreference = "Stop"

$timestamp = Get-Date -Format "yyyyMMddTHHmmss"
$outputDir = "cost-analysis/reports"
New-Item -Path $outputDir -ItemType Directory -Force | Out-Null
$outputFile = Join-Path $outputDir ("docker-stats-" + $timestamp + ".csv")

docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}}" |
    ForEach-Object {
        $parts = $_.Split(",")
        [PSCustomObject]@{
            container = $parts[0]
            cpu_percent = $parts[1]
            memory_usage = $parts[2]
            memory_percent = $parts[3]
            net_io = $parts[4]
            block_io = $parts[5]
        }
    } | Export-Csv -Path $outputFile -NoTypeInformation

Write-Host "Saved usage snapshot to $outputFile"
