param([Parameter(Mandatory=$true)][string]$BackupFile)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src = if ([System.IO.Path]::IsPathRooted($BackupFile)) { $BackupFile } else { Join-Path $root $BackupFile }
if (-not (Test-Path $src)) { throw "备份文件不存在: $src" }
$dbDir = Join-Path $root 'backend\data'
$db = Join-Path $dbDir 'ivs.mv.db'
New-Item -ItemType Directory -Force -Path $dbDir | Out-Null
& (Join-Path $PSScriptRoot 'stop-backend.ps1') | Out-Null
Start-Sleep -Seconds 2
if (Test-Path $db) { Copy-Item -LiteralPath $db -Destination ($db + '.before-restore-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) -Force }
Copy-Item -LiteralPath $src -Destination $db -Force
& (Join-Path $PSScriptRoot 'start-backend.ps1')
if ($LASTEXITCODE -ne 0) { throw '恢复后启动失败' }
Write-Host "H2 恢复完成: $src"
