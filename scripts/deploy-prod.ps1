# 生产部署（Docker + PostgreSQL）
param([switch]$SkipPreflight)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env.production'
$compose = Join-Path $root 'docker-compose.prod.yml'
if (-not (Test-Path $envFile)) { throw "缺少 $envFile，请从 .env.production.example 复制并填写强密码" }
if (-not (Test-Path (Join-Path $root 'backend\app\target\ivs-app-0.1.0-SNAPSHOT.jar'))) {
  throw '缺少发布 JAR，请先执行 scripts\build-all.ps1'
}
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker 引擎不可用，请先启动 Docker Desktop 或服务器 Docker 服务' }
if (-not $SkipPreflight) { & (Join-Path $PSScriptRoot 'preflight-prod.ps1') -EnvFile $envFile }
Push-Location $root
try {
  docker compose --env-file $envFile -f $compose up -d --build
  if ($LASTEXITCODE -ne 0) { throw 'docker compose 启动失败' }
  $port = 8090
  foreach ($line in Get-Content $envFile) {
    if ($line -match '^APP_PORT=(.+)$') { $port = [int]$Matches[1].Trim() }
  }
  for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 3
    try {
      $health = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 5
      if ($health.status -eq 'UP') {
      $publicHost = $null
      foreach ($line in Get-Content $envFile) { if ($line -match '^IVS_PUBLIC_HOST=(.+)$') { $publicHost = $Matches[1].Trim() } }
      Write-Host "生产部署完成: http://localhost:$port"
      if ($publicHost) {
        try {
          $publicHealth = Invoke-RestMethod -Uri "https://$publicHost/actuator/health" -TimeoutSec 20
          if ($publicHealth.status -eq 'UP') { Write-Host "公网 HTTPS 已验证: https://$publicHost" }
        } catch { Write-Warning "本地服务已启动，但公网 HTTPS 暂未通过：$($_.Exception.Message)" }
      }
        exit 0
      }
    } catch { }
  }
  throw '健康检查未通过，请查看 docker compose logs app'
} finally { Pop-Location }
