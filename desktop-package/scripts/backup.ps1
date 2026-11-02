$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$db = Join-Path $root 'data\ivs.mv.db'
$backupDir = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
if (-not (Test-Path $db)) { throw '数据库文件不存在，请先启动系统并登录一次。' }
$target = Join-Path $backupDir ('ivs-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.mv.db')
Copy-Item -LiteralPath $db -Destination $target -Force
Write-Host "备份完成：$target"
