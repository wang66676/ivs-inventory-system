$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backendScript = Join-Path $root 'scripts\service-backend.ps1'
$tunnelScript = Join-Path $root 'scripts\service-tunnel.ps1'
$pwsh = (Get-Command powershell.exe -ErrorAction SilentlyContinue).Source
if (-not $pwsh) { $pwsh = (Get-Command pwsh.exe -ErrorAction Stop).Source }

$firewallName = 'IVS 8090'
if (-not (Get-NetFirewallRule -DisplayName $firewallName -ErrorAction SilentlyContinue)) {
  New-NetFirewallRule -DisplayName $firewallName -Direction Inbound -Protocol TCP -LocalPort 8090 -Action Allow -Profile Any | Out-Null
}

$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
  -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 10 -RestartInterval (New-TimeSpan -Minutes 1) `
  -MultipleInstances IgnoreNew -StartWhenAvailable
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

$backendAction = New-ScheduledTaskAction -Execute $pwsh -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backendScript`""
$tunnelAction = New-ScheduledTaskAction -Execute $pwsh -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$tunnelScript`""

Register-ScheduledTask -TaskName 'IVS-Backend' -Action $backendAction -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Register-ScheduledTask -TaskName 'IVS-Tunnel' -Action $tunnelAction -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null

Start-ScheduledTask -TaskName 'IVS-Backend'
Start-ScheduledTask -TaskName 'IVS-Tunnel'
Write-Host '已安装并启动 IVS-Backend 与 IVS-Tunnel 开机任务'
