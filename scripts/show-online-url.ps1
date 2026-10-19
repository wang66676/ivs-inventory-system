$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.run'
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$currentFile = Join-Path $runDir 'online-url.txt'
$logs = Get-ChildItem (Join-Path $root 'logs') -File -Filter 'tunnel-*.log' -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending
foreach ($log in $logs) {
  $match = Select-String -Path $log.FullName -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches |
    ForEach-Object { $_.Matches.Value } | Select-Object -Last 1
  if ($match) {
    Set-Content -Path $currentFile -Value $match -Encoding ascii
    Write-Output $match
    exit 0
  }
}
throw '暂未从隧道日志中获取到公网地址'
