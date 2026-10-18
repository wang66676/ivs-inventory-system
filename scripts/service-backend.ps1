$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\java.exe'
$jar = Join-Path $root 'backend\app\target\ivs-app-0.1.0-SNAPSHOT.jar'
$log = Join-Path $root 'logs\backend-service.log'
New-Item -ItemType Directory -Force -Path (Join-Path $root 'logs') | Out-Null
Set-Location (Join-Path $root 'backend')
& $java '-XX:MaxRAMPercentage=75.0' -jar $jar '--spring.profiles.active=prod' *>> $log
