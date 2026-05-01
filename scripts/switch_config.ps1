param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("staging", "production")]
    [string]$Environment
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repoRoot "configs/config.$Environment.json"
$target = Join-Path $repoRoot "config.json"

if (-not (Test-Path $source)) {
    Write-Error "Source config not found: $source"
}

Copy-Item -Path $source -Destination $target -Force
Write-Host "Switched active config to: $Environment"
Write-Host "Copied: $source -> $target"
