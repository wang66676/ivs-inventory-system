$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logs = @(
  (Join-Path $root 'logs\tunnel.err.log'),
  (Join-Path $root 'logs\tunnel.out.log'),
  (Join-Path $root 'logs\tunnel.log')
)
for ($i = 0; $i -lt 60; $i++) {
  foreach ($log in $logs) {
    if (Test-Path $log) {
      $match = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -AllMatches |
        ForEach-Object { $_.Matches.Value } | Select-Object -Last 1
      if ($match) { Write-Output $match; exit 0 }
    }
  }
  Start-Sleep -Seconds 2
}
throw '暂未从隧道日志中获取到公网地址'
