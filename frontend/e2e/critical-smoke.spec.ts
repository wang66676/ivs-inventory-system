import { expect, test, type Page } from '@playwright/test'

const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || 'admin123'

async function login(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登 录', exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard$/, { timeout: 30000 })
}

test.describe.configure({ mode: 'serial' })
test('登录、付款入口重定向和关键业务页面冒烟', async ({ page }) => {
  test.setTimeout(180000)
  const errors: string[] = []
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('pageerror', (error) => errors.push(error.message))
  await login(page)

  await page.goto('/billing/payments')
  await expect(page).toHaveURL(/\/procurement\/payables$/)
  await expect(page.locator('body')).not.toContainText('业务功能待实现')

  const routes = [
    '/masterdata/products', '/procurement/orders', '/procurement/receipts',
    '/procurement/invoices', '/procurement/payables', '/inventory/dashboard',
    '/inventory/vin', '/sales/orders', '/sales/deliveries', '/retail/records',
    '/policy/pre-entitlements', '/policy/entitlements', '/policy/claims',
    '/policy/fund-pool', '/billing/sales-invoices', '/procurement/red-invoices', '/procurement/offsets', '/billing/refunds', '/reports/monthly', '/system/approval-tasks',
    '/system/health',
  ]
  for (const route of routes) {
    await page.goto(route)
    await expect(page.locator('body')).not.toContainText('页面框架已建立')
    await expect(page.locator('body')).not.toContainText('业务功能待实现')
  }
  expect(errors).toEqual([])
})

test('进项蓝字发票字段和日期选择可用', async ({ page }) => {
  await login(page)
  await page.goto('/procurement/invoices')
  await page.getByRole('button', { name: '单张登记蓝字', exact: true }).click()
  await expect(page.getByTitle('发票类型', { exact: true })).toBeVisible()
  await expect(page.getByTitle('发票号码', { exact: true })).toBeVisible()
  await expect(page.getByTitle('发票金额（价税合计）', { exact: true })).toBeVisible()
  await expect(page.getByTitle('开票日期', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('选择年月日')).toBeVisible()
})

test('销项发票支持 PDF 上传识别入口', async ({ page }) => {
  await login(page)
  await page.goto('/billing/sales-invoices')
  await page.getByRole('button', { name: '单张开票', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.locator('button').filter({ hasText: '上传销项发票 PDF 并自动识别' })).toBeVisible()
  await expect(dialog.getByText('销售订单编号', { exact: true })).toBeVisible()
  await expect(dialog.getByText('VIN/SN（可多选）', { exact: true })).toBeVisible()
})

test('进项蓝字发票支持一次多选 PDF 批量上传', async ({ page }) => {
  await login(page)
  await page.goto('/procurement/invoices')
  await page.getByRole('button', { name: '批量上传蓝字', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('批量上传并登记进项蓝字发票', { exact: true })).toBeVisible()
  await expect(dialog.locator('input[type="file"]')).toHaveAttribute('multiple', '')
  await expect(dialog.locator('input[type="file"]')).toHaveAttribute('accept', /application\/pdf/)
})

test('销项蓝字发票支持一次多选 PDF 批量上传', async ({ page }) => {
  await login(page)
  await page.goto('/billing/sales-invoices')
  await page.getByRole('button', { name: '批量上传蓝字发票', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('批量上传并登记销项蓝字发票', { exact: true })).toBeVisible()
  await expect(dialog.locator('input[type="file"]')).toHaveAttribute('multiple', '')
  await expect(dialog.locator('input[type="file"]')).toHaveAttribute('accept', /application\/pdf/)
})
test('整车批量入库无采购单时自动创建采购单并收货', async ({ page }) => {
  test.setTimeout(120000)
  await login(page)
  const token = await page.evaluate(() => localStorage.getItem('ivs_token'))
  const headers = { Authorization: `Bearer ${token}` }
  const suffix = Date.now().toString()
  const supplierName = `E2E整车厂-${suffix}`
  const warehouseName = `E2E整车仓-${suffix}`
  const productName = `E2E自动入库整车-${suffix}`
  const orderNo = `CG${new Date().toISOString().slice(2, 10).replace(/-/g, '')}${suffix.slice(-6)}`
  const vin = `LZZE2E${suffix.slice(-11)}`
  const supplier = await page.request.post('/api/ivs/suppliers', {
    headers, data: { name: supplierName, supplierType: 'VEHICLE_OEM' },
  })
  expect(supplier.ok()).toBeTruthy()
  const warehouse = await page.request.post('/api/ivs/warehouses', {
    headers, data: { code: `E2E-WH-${suffix.slice(-8)}`, name: warehouseName, type: 'MAIN' },
  })
  expect(warehouse.ok()).toBeTruthy()
  const category = await page.request.post('/api/ivs/product-categories', {
    headers, data: { code: `E2E-CAT-${suffix.slice(-8)}`, name: `E2E整车-${suffix}`,
      trackingMode: 'SERIAL', statusTemplate: 'VEHICLE', unit: '台', sortOrder: 0 },
  })
  expect(category.ok()).toBeTruthy()
  const categoryBody = await category.json()
  const product = await page.request.post('/api/ivs/products', {
    headers, data: { name: productName, categoryId: categoryBody.id, unit: '台', minPrice: 300000,
      purchasePriceRef: 350000 },
  })
  expect(product.ok()).toBeTruthy()

  const csv = [
    '订单编号,供应商,车系/商品,配置,VIN/SN,采购价,收货仓库,申请合格证日期',
    `${orderNo},${supplierName},${productName},标准配置,${vin},350000,${warehouseName},2026-11-19`,
  ].join('\n')
  await page.goto('/procurement/receipts')
  await page.locator('input[type="file"][accept*=".csv"]').setInputFiles({
    name: `receipt-${suffix}.csv`, mimeType: 'text/csv', buffer: Buffer.from(csv, 'utf8'),
  })
  await expect(page.getByText(/自动创建并审核/)).toBeVisible({ timeout: 60000 })
  await expect(page.getByText(vin, { exact: true })).toBeVisible()

  const created = await page.request.get(`/api/ivs/purchase-orders?keyword=${encodeURIComponent(orderNo)}`, { headers })
  expect(created.ok()).toBeTruthy()
  const orders = await created.json()
  expect(orders.some((order: any) => order.externalOrderNo === orderNo)).toBeTruthy()
})

test('全站表格分页支持10条20条50条选择', async ({ page }) => {
  await login(page)
  await page.goto('/masterdata/products')
  const pagination = page.locator('.ant-pagination-options')
  await expect(pagination).toBeVisible()
  await pagination.locator('.ant-select').click()
  const options = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content')
  await expect(options).toContainText(['10 / page', '20 / page', '50 / page'])
})
test('政策红字发票按 VIN/SN 冲减应付入口可用', async ({ page }) => {
  await login(page)
  await page.goto('/procurement/red-invoices')
  await page.getByRole('button', { name: /^登\s*记\s*返\s*利\s*红\s*字$/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('VIN/SN（按对应 VIN 冲减采购应付）', { exact: true })).toBeVisible()
  await expect(dialog.getByText('关联申报单', { exact: true })).toBeVisible()
  await page.goto('/procurement/offsets')
  await expect(page.getByText('政策红字返利合计', { exact: true })).toBeVisible()
  await expect(page.getByText('政策返利红字发票', { exact: true })).toBeVisible()
})

test('政策草稿支持编辑并按月度季度半年年度选择周期', async ({ page }) => {
  await login(page)
  const token = await page.evaluate(() => localStorage.getItem('ivs_token'))
  const name = `E2E-政策-${Date.now()}`
  const today = new Date()
  const from = today.toISOString().slice(0, 10)
  const to = new Date(today.getTime() + 30 * 86400000).toISOString().slice(0, 10)
  const created = await page.request.post('/api/ivs/policies', {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      name, policyType: 'PHASE_SUBSIDY', benefitType: 'AMOUNT', benefitValue: 1000,
      effFrom: from, effTo: to, referenceDateType: 'RETAIL_DATE', claimCycle: 'MONTHLY',
      claimCutoffDay: 5, priority: 0, stackingRule: 'ADDITIVE', limitType: 'AMOUNT', totalAmount: 10000,
    },
  })
  expect(created.ok()).toBeTruthy()
  await page.goto('/policy/policies')
  const row = page.getByRole('row').filter({ hasText: name })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: /^编\s*辑$/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('编辑政策草稿', { exact: true })).toBeVisible()
  const cycleItem = dialog.locator('.ant-form-item').filter({ hasText: '申报周期' })
  await cycleItem.locator('.ant-select').click()
  const options = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option-content')
  await expect(options).toContainText(['月度', '季度', '半年', '年度'])
})
test('实销登记支持同一次选择多个 VIN/SN', async ({ page }) => {
  await login(page)
  await page.goto('/retail/records')
  await page.getByRole('button', { name: /^登\s*记\s*实\s*销$/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('一张终端发票对应多台车时可一次选择多个 VIN/SN', { exact: true })).toBeVisible()
  await expect(dialog.locator('.ant-select-multiple')).toBeVisible()
  await expect(dialog.getByPlaceholder('多台车按 VIN/SN 顺序填写，用逗号分隔')).toBeVisible()
})

test('系统健康检查可加载并执行备份恢复验证', async ({ page }) => {
  test.setTimeout(120000)
  await login(page)
  await page.goto('/system/health')

  await expect(page.getByRole('button', { name: '立即备份并验证恢复', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '立即备份并验证恢复', exact: true }).click()
  await expect(page.getByText(/备份完成并已通过恢复验证/)).toBeVisible({ timeout: 90000 })
  await expect(page.getByText('通过', { exact: true })).toBeVisible()
})
