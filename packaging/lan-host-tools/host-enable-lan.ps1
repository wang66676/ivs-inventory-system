$ErrorActionPreference = 'Stop'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw '请右键“主机端-一键开启局域网访问.bat”，选择“以管理员身份运行”。'
}

$appDir = Join-Path $env:LOCALAPPDATA 'Programs\IVS'
$appExe = Join-Path $appDir 'IVS.exe'
$launcher = Join-Path $appDir 'IVSLauncher.exe'
if (-not (Test-Path -LiteralPath $appExe)) {
    throw "未找到安装版 IVS.exe：$appExe"
}

Get-NetFirewallRule -DisplayName 'IVS','IVSConsole' -ErrorAction SilentlyContinue |
    Where-Object { $_.Direction -eq 'Inbound' -and $_.Action -eq 'Block' } |
    Disable-NetFirewallRule

Get-NetFirewallRule -DisplayName 'IVS LAN Server' -ErrorAction SilentlyContinue |
    Remove-NetFirewallRule

$port = 8090
$portFile = Join-Path $env:LOCALAPPDATA 'IVS\desktop-port.txt'
if (Test-Path -LiteralPath $portFile) {
    $saved = (Get-Content -LiteralPath $portFile -Raw).Trim()
    $parsed = 0
    if ([int]::TryParse($saved, [ref]$parsed) -and $parsed -gt 0) { $port = $parsed }
}

New-NetFirewallRule `
    -DisplayName 'IVS LAN Server' `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $port `
    -Profile Any `
    -RemoteAddress LocalSubnet `
    -Program $appExe `
    -Description 'IVS 进销存系统局域网主机访问' | Out-Null

$healthy = $false
try {
    $health = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/actuator/health" -f $port) -TimeoutSec 5
    $healthy = $health.status -eq 'UP'
} catch { }

if (-not $healthy -and (Test-Path -LiteralPath $launcher)) {
    Start-Process -FilePath $launcher -WindowStyle Hidden
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/actuator/health" -f $port) -TimeoutSec 3
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
    }
}

Write-Host ''
Write-Host '============================================' -ForegroundColor Cyan
Write-Host ' 容大进销存系统 - 局域网访问已配置' -ForegroundColor Green
Write-Host '============================================' -ForegroundColor Cyan
Write-Host '客户端统一使用下面的地址：' -ForegroundColor Yellow
$ips = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike '127.*' -and
        $_.IPAddress -notlike '169.254.*' -and
        $_.InterfaceAlias -notmatch 'vEthernet|Loopback'
    }

$primaryIp = $null
if ($ips) {
    foreach ($ip in $ips) {
        $primaryIp = $primaryIp ?? $ip.IPAddress
        Write-Host ("  http://{0}:{1}/" -f $ip.IPAddress, $port) -ForegroundColor White
    }
} else {
    Write-Host '  未检测到可用的 WLAN/有线网卡地址，请检查网络连接。' -ForegroundColor Red
}

$tcpOk = $false
if ($primaryIp) {
    $tcpOk = Test-NetConnection -ComputerName $primaryIp -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
}
$profile = Get-NetConnectionProfile -ErrorAction SilentlyContinue | Select-Object -First 1
$listen = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1

Write-Host ''
Write-Host ("当前服务端口：{0}" -f $port)
Write-Host ("服务状态：" + $(if ($healthy) { 'UP' } else { '未就绪，请重新打开进销存系统' }))
Write-Host ("监听地址：" + $(if ($listen) { $listen.LocalAddress } else { '未监听' }))
Write-Host ("本机网卡地址端口测试：" + $(if ($tcpOk) { '通过' } else { '失败' }))
Write-Host ("当前网络类别：" + $(if ($profile) { $profile.NetworkCategory } else { '未知' }))
Write-Host ''
Write-Host '整改要点：' -ForegroundColor Yellow
Write-Host '1. 其他电脑必须与主机在同一网段，例如主机为 192.168.18.x，客户端也必须是 192.168.18.x。' -ForegroundColor Yellow
Write-Host '2. 路由器必须关闭“终端隔离 / AP 隔离 / 访客网络隔离”。' -ForegroundColor Yellow
Write-Host '3. 客户端不要使用 VPN，浏览器代理请排除 192.168.*、10.*、172.16.*。' -ForegroundColor Yellow
Write-Host '4. Windows 网络建议设为“专用网络”；本工具已放行 LocalSubnet 的 TCP 8090。' -ForegroundColor Yellow
Write-Host '5. 主机 IP 变化后，必须把新地址重新发给客户端。' -ForegroundColor Yellow
Write-Host '6. 主机和客户端可运行：Test-NetConnection 主机IP -Port 8090' -ForegroundColor Yellow
Write-Host '请勿关闭这台主机的进销存系统。' -ForegroundColor Yellow
Write-Host ''
