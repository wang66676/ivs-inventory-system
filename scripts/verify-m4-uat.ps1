param(
  [string]$BaseUrl = 'http://localhost:8090',
  [string]$Username = 'admin',
  [string]$Password = 'admin123'
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$suffix = $stamp.Substring(8)
$token = $null
$headers = @{}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw "验收失败：$Message" }
}

function Post-Json([string]$Path, $Body) {
  $json = if ($null -eq $Body) { '{}' } else { $Body | ConvertTo-Json -Depth 12 -Compress }
  return Invoke-RestMethod -Method Post -Uri ($BaseUrl + $Path) -Headers $headers `
    -ContentType 'application/json' -Body $json -TimeoutSec 30
}

function Get-Json([string]$Path) {
  return Invoke-RestMethod -Method Get -Uri ($BaseUrl + $Path) -Headers $headers -TimeoutSec 30
}

Write-Host '[1/9] 登录与健康检查'
$health = Invoke-RestMethod -Uri ($BaseUrl + '/actuator/health') -TimeoutSec 10
Assert-True ($health.status -eq 'UP') '健康检查不是 UP'
$login = Invoke-RestMethod -Method Post -Uri ($BaseUrl + '/api/platform/auth/login') `
  -ContentType 'application/json' -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -TimeoutSec 10
Assert-True ($null -ne $login.token) '登录未返回 Token'
$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host '[2/9] 创建 UAT 主数据'
$supplier = Post-Json '/api/ivs/suppliers' @{
  name = "UAT-某整车厂-$suffix"; supplierType = 'OEM'; contactName = 'UAT 联系人'
}
$warehouse = Post-Json '/api/ivs/warehouses' @{
  code = "UATWH$suffix"; name = "UAT-整车仓-$suffix"; type = 'MAIN'
}
$category = Post-Json '/api/ivs/product-categories' @{
  code = "UAT-CAT-$suffix"; name = "UAT-整车-$suffix"; trackingMode = 'SERIAL';
  statusTemplate = 'VEHICLE'; unit = '台'; sortOrder = 99
}
$product = Post-Json '/api/ivs/products' @{
  name = "UAT-Q3 牵引车-$suffix"; categoryId = $category.id; unit = '台'; brand = 'UAT';
  series = 'Q3'; model = 'T31'; minPrice = 0; salePriceGuide = 450000
}
$vin = ('UATVIN' + $stamp).ToUpper()
$serial = Post-Json '/api/ivs/product-serials' @{
  serialNo = $vin; productId = $product.id; warehouseId = $warehouse.id;
  purchasePrice = 420000; inboundAt = (Get-Date).ToUniversalTime().ToString('o'); remark = 'UAT 车辆'
}
$customer = Post-Json '/api/ivs/customers' @{
  name = "UAT-终端客户-$suffix"; customerType = 'INDIVIDUAL'; creditLimit = 1000000
}

Write-Host '[3/9] 录入并发布两条叠加政策'
$policyA = Post-Json '/api/ivs/policies' @{
  name = "UAT-阶段性补贴-$suffix"; policyType = 'PHASE_SUBSIDY'; supplierId = $supplier.id;
  productIds = @($product.id); benefitType = 'AMOUNT'; benefitValue = 10000;
  effFrom = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); effTo = (Get-Date).AddDays(30).ToString('yyyy-MM-dd');
  referenceDateType = 'RETAIL_DATE'; claimCycle = 'MONTHLY'; claimCutoffDay = 5;
  priority = 10; stackingRule = 'ADDITIVE'; limitType = 'AMOUNT'; totalAmount = 100000
}
$policyB = Post-Json '/api/ivs/policies' @{
  name = "UAT-建店补贴-$suffix"; policyType = 'STORE_SUBSIDY'; supplierId = $supplier.id;
  productIds = @($product.id); benefitType = 'AMOUNT'; benefitValue = 3000;
  effFrom = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); effTo = (Get-Date).AddDays(30).ToString('yyyy-MM-dd');
  referenceDateType = 'RETAIL_DATE'; claimCycle = 'MONTHLY'; claimCutoffDay = 5;
  priority = 5; stackingRule = 'ADDITIVE'; limitType = 'AMOUNT'; totalAmount = 50000
}
$policyA = Post-Json ("/api/ivs/policies/{0}/publish" -f $policyA.id) $null
$policyB = Post-Json ("/api/ivs/policies/{0}/publish" -f $policyB.id) $null
Assert-True ($policyA.status -eq 'PUBLISHED' -and $policyB.status -eq 'PUBLISHED') '政策未成功发布'

Write-Host '[4/9] 创建销售订单并勾选政策（预享受项）'
$order = Post-Json '/api/ivs/sales-orders' @{
  customerId = $customer.id; warehouseId = $warehouse.id; remark = 'UAT 政策兑现订单';
  lines = @(@{ productId = $product.id; quantity = 1; unitPrice = 450000; serialIds = @($serial.id) })
}
$pre = Post-Json '/api/ivs/pre-entitlements' @{
  orderId = $order.id; orderLineId = $order.lines[0].id; productId = $product.id; serialId = $serial.id;
  orderLineAmount = 450000; policyIds = @($policyA.id, $policyB.id);
  addedAfterOrder = $false; reason = $null
}
Assert-True (@($pre).Count -eq 2) '预享受项数量不是 2'
Assert-True ((@($pre) | Measure-Object -Property estimateAmount -Sum).Sum -eq 13000) '预享受金额合计不是 13000'

Write-Host '[5/9] 登记并确认实销'
$retail = Post-Json '/api/ivs/retails' @{
  retailDate = (Get-Date).ToString('yyyy-MM-dd');
  invoiceIssuedAt = (Get-Date).AddHours(-2).ToUniversalTime().ToString('o');
  serialId = $serial.id; customerId = $customer.id; orderId = $order.id;
  userName = 'UAT 车辆使用人'; userMobile = '13800000000'; plateNo = "沪UAT$suffix";
  salePrice = 450000; remark = 'UAT 实销'
}
$retail = Post-Json ("/api/ivs/retails/{0}/submit" -f $retail.id) $null
$retail = Post-Json ("/api/ivs/retails/{0}/verify" -f $retail.id) $null
Assert-True ($retail.status -eq 'VERIFIED') '实销未确认'

Write-Host '[6/9] 生成正式权益并合并申报'
$entitlements = Post-Json ("/api/ivs/entitlements/generate?orderId={0}&retailId={1}&retailDate={2}&serialId={3}" -f `
  $order.id, $retail.id, $retail.retailDate, $serial.id) $null
Assert-True (@($entitlements).Count -eq 2) '正式权益数量不是 2'
$claims = Post-Json '/api/ivs/entitlements/apply' @{
  entitlementIds = @($entitlements | ForEach-Object { $_.id }); merge = $true; remark = 'UAT 合并申报'
}
Assert-True (@($claims).Count -eq 1) '合并申报应只生成 1 张申报单'
$claim = @($claims)[0]
Assert-True ($claim.mergedClaim -eq $true) '申报单未标记合并'
Assert-True (@($claim.lines).Count -eq 2) '合并申报明细不是 2 行'
$claim = Post-Json ("/api/ivs/oem-claims/{0}/submit" -f $claim.id) $null
Assert-True ($claim.status -eq 'SUBMITTED') '申报单未提交'

Write-Host '[7/9] 厂家确认并登记差异'
$confirmLines = @()
foreach ($line in $claim.lines) {
  $isPhase = $line.policyName -like 'UAT-阶段性补贴*'
  $confirmed = if ($isPhase) { [decimal]$line.amount - 500 } else { [decimal]$line.amount }
  $confirmLines += @{
    lineId = $line.id; confirmedAmount = $confirmed;
    reason = if ($isPhase) { 'UAT 厂家按政策口径核减' } else { $null };
    disposition = if ($isPhase) { 'SELF_BORNE' } else { $null }
  }
}
$claim = Post-Json ("/api/ivs/oem-claims/{0}/confirm" -f $claim.id) @{
  lines = $confirmLines; disputed = $false; remark = 'UAT 厂家确认'
}
Assert-True ($claim.status -eq 'PARTIALLY_CONFIRMED') '申报单状态不是部分确认'
Assert-True ([decimal]$claim.confirmedAmount -eq 12500) '厂家确认额不是 12500'
Assert-True ([decimal]$claim.differenceAmount -eq 500) '申报差异不是 500'
Assert-True (@($claim.differences).Count -eq 1) '差异行数量不是 1'

Write-Host '[8/9] 登记红字发票并核验资金池'
$poolBefore = Get-Json '/api/ivs/rebate-fund-pool'
$balanceBefore = [decimal]$poolBefore.balanceAmount
$red = Post-Json '/api/ivs/purchase-invoices/red' @{
  supplierId = $supplier.id; amount = 12500; taxRate = 0.13;
  originalInvoiceNo = "UAT-BLUE-$suffix"; externalInvoiceNo = "UAT-RED-$suffix";
  claimId = $claim.id; remark = 'UAT 厂家红字'
}
$claim = Get-Json ("/api/ivs/oem-claims/{0}" -f $claim.id)
Assert-True ($claim.status -eq 'SETTLED') '红字登记后申报单未结算'
$pool = Get-Json '/api/ivs/rebate-fund-pool'
Assert-True ([decimal]$pool.balanceAmount -eq ($balanceBefore + 12500)) '资金池余额未按红字增加 12500'
$unitCosts = Get-Json '/api/ivs/policies/reports/unit-cost'
$uatCosts = @($unitCosts | Where-Object { [string]$_.retailId -eq [string]$retail.id })
Write-Host ("UAT retailId=" + $retail.id + " costRows=" + $uatCosts.Count + " allRows=" + $unitCosts.Count)
Assert-True ($uatCosts.Count -eq 2) '单车政策成本应有 2 条政策行'
Assert-True ((($uatCosts | Measure-Object -Property policyCost -Sum).Sum) -eq 12500) '政策成本合计不是 12500'

$differences = Get-Json '/api/ivs/policies/reports/claim-differences'
Assert-True (@($differences | Where-Object { $_.claimNo -eq $claim.claimNo }).Count -eq 1) '申报差异报表缺少 UAT 记录'

Write-Host '[9/9] 页面路由与报表接口冒烟'
$pages = @('/retail/records','/policy/policies','/policy/subsidy-pools','/policy/pre-entitlements',
           '/policy/entitlements','/policy/claims','/policy/fund-pool','/billing/red-invoices','/policy/reports')
foreach ($page in $pages) {
  $response = Invoke-WebRequest -Uri ($BaseUrl + $page) -TimeoutSec 15
  Assert-True ($response.StatusCode -eq 200) "页面 $page 返回 $($response.StatusCode)"
}
$reportPaths = @('/api/ivs/policies/reports/receivable-aging','/api/ivs/policies/reports/pool-progress',
                 '/api/ivs/policies/reports/fund-ledger','/api/ivs/policies/reports/unit-cost',
                 '/api/ivs/policies/reports/cost-rate','/api/ivs/policies/reports/claim-differences')
foreach ($path in $reportPaths) { Get-Json $path | Out-Null }

[pscustomobject]@{
  Result = 'PASS'
  Supplier = $supplier.name
  Product = $product.name
  SerialNo = $serial.serialNo
  RetailNo = $retail.retailNo
  ClaimNo = $claim.claimNo
  ClaimAmount = 13000
  ConfirmedAmount = 12500
  DifferenceAmount = 500
  RedInvoiceNo = $red.invoiceNo
  FundBalance = [decimal]$pool.balanceAmount
  FundDelta = 12500
  PagesChecked = $pages.Count
  ReportsChecked = $reportPaths.Count
} | Format-List






