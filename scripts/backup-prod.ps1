param(
  [string]$OutputDir = 'backups',
  [string]$EnvFile = '.env.production'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root $OutputDir
New-Item -ItemType Directory -Force -Path $out | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$compose = Join-Path $root 'docker-compose.prod.yml'
$envPath = Join-Path $root $EnvFile
if (-not (Test-Path $envPath)) { throw "缺少 $envPath" }
$values = @{}
Get-Content $envPath | ForEach-Object { if ($_ -match '^([^#][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() } }
$db = $values['POSTGRES_DB']; $user = $values['POSTGRES_USER']
if (-not $db -or -not $user) { throw '环境文件缺少 POSTGRES_DB / POSTGRES_USER' }
$target = Join-Path $out "ivs-$stamp.sql"
Push-Location $root
try {
  docker compose --env-file $envPath -f $compose exec -T postgres pg_dump -U $user -d $db > $target
  if ($LASTEXITCODE -ne 0) { throw 'pg_dump 失败' }
  Write-Host "备份完成: $target"
} finally { Pop-Location }
