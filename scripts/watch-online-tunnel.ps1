$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$currentFile = Join-Path $root '.run\online-url.txt'
$publicUrl = $null
$logs = Get-ChildItem (Join-Path $root 'logs') -File -Filter 'tunnel-*.log' -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending
foreach ($log in $logs) {
  $match = Select-String -Path $log.FullName -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches |
    ForEach-Object { $_.Matches.Value } | Select-Object -Last 1
  if ($match) { $publicUrl = $match; break }
}
if ($publicUrl) {
  Set-Content -Path $currentFile -Value $publicUrl -Encoding ascii
  $health = & curl.exe -fsS --max-time 20 "$publicUrl/actuator/health" 2>$null
  if ($LASTEXITCODE -eq 0 -and $health -match 'UP') { exit 0 }
}
schtasks /End /TN '\IVS-Tunnel' 2>$null | Out-Null
Start-Sleep -Seconds 2
Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
schtasks /Run /TN '\IVS-Tunnel' 2>$null | Out-Null
