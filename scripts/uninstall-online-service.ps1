$ErrorActionPreference = 'Continue'
Stop-ScheduledTask -TaskName 'IVS-Tunnel' -ErrorAction SilentlyContinue
Stop-ScheduledTask -TaskName 'IVS-Backend' -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName 'IVS-Tunnel' -Confirm:$false -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName 'IVS-Backend' -Confirm:$false -ErrorAction SilentlyContinue
Remove-NetFirewallRule -DisplayName 'IVS 8090' -ErrorAction SilentlyContinue
Write-Host '已停止并移除 IVS 在线服务任务'
