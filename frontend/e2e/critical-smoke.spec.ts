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
    '/policy/fund-pool', '/billing/refunds', '/reports/monthly', '/system/approval-tasks',
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
  await page.getByRole('button', { name: '登记进项蓝字', exact: true }).click()
  await expect(page.getByTitle('发票类型', { exact: true })).toBeVisible()
  await expect(page.getByTitle('发票号码', { exact: true })).toBeVisible()
  await expect(page.getByTitle('发票金额（价税合计）', { exact: true })).toBeVisible()
  await expect(page.getByTitle('开票日期', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('选择年月日')).toBeVisible()
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