# 停止进销存后端（按 PID 文件精确停止，避免影响其它 Java 进程）
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root '.run\backend.pid'
if (-not (Test-Path $pidFile)) { Write-Host '未找到 .run\backend.pid，无需停止'; exit 0 }
$targetPid = [int](Get-Content $pidFile -Raw).Trim()
$proc = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
if (-not $proc) { Write-Host "进程 $targetPid 已不存在"; Remove-Item $pidFile -Force; exit 0 }
if ($proc.ProcessName -ne 'java') { throw "PID $targetPid 不是 java 进程（$($proc.ProcessName)），已中止以免误杀" }
Stop-Process -Id $targetPid -Force
Remove-Item $pidFile -Force
Write-Host "已停止后端 PID=$targetPid"