param([string]$EnvFile = '.env.production')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $root $EnvFile
if (-not (Test-Path $envPath)) { throw "缺少生产环境文件：$envPath" }

$values = @{}
Get-Content $envPath | ForEach-Object {
  if ($_ -match '^([^#][^=]*)=(.*)$') { $values[$Matches[1].Trim()] = $Matches[2].Trim() }
}
$required = @('POSTGRES_DB','POSTGRES_USER','POSTGRES_PASSWORD','IVS_PUBLIC_HOST','ACME_EMAIL',
  'IVS_CORS_ALLOWED_ORIGINS','BOOTSTRAP_ADMIN_USERNAME','BOOTSTRAP_ADMIN_PASSWORD')
foreach ($key in $required) {
  if ([string]::IsNullOrWhiteSpace($values[$key])) { throw "生产环境缺少 $key" }
}
foreach ($key in @('POSTGRES_PASSWORD','BOOTSTRAP_ADMIN_PASSWORD')) {
  if ($values[$key] -match 'CHANGE_ME|admin123|password') { throw "$key 仍为默认或弱密码" }
  if ($values[$key].Length -lt 12) { throw "$key 至少需要 12 位" }
}
if ($values['IVS_PUBLIC_HOST'] -match 'example\.com|localhost|127\.0\.0\.1') {
  throw 'IVS_PUBLIC_HOST 必须替换为正式域名'
}
if ($values['ACME_EMAIL'] -match 'example\.com') { throw 'ACME_EMAIL 必须是正式邮箱' }
if ($values['IVS_CORS_ALLOWED_ORIGINS'] -notmatch 'https://wang66676.github.io') {
  throw 'IVS_CORS_ALLOWED_ORIGINS 必须包含 https://wang66676.github.io'
}

docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker 引擎不可用' }

Push-Location $root
try {
  docker compose --env-file $envPath -f docker-compose.prod.yml config *> $null
  if ($LASTEXITCODE -ne 0) { throw 'docker compose 配置校验失败' }
} finally { Pop-Location }

$dns = Resolve-DnsName $values['IVS_PUBLIC_HOST'] -ErrorAction Stop |
  Where-Object { $_.Type -in @('A','AAAA','CNAME') }
if (-not $dns) { throw "域名 $($values['IVS_PUBLIC_HOST']) 没有 DNS 记录" }

if (-not (Test-NetConnection $values['IVS_PUBLIC_HOST'] -Port 80 -InformationLevel Quiet -WarningAction SilentlyContinue)) {
  throw '公网 80 端口不可达，Caddy 无法申请证书'
}
if (-not (Test-NetConnection $values['IVS_PUBLIC_HOST'] -Port 443 -InformationLevel Quiet -WarningAction SilentlyContinue)) {
  throw '公网 443 端口不可达，请检查防火墙或安全组'
}
Write-Host "生产预检通过：https://$($values['IVS_PUBLIC_HOST'])"
