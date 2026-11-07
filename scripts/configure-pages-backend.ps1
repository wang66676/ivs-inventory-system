param(
  [Parameter(Mandatory=$true)][string]$BaseUrl
)
$ErrorActionPreference = 'Stop'
$normalized = $BaseUrl.Trim().TrimEnd('/')
if ($normalized -notmatch '^https://[^/]+$') { throw 'BaseUrl 必须是 https://域名 形式，不能包含路径' }
$root = Split-Path -Parent $PSScriptRoot
$script = Join-Path $root 'scripts\sync-pages-api-url.mjs'
Push-Location $root
try {
  & node $script $normalized
  if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne 10) { throw "更新 Pages 后端地址失败，退出码 $LASTEXITCODE" }
} finally { Pop-Location }
Write-Host "GitHub Pages 后端地址已指向：$normalized/api"
