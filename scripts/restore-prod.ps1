param(
  [Parameter(Mandatory=$true)][string]$BackupFile,
  [string]$EnvFile = '.env.production'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = if ([System.IO.Path]::IsPathRooted($BackupFile)) { $BackupFile } else { Join-Path $root $BackupFile }
if (-not (Test-Path $file)) { throw "备份文件不存在: $file" }
$compose = Join-Path $root 'docker-compose.prod.yml'
$envPath = Join-Path $root $EnvFile
$values = @{}
Get-Content $envPath | ForEach-Object { if ($_ -match '^([^#][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() } }
$db = $values['POSTGRES_DB']; $user = $values['POSTGRES_USER']
if (-not $db -or -not $user) { throw '环境文件缺少 POSTGRES_DB / POSTGRES_USER' }
Write-Host '警告：恢复会覆盖当前数据库。请确认已停止写入并已做最新备份。'
$confirm = Read-Host "输入 RESTORE 继续"
if ($confirm -ne 'RESTORE') { throw '已取消' }
Get-Content $file -Raw | docker compose --env-file $envPath -f $compose exec -T postgres psql -U $user -d $db
if ($LASTEXITCODE -ne 0) { throw 'psql 恢复失败' }
Write-Host '恢复完成，请重启 app 容器并执行冒烟测试'
