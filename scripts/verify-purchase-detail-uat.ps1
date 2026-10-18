param(
  [string]$BaseUrl = 'http://localhost:8090',
  [string]$Username = 'admin',
  [string]$Password = 'admin123'
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$suffix = $stamp.Substring(8)
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
  $result = Invoke-RestMethod -Method Get -Uri ($BaseUrl + $Path) -Headers $headers -TimeoutSec 30
  if ($result -is [System.Array]) {
    return $result | ForEach-Object { $_ }
  }
  return $result
}

Write-Host '[1/7] 登录与健康检查'
$health = Invoke-RestMethod -Uri ($BaseUrl + '/actuator/health') -TimeoutSec 10
Assert-True ($health.status -eq 'UP') '健康检查不是 UP'
$login = Invoke-RestMethod -Method Post -Uri ($BaseUrl + '/api/platform/auth/login') `
  -ContentType 'application/json' -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -TimeoutSec 10
Assert-True ($null -ne $login.token) '登录未返回 Token'
$headers = @{ Authorization = "Bearer $($login.token)" }

Write-Host '[2/7] 创建 UAT 主数据'
$supplier = Post-Json '/api/ivs/suppliers' @{
  name = "UAT-采购明细厂家-$suffix"; supplierType = 'OEM'; contactName = 'UAT 采购联系人'
}
$warehouse = Post-Json '/api/ivs/warehouses' @{
  code = "UATPWH$suffix"; name = "UAT-采购明细仓-$suffix"; type = 'MAIN'
}
$category = Post-Json '/api/ivs/product-categories' @{
  code = "UATP-CAT-$suffix"; name = "UAT-采购明细整车-$suffix"; trackingMode = 'SERIAL';
  statusTemplate = 'VEHICLE'; unit = '台'; sortOrder = 100
}
$product = Post-Json '/api/ivs/products' @{
  name = "UAT-采购牵引车-$suffix"; categoryId = $category.id; unit = '台';
  series = 'Q3-UAT'; model = 'T31-UAT'; configCode = '6X4-AMT'; taxRate = 0.13
}
Assert-True ($product.series -eq 'Q3-UAT') '商品车系未保存'
Assert-True ($product.configCode -eq '6X4-AMT') '商品配置未保存'

Write-Host '[3/7] 发布两项返利政策'
$policyA = Post-Json '/api/ivs/policies' @{
  name = "UAT-采购返利A-$suffix"; policyType = 'PHASE_SUBSIDY'; supplierId = $supplier.id;
  categoryId = $category.id; productIds = @($product.id); benefitType = 'AMOUNT'; benefitValue = 10000;
  effFrom = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); effTo = (Get-Date).AddDays(30).ToString('yyyy-MM-dd');
  referenceDateType = 'RETAIL_DATE'; claimCycle = 'MONTHLY'; claimCutoffDay = 5;
  priority = 10; stackingRule = 'ADDITIVE'; limitType = 'AMOUNT'; totalAmount = 100000
}
$policyB = Post-Json '/api/ivs/policies' @{
  name = "UAT-采购返利B-$suffix"; policyType = 'STOCK_VEHICLE_SUBSIDY'; supplierId = $supplier.id;
  categoryId = $category.id; productIds = @($product.id); benefitType = 'AMOUNT'; benefitValue = 3000;
  effFrom = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd'); effTo = (Get-Date).AddDays(30).ToString('yyyy-MM-dd');
  referenceDateType = 'RETAIL_DATE'; claimCycle = 'MONTHLY'; claimCutoffDay = 5;
  priority = 5; stackingRule = 'ADDITIVE'; limitType = 'AMOUNT'; totalAmount = 50000
}
$policyA = Post-Json ("/api/ivs/policies/{0}/publish" -f $policyA.id) $null
$policyB = Post-Json ("/api/ivs/policies/{0}/publish" -f $policyB.id) $null
Assert-True ($policyA.status -eq 'PUBLISHED' -and $policyB.status -eq 'PUBLISHED') '政策未发布'

Write-Host '[4/7] 新建采购单并校验明细字段'
$externalOrderNo = "UAT-OEM-ORDER-$suffix"
$purchase = Post-Json '/api/ivs/purchase-orders' @{
  supplierId = $supplier.id; externalOrderNo = $externalOrderNo; warehouseId = $warehouse.id;
  remark = 'UAT 采购明细验收';
  lines = @(@{
    productId = $product.id; quantity = 2; unitPrice = 410000;
    policyIds = @($policyA.id, $policyB.id)
  })
}
Assert-True ($purchase.externalOrderNo -eq $externalOrderNo) '订单编号未保存'
Assert-True ([decimal]$purchase.totalAmount -eq 820000) '总计车款不是 820000'
Assert-True (@($purchase.lines).Count -eq 1) '采购明细行数不是 1'
$line = @($purchase.lines)[0]
Assert-True ($line.series -eq 'Q3-UAT') '车系未返回'
Assert-True ($line.configCode -eq '6X4-AMT') '配置未返回'
Assert-True ([decimal]$line.quantity -eq 2) '数量不是 2'
Assert-True ([decimal]$line.unitPrice -eq 410000) '单价不是 410000'
Assert-True ([decimal]$line.amount -eq 820000) '明细金额不是 820000'
Assert-True (@($line.policies).Count -eq 2) '返利政策不是 2 项'

Write-Host '[5/7] 审核并录入两条不同单价的 VIN'
$purchase = Post-Json ("/api/ivs/purchase-orders/{0}/approve" -f $purchase.id) $null
$vinPrefix = ('UATP' + $stamp).ToUpper()
$vin1 = $vinPrefix + '01'
$vin2 = $vinPrefix + '02'
$receipt = Post-Json ("/api/ivs/purchase-orders/{0}/receive" -f $purchase.id) @{
  warehouseId = $warehouse.id; remark = 'UAT 采购收货';
  lines = @(
    @{ orderLineId = $line.id; quantity = 1; serialNos = @($vin1); unitPrice = 410000 },
    @{ orderLineId = $line.id; quantity = 1; serialNos = @($vin2); unitPrice = 430000 }
  )
}
Assert-True ([decimal]$receipt.amount -eq 840000) '收货金额不是 840000'

Write-Host '[6/7] 校验 VIN 库存与单车采购价'
$serials = @(Get-Json ("/api/ivs/product-serials?keyword={0}" -f $vinPrefix))
Assert-True ($serials.Count -eq 2) 'VIN 库存记录不是 2 条'
$serial1 = $serials | Where-Object { $_.serialNo -eq $vin1 }
$serial2 = $serials | Where-Object { $_.serialNo -eq $vin2 }
Assert-True ($null -ne $serial1 -and $null -ne $serial2) '未找到两条 VIN'
Assert-True ($serial1.status -eq 'IN_STOCK' -and $serial2.status -eq 'IN_STOCK') 'VIN 状态不是在库'
Assert-True ([decimal]$serial1.purchasePrice -eq 410000) 'VIN1 单车采购价错误'
Assert-True ([decimal]$serial2.purchasePrice -eq 430000) 'VIN2 单车采购价错误'
$balances = @(Get-Json ("/api/ivs/inventory/balances?warehouseId={0}" -f $warehouse.id))
Assert-True ($balances.Count -eq 1) '库存余额记录不是 1 条'
Assert-True ([decimal]$balances[0].onHandQty -eq 2) '在库数量不是 2'
Assert-True ([decimal]$balances[0].amount -eq 840000) '库存金额不是 840000'

Write-Host '[7/7] 页面路由与采购单查询'
foreach ($page in @('/masterdata/products', '/procurement/orders', '/inventory/vin')) {
  $response = Invoke-WebRequest -Uri ($BaseUrl + $page) -TimeoutSec 15
  Assert-True ($response.StatusCode -eq 200) "页面 $page 返回 $($response.StatusCode)"
}
$found = @(Get-Json ("/api/ivs/purchase-orders?keyword={0}" -f $externalOrderNo))
Assert-True ($found.Count -eq 1) '按订单编号查询采购单失败'

[pscustomobject]@{
  Result = 'PASS'
  Supplier = $supplier.name
  ExternalOrderNo = $externalOrderNo
  SystemOrderNo = $purchase.orderNo
  VehicleSeries = $line.series
  Configuration = $line.configCode
  Quantity = 2
  UnitPrice = 410000
  TotalVehicleAmount = 820000
  PolicyCount = @($line.policies).Count
  PolicyNames = (@($line.policies) | ForEach-Object { $_.name }) -join ' / '
  Vin1 = $vin1
  Vin2 = $vin2
  ReceiptNo = $receipt.receiptNo
  StockAmount = [decimal]$balances[0].amount
  PagesChecked = 3
} | Format-List



