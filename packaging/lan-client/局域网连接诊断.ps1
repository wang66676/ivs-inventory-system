$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$addressFile = Join-Path $root '服务器地址.txt'
if (-not (Test-Path -LiteralPath $addressFile)) { throw '未找到服务器地址.txt' }
$urlText = (Get-Content -LiteralPath $addressFile -Raw).Trim()
$uri = [Uri]$urlText
$hostIp = $uri.Host
$port = if ($uri.Port -gt 0) { $uri.Port } else { 80 }

Write-Host '============================================' -ForegroundColor Cyan
Write-Host ' 容大进销存系统 - 局域网连接诊断' -ForegroundColor Cyan
Write-Host '============================================' -ForegroundColor Cyan
Write-Host ("目标地址：{0}" -f $urlText)
Write-Host ("主机 IP：{0}  端口：{1}" -f $hostIp, $port)

$prefix = ($hostIp -split '\.')[0..2] -join '.'
$localIps = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' }
$sameSubnet = $localIps | Where-Object { ($_.IPAddress -split '\.')[0..2] -join '.' -eq $prefix }

Write-Host ''
Write-Host '本机 IPv4 地址：' -ForegroundColor Yellow
$localIps | ForEach-Object { Write-Host ("  {0}  {1}/{2}" -f $_.InterfaceAlias, $_.IPAddress, $_.PrefixLength) }
Write-Host ("与主机网段一致：" + $(if ($sameSubnet) { '是' } else { '否' }))

$pingOk = Test-Connection -ComputerName $hostIp -Count 2 -Quiet -ErrorAction SilentlyContinue
$tcpOk = Test-NetConnection -ComputerName $hostIp -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
Write-Host ("Ping 主机：" + $(if ($pingOk) { '成功' } else { '失败' }))
Write-Host ("TCP {0} 端口：" -f $port + $(if ($tcpOk) { '成功' } else { '失败' }))

$proxyVars = @($env:HTTP_PROXY, $env:HTTPS_PROXY, $env:ALL_PROXY) | Where-Object { $_ }
Write-Host ("代理环境变量：" + $(if ($proxyVars.Count) { $proxyVars -join '; ' } else { '未设置' }))
Write-Host ''
Write-Host '诊断结论：' -ForegroundColor Yellow
if (-not $sameSubnet) {
    Write-Host '  客户端与主机不在同一网段。请连接同一个 Wi-Fi/局域网后再试。' -ForegroundColor Red
} elseif (-not $pingOk) {
    Write-Host '  Ping 不通，优先检查路由器“终端隔离/AP 隔离”、访客网络或客户端 VPN。' -ForegroundColor Red
} elseif (-not $tcpOk) {
    Write-Host '  Ping 通但 8090 不通，说明主机系统未启动、端口未监听或防火墙仍拦截。' -ForegroundColor Red
} else {
    Write-Host '  主机地址和端口可达。若浏览器仍无法登录，请检查浏览器代理、HTTPS 误填和登录地址。' -ForegroundColor Green
}
Write-Host ''
Write-Host '主机端应执行：主机端-一键开启局域网访问.bat' -ForegroundColor Yellow
Write-Host '按任意键关闭本窗口...' -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')