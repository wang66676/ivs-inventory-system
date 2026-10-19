$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = Join-Path $root 'runtime\bin\java.exe'
$jar = Join-Path $root 'app\ivs-app.jar'
$pidFile = Join-Path $root '.run\backend.pid'
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir, (Join-Path $root '.run'), (Join-Path $root 'data'), (Join-Path $root 'backups') | Out-Null
if (-not (Test-Path $java)) { throw '未找到内置 Java 运行环境：runtime\bin\java.exe' }
if (-not (Test-Path $jar)) { throw '未找到系统程序：app\ivs-app.jar' }
if (Test-Path $pidFile) {
  $oldPid = [int](Get-Content $pidFile -Raw).Trim()
  $old = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
  if ($old -and $old.ProcessName -eq 'java') {
    Write-Host "系统已在运行，PID=$oldPid"
    Start-Process 'http://localhost:8090'
    exit 0
  }
  Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
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
  try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8090/actuator/health' -TimeoutSec 3 -NoProxy
    if ($health.status -eq 'UP') {
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
  } catch { }
}
Write-Host '启动失败，请查看 logs\backend.err.log'
exit 1
