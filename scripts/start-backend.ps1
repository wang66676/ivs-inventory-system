# 启动进销存后端（后台运行，等待健康检查通过）
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\app\target\ivs-app-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw "未找到构建产物，请先执行 scripts\build-all.ps1：$jar" }
New-Item -ItemType Directory -Force -Path (Join-Path $root 'logs'), (Join-Path $root '.run') | Out-Null

$proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) `
  -WorkingDirectory (Join-Path $root 'backend') -WindowStyle Hidden `
  -RedirectStandardOutput (Join-Path $root 'logs\backend.out.log') `
  -RedirectStandardError  (Join-Path $root 'logs\backend.err.log') -PassThru
Set-Content -Path (Join-Path $root '.run\backend.pid') -Value $proc.Id -Encoding ascii
Write-Host "后端已启动 PID=$($proc.Id)，等待健康检查..."

for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 3
  try {
    $health = Invoke-RestMethod -Uri 'http://localhost:8090/actuator/health' -TimeoutSec 3
    if ($health.status -eq 'UP') {
      Write-Host '健康检查 UP'
      Write-Host '访问地址: http://localhost:8090  （默认账号 admin / admin123，首次登录后请修改）'
      exit 0
    }
  } catch { }
}
Write-Host '健康检查未通过，请查看 logs\backend.err.log'
exit 1