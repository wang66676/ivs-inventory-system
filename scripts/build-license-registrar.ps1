[CmdletBinding()]
param(
  [string]$Root = (Split-Path -Parent $PSScriptRoot),
  [string]$PrivateKey = "$env:USERPROFILE\.codex\ivs-license-private.xml"
)
$ErrorActionPreference = 'Stop'
$compilerCandidates = @(
  (Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'),
  (Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe')
)
$compiler = $compilerCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $compiler) { throw '未找到 .NET Framework C# 编译器 csc.exe。' }
$source = Join-Path $Root 'packaging\license-registrar\IVSRegistrar.cs'
$output = Join-Path $Root 'packaging\license-registrar\IVSRegistrar.exe'
& $compiler /nologo /target:winexe /optimize+ /platform:anycpu /utf8output `
  /reference:System.dll /reference:System.Drawing.dll /reference:System.Windows.Forms.dll `
  "/out:$output" $source
if ($LASTEXITCODE -ne 0) { throw "注册器编译失败：$LASTEXITCODE" }
Write-Output "Built $output"
if (Test-Path -LiteralPath $PrivateKey) {
  Write-Output "Private key found: $PrivateKey"
} else {
  Write-Warning '未找到私钥；注册器可编译，但生成注册码时需要同目录 license-private.xml。'
}
