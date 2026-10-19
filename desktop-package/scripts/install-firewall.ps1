$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  $script = $PSCommandPath
  Start-Process powershell.exe -Verb RunAs -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$script`"")
  exit 0
}
if (-not (Get-NetFirewallRule -DisplayName 'IVS 8090' -ErrorAction SilentlyContinue)) {
  New-NetFirewallRule -DisplayName 'IVS 8090' -Direction Inbound -Protocol TCP -LocalPort 8090 -Action Allow -Profile Any | Out-Null
}
Write-Host '已开放局域网 TCP 8090 端口'
