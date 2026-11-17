param(
  [switch]$ConfirmReset,
  [string]$DatabasePath = "$env:LOCALAPPDATA\Programs\IVS\data\ivs.mv.db"
)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmReset) { throw '危险操作：请添加 -ConfirmReset 参数后再执行。' }
$root = Split-Path -Parent $PSScriptRoot
$sql = Join-Path $PSScriptRoot 'reset-sales-rebate-test-data.sql'
$h2 = 'C:\Users\Administrator\.m2\repository\com\h2database\h2\2.2.224\h2-2.2.224.jar'
$backupDir = Join-Path $root 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $backupDir "ivs-before-sales-rebate-reset-$stamp.mv.db"

Get-Process -Name IVS -ErrorAction SilentlyContinue | Stop-Process -Force
$listener = Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
  $listener | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
}
Start-Sleep -Seconds 3

if (-not (Test-Path -LiteralPath $DatabasePath)) { throw "数据库不存在：$DatabasePath" }
Copy-Item -LiteralPath $DatabasePath -Destination $backup -Force
Write-Host "已备份数据库：$backup"

$dbBase = $DatabasePath.Substring(0, $DatabasePath.Length - 6)
$url = 'jdbc:h2:file:' + ($dbBase -replace '\\','/') + ';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH'
& java -cp $h2 org.h2.tools.RunScript -url $url -user sa -password '' -script $sql -showResults
if ($LASTEXITCODE -ne 0) { throw "数据清空失败，退出码 $LASTEXITCODE" }

$launcher = Join-Path $env:LOCALAPPDATA 'Programs\IVS\IVSLauncher.exe'
if (Test-Path -LiteralPath $launcher) {
  Start-Process -FilePath $launcher -WindowStyle Hidden
  for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep -Seconds 2
    try {
      $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/actuator/health' -TimeoutSec 3
      if ($health.status -eq 'UP') { Write-Host '电脑版已重新启动，健康检查 UP'; break }
    } catch { }
  }
}
Write-Host '销售、实销、兑现申请、申报及返利流水已清空；主数据、政策、采购、库存和应付已保留。'
