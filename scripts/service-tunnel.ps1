$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$cloudflared = Join-Path $root 'tools\cloudflared.exe'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outLog = Join-Path $root "logs\tunnel-$stamp.out.log"
$errLog = Join-Path $root "logs\tunnel-$stamp.err.log"
New-Item -ItemType Directory -Force -Path (Join-Path $root 'logs') | Out-Null
if (-not (Test-Path $cloudflared)) { throw "未找到 cloudflared：$cloudflared" }
$process = Start-Process -FilePath $cloudflared `
  -ArgumentList @('tunnel', '--url', 'http://127.0.0.1:8090', '--no-autoupdate', '--protocol', 'http2', '--metrics', '127.0.0.1:20241') `
  -NoNewWindow -PassThru -Wait `
  -RedirectStandardOutput $outLog -RedirectStandardError $errLog
exit $process.ExitCode
