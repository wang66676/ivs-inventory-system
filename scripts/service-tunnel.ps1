$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$cloudflared = Join-Path $root 'tools\cloudflared.exe'
$outLog = Join-Path $root 'logs\tunnel.out.log'
$errLog = Join-Path $root 'logs\tunnel.err.log'
New-Item -ItemType Directory -Force -Path (Join-Path $root 'logs') | Out-Null
if (-not (Test-Path $cloudflared)) { throw "未找到 cloudflared：$cloudflared" }
$process = Start-Process -FilePath $cloudflared `
  -ArgumentList @('tunnel', '--url', 'http://127.0.0.1:8090', '--no-autoupdate') `
  -NoNewWindow -PassThru -Wait `
  -RedirectStandardOutput $outLog -RedirectStandardError $errLog
exit $process.ExitCode
