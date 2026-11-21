param(
    [string]$AppDir = $env:IVS_HOME
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw '请右键“主机端-一键开启局域网访问.bat”，选择“以管理员身份运行”。'
}

function Find-IvsApplication {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($AppDir) { $candidates.Add((Join-Path $AppDir 'IVS.exe')) }
    $candidates.Add((Join-Path $env:LOCALAPPDATA 'Programs\IVS\IVS.exe'))
    $candidates.Add((Join-Path $env:ProgramFiles 'IVS\IVS.exe'))
    if (${env:ProgramFiles(x86)}) { $candidates.Add((Join-Path ${env:ProgramFiles(x86)} 'IVS\IVS.exe')) }
    $candidates.Add((Join-Path $PSScriptRoot 'IVS.exe'))
    $candidates.Add((Join-Path (Split-Path -Parent $PSScriptRoot) 'IVS.exe'))
    $candidates.Add((Join-Path (Split-Path -Parent $PSScriptRoot) 'IVS\IVS.exe'))
    $candidates.Add((Join-Path (Split-Path -Parent $PSScriptRoot) 'windows-app\IVS\IVS.exe'))

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    if (Test-Path -LiteralPath 'C:\Users') {
        $profiles = Get-ChildItem -LiteralPath 'C:\Users' -Directory -ErrorAction SilentlyContinue
        foreach ($profile in $profiles) {
            $candidate = Join-Path $profile.FullName 'AppData\Local\Programs\IVS\IVS.exe'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }

    $searchRoots = @(
        $PSScriptRoot,
        (Split-Path -Parent $PSScriptRoot)
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
    foreach ($root in $searchRoots) {
        $found = Get-ChildItem -LiteralPath $root -Filter 'IVS.exe' -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\Windows\\' } |
            Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

$appExe = Find-IvsApplication
if (-not $appExe) {
    Write-Host ''
    Write-Host '未找到容大进销存系统 IVS.exe。' -ForegroundColor Red
    Write-Host '请选择以下任一方式：' -ForegroundColor Yellow
    Write-Host '1. 双击桌面的“容大进销存系统.exe”完成安装后再运行本工具；'
    Write-Host '2. 将本工具放到绿色版 IVS.exe 所在文件夹后重新运行；'
    Write-Host '3. 如程序安装在其他 Windows 用户下，请确保当前管理员有权访问该目录。'
    Write-Host ''
    throw "未检测到 IVS.exe。当前用户目录检查位置：$env:LOCALAPPDATA\Programs\IVS\IVS.exe"
}

$appDir = Split-Path -Parent $appExe
$dataDir = Join-Path $appDir 'data'
$launcher = Join-Path $appDir 'IVSLauncher.exe'

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

if (-not $healthy) {
    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    $previousDataDir = $env:IVS_DATA_DIR
    $env:IVS_DATA_DIR = $dataDir
    try {
        if (Test-Path -LiteralPath $launcher -PathType Leaf) {
            Start-Process -FilePath $launcher -WorkingDirectory $appDir -WindowStyle Hidden
        } else {
            Start-Process -FilePath $appExe -WorkingDirectory $appDir -WindowStyle Hidden `
                -ArgumentList @("--server.port=$port", '--ivs.desktop.open-browser=false')
        }
    } finally {
        if ($null -eq $previousDataDir) { Remove-Item Env:IVS_DATA_DIR -ErrorAction SilentlyContinue }
        else { $env:IVS_DATA_DIR = $previousDataDir }
    }
    for ($i = 0; $i -lt 120; $i++) {
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
Write-Host ("程序位置：" + $appExe) -ForegroundColor Gray
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
        if (-not $primaryIp) { $primaryIp = $ip.IPAddress }
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
Write-Host '1. 客户端必须与上方主机地址处于同一网段。' -ForegroundColor Yellow
Write-Host '2. 路由器必须关闭“终端隔离 / AP 隔离 / 访客网络隔离”。' -ForegroundColor Yellow
Write-Host '3. 客户端不要使用 VPN，浏览器代理请排除 192.168.*、10.*、172.16.*。' -ForegroundColor Yellow
Write-Host '4. Windows 网络建议设为“专用网络”；本工具已放行 LocalSubnet。' -ForegroundColor Yellow
Write-Host '5. 主机 IP 变化后，必须把新地址重新发给客户端。' -ForegroundColor Yellow
Write-Host '6. 客户端请使用 v1.9.30 或更新版本的局域网登录工具。' -ForegroundColor Yellow
Write-Host '请勿关闭这台主机的进销存系统。' -ForegroundColor Yellow
Write-Host ''