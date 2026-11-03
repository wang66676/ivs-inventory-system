$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$currentFile = Join-Path $root '.run\online-url.txt'
$publicUrl = $null

function Sync-GitHubPagesApiUrl([string]$url) {
  $tokenFile = Join-Path $env:USERPROFILE '.codex\github_token.txt'
  if (-not (Test-Path -LiteralPath $tokenFile)) {
    $tokenFile = 'C:\Users\Administrator\.codex\github_token.txt'
  }
  if (-not (Test-Path -LiteralPath $tokenFile)) { return }
  $token = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
  if ([string]::IsNullOrWhiteSpace($token)) { return }
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
  if ($changed) {
    $body = @{ name = 'IVS_API_BASE_URL'; value = $apiUrl } | ConvertTo-Json
    try {
      Invoke-RestMethod -Method Patch -Uri $variableUrl -Headers $headers `
        -ContentType 'application/json' -Body $body -TimeoutSec 30 | Out-Null
    } catch {
      $createBody = @{ name = 'IVS_API_BASE_URL'; value = $apiUrl } | ConvertTo-Json
      try {
        Invoke-RestMethod -Method Post `
          -Uri 'https://api.github.com/repos/wang66676/ivs-inventory-system/actions/variables' `
          -Headers $headers -ContentType 'application/json' -Body $createBody -TimeoutSec 30 | Out-Null
      } catch { return }
    }
    try {
      Invoke-RestMethod -Method Post `
        -Uri 'https://api.github.com/repos/wang66676/ivs-inventory-system/actions/workflows/pages.yml/dispatches' `
        -Headers $headers -ContentType 'application/json' -Body (@{ ref = 'main' } | ConvertTo-Json) `
        -TimeoutSec 30 | Out-Null
    } catch { }
  }
}

$logs = Get-ChildItem (Join-Path $root 'logs') -File -Filter 'tunnel-*.log' -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending
foreach ($log in $logs) {
  $match = Select-String -Path $log.FullName -Pattern 'https://(?!api\.)[a-z0-9-]+\.trycloudflare\.com' -AllMatches |
    ForEach-Object { $_.Matches.Value } | Select-Object -Last 1
  if ($match) { $publicUrl = $match; break }
}
if ($publicUrl) {
  Set-Content -Path $currentFile -Value $publicUrl -Encoding ascii
  Sync-GitHubPagesApiUrl $publicUrl
  # 临时隧道首次或跨区域请求偶尔会超过 20 秒；连续重试后再判定故障，避免无谓更换网址。
  for ($attempt = 0; $attempt -lt 3; $attempt++) {
    $health = & curl.exe -fsS --max-time 45 "$publicUrl/actuator/health" 2>$null
    if ($LASTEXITCODE -eq 0 -and $health -match 'UP') { exit 0 }
    Start-Sleep -Seconds 3
  }
}
schtasks /End /TN '\IVS-Tunnel' 2>$null | Out-Null
Start-Sleep -Seconds 2
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
schtasks /Run /TN '\IVS-Tunnel' 2>$null | Out-Null

