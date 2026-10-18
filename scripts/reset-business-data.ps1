param(
  [switch]$ConfirmReset,
  [switch]$KeepServiceStopped
)
$ErrorActionPreference = 'Stop'
if (-not $ConfirmReset) { throw '危险操作：请添加 -ConfirmReset 参数后再执行。' }
$root = Split-Path -Parent $PSScriptRoot
$db = Join-Path $root 'backend\data\ivs.mv.db'
$sql = Join-Path $root 'scripts\reset-business-data.sql'
$h2 = 'C:\Users\Administrator\.m2\repository\com\h2database\h2\2.2.224\h2-2.2.224.jar'
$taskName = 'IVS-Backend'
$backupDir = Join-Path $root 'backups'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $backupDir "ivs-h2-before-reset-$stamp.mv.db"

New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) {
  Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 5
$listener = Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
  $listener | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
  Start-Sleep -Seconds 3
}
if (Test-Path $db) {
  Copy-Item -LiteralPath $db -Destination $backup -Force
  Write-Host "已备份数据库：$backup"
}
$url = 'jdbc:h2:file:' + ((Join-Path $root 'backend\data\ivs') -replace '\\','/') + ';MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH'
& java -cp $h2 org.h2.tools.RunScript -url $url -user sa -password '' -script $sql -showResults
if ($LASTEXITCODE -ne 0) { throw "数据清空失败，退出码 $LASTEXITCODE" }
if (-not $KeepServiceStopped) {
  if (Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue) { Start-ScheduledTask -TaskName $taskName }
  Write-Host '后端任务已重新启动，等待健康检查...'
  for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    try {
      $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/actuator/health' -TimeoutSec 5 -NoProxy
      if ($health.status -eq 'UP') { Write-Host '健康检查 UP'; break }
    } catch { }
  }
}
Write-Host '业务数据已全部清空，admin、角色权限和全局参数保留。'
