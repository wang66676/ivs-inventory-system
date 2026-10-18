param([string]$OutputDir = 'backups')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$db = Join-Path $root 'backend\data\ivs.mv.db'
if (-not (Test-Path $db)) { throw "未找到 H2 数据文件: $db" }
$out = Join-Path $root $OutputDir
New-Item -ItemType Directory -Force -Path $out | Out-Null
$target = Join-Path $out ("ivs-h2-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.mv.db')
& (Join-Path $PSScriptRoot 'stop-backend.ps1') | Out-Null
Start-Sleep -Seconds 2
Copy-Item -LiteralPath $db -Destination $target -Force
& (Join-Path $PSScriptRoot 'start-backend.ps1')
if ($LASTEXITCODE -ne 0) { throw '备份后重启失败' }
Write-Host "H2 备份完成: $target"
