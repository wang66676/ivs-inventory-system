$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root '.run\backend.pid'
if (-not (Test-Path $pidFile)) { Write-Host '系统未运行'; exit 0 }
$targetPid = [int](Get-Content $pidFile -Raw).Trim()
$process = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
if ($process -and $process.ProcessName -eq 'java') {
  Stop-Process -Id $targetPid -Force
  Write-Host "已停止进销存系统 PID=$targetPid"
} else {
  Write-Host '进程已不存在'
}
Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
