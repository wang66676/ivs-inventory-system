# 前端开发服务器（热更新），API 反向代理到 http://localhost:8090
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'frontend')
& pnpm run dev
Pop-Location