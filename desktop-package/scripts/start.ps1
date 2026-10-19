$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'app\ivs-app.jar'
$pidFile = Join-Path $root '.run\backend.pid'
$logDir = Join-Path $root 'logs'
$curl = Join-Path $env:SystemRoot 'System32\curl.exe'
New-Item -ItemType Directory -Force -Path $logDir, (Join-Path $root '.run'), (Join-Path $root 'data'), (Join-Path $root 'backups') | Out-Null

function Test-IvsHealth {
  try {
    $result = & $curl -fsS --max-time 4 'http://127.0.0.1:8090/actuator/health' 2>$null
    $body = ($result | Out-String).Trim()
    return ($LASTEXITCODE -eq 0 -and $body.Contains('"status":"UP"'))
  } catch {
    return $false
  }
}

if (Test-IvsHealth) {
  Write-Host '进销存系统已在运行，正在打开浏览器...'
  Start-Process 'http://localhost:8090'
  exit 0
}

if (Test-Path $pidFile) {
  $oldPid = [int](Get-Content $pidFile -Raw).Trim()
  $old = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
  if ($old -and $old.ProcessName -eq 'java') {
    Write-Host "系统进程正在启动，PID=$oldPid，请稍候..."
    Start-Sleep -Seconds 5
    if (Test-IvsHealth) { Start-Process 'http://localhost:8090'; exit 0 }
  }
  Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

if (-not (Test-Path $java)) { throw '未找到内置 Java 运行环境：runtime\bin\java.exe' }
if (-not (Test-Path $jar)) { throw '未找到系统程序：app\ivs-app.jar' }

$listener = Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
  Write-Host '端口 8090 已被其他程序占用，且不是可用的进销存系统。请先关闭占用程序。'
  exit 1
}

$args = @(
  '-XX:MaxRAMPercentage=60.0',
  '-Dfile.encoding=UTF-8',
  '-jar', $jar,
  '--spring.profiles.active=prod',
  '--server.address=0.0.0.0',
  '--server.port=8090'
)
$process = Start-Process -FilePath $java -ArgumentList $args -WorkingDirectory $root -WindowStyle Hidden `
  -RedirectStandardOutput (Join-Path $logDir 'backend.out.log') `
  -RedirectStandardError (Join-Path $logDir 'backend.err.log') -PassThru
Set-Content -Path $pidFile -Value $process.Id -Encoding ascii
Write-Host "进销存系统正在启动，PID=$($process.Id)..."
for ($i = 0; $i -lt 90; $i++) {
  Start-Sleep -Seconds 2
  if (Test-IvsHealth) {
    Write-Host '启动成功'
    Write-Host '本机访问：http://localhost:8090'
    Write-Host '局域网访问地址：'
    try {
      Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object {
        $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'
      } | ForEach-Object { Write-Host ("  http://" + $_.IPAddress + ":8090") }
    } catch {
      [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
        Where-Object { $_.AddressFamily -eq 'InterNetwork' -and $_.IPAddressToString -notlike '127.*' } |
        ForEach-Object { Write-Host ("  http://" + $_.IPAddressToString + ":8090") }
    }
    Start-Process 'http://localhost:8090'
    exit 0
  }
}
Write-Host '启动失败，最近日志如下：'
Get-Content (Join-Path $logDir 'backend.err.log') -Tail 20 -ErrorAction SilentlyContinue
Get-Content (Join-Path $logDir 'backend.out.log') -Tail 20 -ErrorAction SilentlyContinue
exit 1
