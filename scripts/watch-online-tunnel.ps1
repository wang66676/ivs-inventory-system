$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$currentFile = Join-Path $runDir 'online-url.txt'
$missingCountFile = Join-Path $runDir 'tunnel-missing-count.txt'
$metricsUrl = 'http://127.0.0.1:20241/metrics'
$publicUrl = $null

function Get-TunnelPublicUrl {
  $metrics = & curl.exe --noproxy '*' -fsS --max-time 10 $metricsUrl 2>$null
  if ($LASTEXITCODE -eq 0 -and $metrics) {
    $text = $metrics -join "`n"
    $metricMatch = [regex]::Match($text, 'userHostname="(https://(?!api\.)[a-z0-9-]+\.trycloudflare\.com)"')
    if ($metricMatch.Success) { return $metricMatch.Groups[1].Value }
  }

  $logs = Get-ChildItem (Join-Path $root 'logs') -File -Filter 'tunnel-*.log' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending
  foreach ($log in $logs) {
    $match = Select-String -Path $log.FullName -Pattern 'https://(?!api\.)[a-z0-9-]+\.trycloudflare\.com' -AllMatches |
      ForEach-Object { $_.Matches.Value } | Select-Object -Last 1
    if ($match) { return $match }
  }
  return $null
}

function Sync-GitHubPagesApiUrl([string]$url) {
  $syncScript = Join-Path $root 'scripts\sync-pages-api-url.mjs'
  $node = Get-Command node.exe -ErrorAction SilentlyContinue
  if ($node -and (Test-Path -LiteralPath $syncScript)) {
    & $node.Source $syncScript $url
    if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq 10) { return }
  }

  # 兼容未安装 Node 的环境；清除失效代理后再访问 GitHub API。
  $tokenFile = Join-Path $env:USERPROFILE '.codex\github_token.txt'
  if (-not (Test-Path -LiteralPath $tokenFile)) {
    $tokenFile = 'C:\Users\Administrator\.codex\github_token.txt'
  }
  if (-not (Test-Path -LiteralPath $tokenFile)) { return }
  $token = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
  if ([string]::IsNullOrWhiteSpace($token)) { return }
  $oldHttp = $env:HTTP_PROXY; $oldHttps = $env:HTTPS_PROXY; $oldAll = $env:ALL_PROXY
  $env:HTTP_PROXY = ''; $env:HTTPS_PROXY = ''; $env:ALL_PROXY = ''
  try {
    $headers = @{
      Authorization = "Bearer $token"
      Accept = 'application/vnd.github+json'
      'User-Agent' = 'IVS-Tunnel-Watchdog'
    }
    $variableUrl = 'https://api.github.com/repos/wang66676/ivs-inventory-system/actions/variables/IVS_API_BASE_URL'
    $apiUrl = "$url/api"
    $changed = $true
    try {
      $current = Invoke-RestMethod -Uri $variableUrl -Headers $headers -TimeoutSec 30
      $changed = $current.value -ne $apiUrl
    } catch { }
    if (-not $changed) { return }
    $body = @{ name = 'IVS_API_BASE_URL'; value = $apiUrl } | ConvertTo-Json
    try {
      Invoke-RestMethod -Method Patch -Uri $variableUrl -Headers $headers `
        -ContentType 'application/json' -Body $body -TimeoutSec 30 | Out-Null
    } catch {
      Invoke-RestMethod -Method Post `
        -Uri 'https://api.github.com/repos/wang66676/ivs-inventory-system/actions/variables' `
        -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 30 | Out-Null
    }
    Invoke-RestMethod -Method Post `
      -Uri 'https://api.github.com/repos/wang66676/ivs-inventory-system/actions/workflows/pages.yml/dispatches' `
      -Headers $headers -ContentType 'application/json' -Body (@{ ref = 'main' } | ConvertTo-Json) `
      -TimeoutSec 30 | Out-Null
  } catch {
  } finally {
    $env:HTTP_PROXY = $oldHttp; $env:HTTPS_PROXY = $oldHttps; $env:ALL_PROXY = $oldAll
  }
}

$publicUrl = Get-TunnelPublicUrl
if ($publicUrl) {
  Remove-Item -LiteralPath $missingCountFile -Force -ErrorAction SilentlyContinue
  Set-Content -Path $currentFile -Value $publicUrl -Encoding ascii
  Sync-GitHubPagesApiUrl $publicUrl
  # 临时隧道首次或跨区域请求偶尔会超过 45 秒；连续重试后再判定故障，避免无谓更换网址。
  for ($attempt = 0; $attempt -lt 4; $attempt++) {
    $health = & curl.exe --noproxy '*' -fsS --max-time 45 "$publicUrl/actuator/health" 2>$null
    if ($LASTEXITCODE -eq 0 -and $health -match 'UP') { exit 0 }
    Start-Sleep -Seconds 8
  }

  # Cloudflare 边缘重连期间公网健康检查可能短暂失败，但只要隧道连接已恢复就不要更换网址。
  $metrics = & curl.exe --noproxy '*' -fsS --max-time 10 $metricsUrl 2>$null
  if ($LASTEXITCODE -eq 0 -and (($metrics -join "`n") -match 'cloudflared_tunnel_ha_connections 1')) { exit 0 }
} else {
  # 启动阶段先给 cloudflared 时间建立连接，连续 3 分钟仍无公网地址才重启，避免启动竞态导致网址频繁变化。
  $tunnelProcess = Get-Process cloudflared -ErrorAction SilentlyContinue
  if ($tunnelProcess) {
    $missingCount = 1
    if (Test-Path -LiteralPath $missingCountFile) {
      $missingCount = [int](Get-Content -LiteralPath $missingCountFile -Raw) + 1
    }
    Set-Content -Path $missingCountFile -Value $missingCount -Encoding ascii
    if ($missingCount -lt 3) { exit 0 }
  }
}

schtasks /End /TN '\IVS-Tunnel' 2>$null | Out-Null
Start-Sleep -Seconds 2
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
schtasks /Run /TN '\IVS-Tunnel' 2>$null | Out-Null