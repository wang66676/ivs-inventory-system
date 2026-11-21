[CmdletBinding()]
param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$compilerCandidates = @(
  (Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'),
  (Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe')
)
$compiler = $compilerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $compiler) {
  throw '未找到 .NET Framework C# 编译器 csc.exe。'
}

$source = Join-Path $Root 'packaging\lan-client\LanClient.cs'
$icon = Join-Path $Root 'packaging\windows\IVS.ico'
$output = Join-Path $Root 'packaging\lan-client\容大进销存-局域网登录.exe'

& $compiler /nologo /target:winexe /optimize+ /platform:anycpu /utf8output `
  "/win32icon:$icon" "/out:$output" `
  /reference:System.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll $source
if ($LASTEXITCODE -ne 0) {
  throw "LAN client build failed with exit code $LASTEXITCODE"
}

Write-Output "Built $output"