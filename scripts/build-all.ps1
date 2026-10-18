# 一键构建：前端 dist + 后端单包（含前端静态资源）
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot' }
$mvn = Join-Path $root 'tools\apache-maven-3.9.9\bin\mvn.cmd'
$settings = Join-Path $root 'tools\maven-settings.xml'

Write-Host '[1/2] 构建前端 (vue-tsc + vite build)...'
Push-Location (Join-Path $root 'frontend')
try {
  $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
  if ($pnpm) {
    & $pnpm.Source run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
  } else {
    # 没有全局 pnpm 时，直接使用已被 pnpm 安装到 node_modules 的本地构建工具。
    $node = Get-Command node -ErrorAction SilentlyContinue
    $vueTsc = Join-Path (Get-Location) 'node_modules\vue-tsc\bin\vue-tsc.js'
    $vite = Join-Path (Get-Location) 'node_modules\vite\bin\vite.js'
    if (-not $node -or -not (Test-Path $vueTsc) -or -not (Test-Path $vite)) {
      throw '未找到 pnpm/node 或前端依赖，请先在 frontend 目录安装依赖'
    }
    & $node.Source $vueTsc --noEmit
    if ($LASTEXITCODE -ne 0) { throw '前端 TypeScript 校验失败' }
    & $node.Source $vite build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
  }
} finally {
  Pop-Location
}

Write-Host '[2/2] 构建后端 (maven package，含测试)...'
& $mvn -B -o -s $settings -f (Join-Path $root 'backend\pom.xml') package
if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }

Write-Host ''
Write-Host ('产物: ' + (Join-Path $root 'backend\app\target\ivs-app-0.1.0-SNAPSHOT.jar'))
