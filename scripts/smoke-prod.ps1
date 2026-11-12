param([string]$BaseUrl = 'http://localhost:8090', [string]$Username = 'admin', [string]$Password = 'admin123')
$ErrorActionPreference = 'Stop'
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 10
if ($health.status -ne 'UP') { throw '健康检查不是 UP' }
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/platform/auth/login" -ContentType 'application/json' -Body (@{username=$Username;password=$Password}|ConvertTo-Json)
$h = @{ Authorization = "Bearer $($login.token)" }
$paths = @('/api/ivs/policies','/api/ivs/retails','/api/ivs/oem-claims','/api/ivs/rebate-fund-pool',
  '/api/ivs/reports/monthly','/api/ivs/reports/stock-funds','/api/ivs/migrations','/api/ivs/product-serials',
  '/api/ivs/approval-rules','/api/ivs/approval-tasks?status=PENDING','/api/ivs/purchase-invoices',
  '/api/ivs/payables','/api/ivs/refunds','/api/ivs/notifications/unread-count')
foreach ($path in $paths) { Invoke-RestMethod -Uri ($BaseUrl + $path) -Headers $h -TimeoutSec 20 | Out-Null }
foreach ($page in @('/login','/dashboard','/policy/claims','/inventory/stocktakes','/reports/monthly',
  '/system/migration','/system/approval-tasks','/system/approvals','/procurement/invoices','/billing/refunds')) {
  $response = Invoke-WebRequest -Uri ($BaseUrl + $page) -TimeoutSec 15
  if ($response.StatusCode -ne 200) { throw "页面 $page 返回 $($response.StatusCode)" }
}
Write-Host '生产冒烟测试通过'

