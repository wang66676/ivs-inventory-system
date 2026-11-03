import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { pinia } from '../stores'
import { useAuthStore } from '../stores/auth'

const Placeholder = () => import('../views/dashboard/ModulePlaceholderView.vue')

type MenuPage = {
  path: string
  name: string
  title: string
  description: string
  permission?: string
  component?: () => Promise<unknown>
  hideable?: boolean
}

/** 业务页面（M0 为占位，M1–M4 逐个替换为真实页面）。 */
const modulePages: MenuPage[] = [
  // 主数据
  { path: '/masterdata/categories', name: 'product-category', component: () => import('../views/masterdata/ProductCategoryView.vue'), title: '产品大项', description: '整车、动力电池与其他产品大项：管理策略（数量 / 一物一档 VIN/SN）、属性模板、状态机模板。新增产品大项只需配置，不改代码。', permission: 'IVS_READ' },
  { path: '/masterdata/products', name: 'product', component: () => import('../views/masterdata/ProductView.vue'), title: '商品（车型/SKU）', description: '商品档案：品牌、车系、车型、配置、税率、最低限价、属性扩展（jsonb）。', permission: 'IVS_READ' },
  { path: '/masterdata/warehouses', name: 'warehouse', component: () => import('../views/masterdata/WarehouseView.vue'), title: '仓库', description: '仓库与门店、资产池。', permission: 'IVS_READ' },
  { path: '/masterdata/suppliers', name: 'supplier', component: () => import('../views/masterdata/SupplierView.vue'), title: '供应商', description: '供应商档案，整车厂标记为 OEM（返利主体）。', permission: 'IVS_READ' },
  { path: '/masterdata/customers', name: 'customer', component: () => import('../views/masterdata/CustomerView.vue'), title: '客户', description: '客户档案、信用额度。', permission: 'IVS_READ' },

  // 采购
  { path: '/procurement/orders', name: 'purchase-order', component: () => import('../views/procurement/PurchaseOrderView.vue'), title: '采购单', description: '多行、跨产品大项的采购单与审核；整车按商品与数量下单，收货时逐台录入 VIN 与单车采购价。', permission: 'IVS_READ' },
  { path: '/procurement/receipts', name: 'purchase-receipt', component: () => import('../views/procurement/PurchaseReceiptView.vue'), title: '收货入库', description: '按采购单逐台录入 VIN / 电池 SN 与单车采购价并自动建档入库；数量商品按数量收货，确认时可使用返利抵扣车款。', permission: 'IVS_READ' },
  { path: '/procurement/returns', name: 'purchase-return', component: () => import('../views/procurement/PurchaseReturnView.vue'), title: '采购退货', description: '采购退货与应付冲减。', permission: 'IVS_READ' },
  { path: '/procurement/invoices', name: 'purchase-invoice', title: '进项发票', description: '厂家进项发票（含红字，关联申报单与权益）。', permission: 'IVS_FINANCE_READ' },
  { path: '/procurement/payables', name: 'supplier-payable', component: () => import('../views/procurement/PayableView.vue'), title: '供应商应付', description: '按收货单生成应付，支持发票匹配与付款核销。', permission: 'IVS_FINANCE_READ' },
  { path: '/procurement/offsets', name: 'rebate-offset', title: '返利抵扣', description: '入库单确认时的返利抵扣单：按厂家分账、余额分批使用、无需审批。', permission: 'IVS_FUND_OFFSET' },

  // 库存
  { path: '/inventory/dashboard', name: 'inventory-dashboard', component: () => import('../views/inventory/InventoryDashboardView.vue'), title: '库存看板', description: '按产品大项/仓库/品牌/状态与库龄分档汇总，含超期未实销预警。', permission: 'IVS_READ' },
  { path: '/inventory/stock', name: 'stock-balance', component: () => import('../views/inventory/StockQueryView.vue'), title: '库存查询', description: '按商品与仓库查询在库、锁定、可用数量与成本。', permission: 'IVS_READ' },
  { path: '/inventory/vin', name: 'vin-inventory', component: () => import('../views/inventory/VinInventoryView.vue'), title: 'VIN/SN 库存', description: '按 VIN/SN 展示一物一档库存明细：商品、仓库、单车采购价与状态。', permission: 'IVS_READ' },
  { path: '/inventory/ledger', name: 'stock-ledger', component: () => import('../views/inventory/StockLedgerView.vue'), title: '库存台账', description: '只追加的库存事实表：入库、出库、调拨、盘点、抵扣兑换。', permission: 'IVS_READ' },
  { path: '/inventory/locks', name: 'stock-lock', component: () => import('../views/inventory/StockLockView.vue'), title: '库存锁定', description: '销售订单锁货与超时自动释放。', permission: 'IVS_READ' },
  { path: '/inventory/stocktakes', name: 'stocktake', component: () => import('../views/inventory/StocktakeView.vue'), title: '库存盘点', description: '盘点单：草稿 → 过账，差异写台账。', permission: 'IVS_STOCK_ADJUST' },
  { path: '/inventory/transfers', name: 'stock-transfer', component: () => import('../views/inventory/StockTransferView.vue'), title: '库存调拨', description: '调拨单：审核、在途、签收与差异处理。', permission: 'IVS_STOCK_TRANSFER' },
  { path: '/inventory/age', name: 'stock-age', title: '库龄分析', description: '0-30 / 31-60 / 61-90 / 90+ 天库龄与呆滞预警。', permission: 'IVS_READ' },

  // 销售
  { path: '/sales/orders', name: 'sales-order', component: () => import('../views/sales/SalesOrderView.vue'), title: '销售订单', description: '销售订单；订单生成时勾选享受的政策（预享受项）。', permission: 'IVS_READ' },
  { path: '/sales/deliveries', name: 'sales-delivery', component: () => import('../views/sales/SalesDeliveryView.vue'), title: '出库发货', description: '出库单：指定序列号、结转成本、生成应收。', permission: 'IVS_READ' },
  { path: '/sales/returns', name: 'sales-return', component: () => import('../views/sales/SalesReturnView.vue'), title: '销售退货', description: '退货回库、恢复可售或转维修。', permission: 'IVS_READ' },
  { path: '/sales/installments', name: 'installment-plan', title: '分期收款', description: '以租代售的分期计划与逾期管理。', permission: 'IVS_READ' },
  { path: '/sales/receivables', name: 'receivable', component: () => import('../views/sales/ReceivableView.vue'), title: '应收管理', description: '应收台账、账龄与到期管理。', permission: 'IVS_FINANCE_READ' },
  { path: '/sales/receipts', name: 'receipt', component: () => import('../views/sales/ReceiptView.vue'), title: '收款管理', description: '收款登记与核销（一收多单、一单多收）。', permission: 'IVS_FINANCE_READ' },

  // 实销
  { path: '/retail/records', name: 'retail-record', component: () => import('../views/retail/RetailView.vue'), title: '实销登记', description: '实销登记：车辆使用人、手机号、终端发票开票时间（必填），车牌照号（选填）；实销日期须晚于开票时间。', permission: 'IVS_RETAIL_WRITE' },
  { path: '/retail/verify', name: 'retail-verify', component: () => import('../views/retail/RetailView.vue'), title: '实销复核', description: '实销复核、确认与冲正；确认后触发政策匹配与预享受项转可申请。', permission: 'IVS_RETAIL_VERIFY', hideable: true },
  { path: '/retail/ledger', name: 'retail-ledger', component: () => import('../views/retail/RetailView.vue'), title: '实销台账', description: '按期间/门店/车型的实销台账。', permission: 'IVS_READ', hideable: true },
  { path: '/retail/pending', name: 'retail-pending', title: '未实销预警', description: '已交付未实销清单（直接影响返利申报，必须预警）。', permission: 'IVS_READ' },

  // 政策与返利
  { path: '/policy/policies', name: 'policy', component: () => import('../views/policy/PolicyView.vue'), title: '政策管理', description: '政策手工录入：名目、车型多选、金额或比例、生效与失效日期、限制维度（时间/金额/条件）、参考时间、周期与截止日。', permission: 'IVS_POLICY_WRITE' },
  { path: '/policy/subsidy-pools', name: 'subsidy-pool', component: () => import('../views/policy/SubsidyPoolView.vue'), title: '政策额度池', description: '每条政策一个额度池：时间/金额/条件限制，逐台原子扣减，返完为止。', permission: 'IVS_SUBSIDY_POOL_MANAGE' },
  { path: '/policy/pre-entitlements', name: 'pre-entitlement', component: () => import('../views/policy/PreEntitlementView.vue'), title: '预享受项', description: '采购单选择返利政策后自动同步：待实销 → 可申请 → 已申请 → 已兑现。', permission: 'IVS_PRE_ENTITLEMENT_MANAGE' },
  { path: '/policy/applications', name: 'entitlement-apply', component: () => import('../views/policy/EntitlementView.vue'), title: '兑现申请', description: '实销后申请兑现（可批量），按政策周期归集到申报单。', permission: 'IVS_ENTITLEMENT_APPLY' },
  { path: '/policy/entitlements', name: 'entitlement', component: () => import('../views/policy/EntitlementView.vue'), title: '权益台账', description: '实销后生成的正式权益：面值、申报额、确认额、已兑现额。', permission: 'IVS_READ', hideable: true },
  { path: '/policy/claims', name: 'oem-claim', component: () => import('../views/policy/ClaimView.vue'), title: '申报单', description: '按厂家 + 政策 + 周期生成（可单张或合并），提交与差异登记。', permission: 'IVS_CLAIM_SUBMIT' },
  { path: '/policy/oem-confirm', name: 'oem-confirm', component: () => import('../views/policy/ClaimView.vue'), title: '厂家确认', description: '厂家确认结果登记与申报差异处置。', permission: 'IVS_CLAIM_CONFIRM', hideable: true },
  { path: '/policy/fund-pool', name: 'fund-pool', component: () => import('../views/policy/FundPoolView.vue'), title: '返利资金池', description: '一个资金池按厂家分账；入库单确认时抵扣同一厂家车款，余额分批使用，不允许预抵扣。', permission: 'IVS_FUND_POOL_VIEW' },
  { path: '/policy/reports', name: 'policy-report', component: () => import('../views/policy/PolicyReportView.vue'), title: '政策报表', description: '返利应收账龄、额度池进度、资金池台账、单车政策成本与申报差异。', permission: 'IVS_FINANCE_READ' },

  // 票据资金
  { path: '/billing/sales-invoices', name: 'sales-invoice', component: () => import('../views/billing/InvoiceView.vue'), title: '销项发票', description: '开票、作废与红冲（一期整车按全价开票）。', permission: 'IVS_INVOICE_WRITE' },
  { path: '/billing/red-invoices', name: 'red-invoice', component: () => import('../views/billing/RedInvoiceView.vue'), title: '红字发票', description: '进项红字（厂家 → 我方）为主通道；关联原蓝票与申报单。', permission: 'IVS_INVOICE_REDFLUSH' },
  { path: '/billing/refunds', name: 'refund', title: '退款', description: '退款单：审批 → 执行 → 成功/失败/重试 → 人工兜底。', permission: 'IVS_FINANCE_READ' },
  { path: '/billing/payments', name: 'payment', title: '付款', description: '付款与批次执行。', permission: 'IVS_FINANCE_READ' },

  // 报表
  { path: '/reports/monthly', name: 'report-monthly', component: () => import('../views/reports/MonthlyReportView.vue'), title: '进销存月报', description: '采购、销售、库存、返利月度汇总。', permission: 'IVS_FINANCE_READ' },
  { path: '/reports/gross-profit', name: 'report-gross-profit', component: () => import('../views/reports/GrossProfitView.vue'), title: '毛利分析', description: '按车型/订单/期间的毛利与政策成本率。', permission: 'IVS_FINANCE_READ' },
  { path: '/reports/unit-policy-cost', name: 'report-unit-policy-cost', component: () => import('../views/policy/PolicyReportView.vue'), title: '单车政策成本', description: '每台车：成交价、命中政策明细、返利合计、净车价。', permission: 'IVS_FINANCE_READ' },
  { path: '/reports/stock-funds', name: 'report-stock-funds', component: () => import('../views/reports/StockFundsView.vue'), title: '库存资金占用', description: '库存资金占用与周转分析。', permission: 'IVS_FINANCE_READ' },
]

export const menuGroups = [
  { key: 'masterdata', title: '主数据', items: modulePages.filter((p) => p.path.startsWith('/masterdata/')) },
  { key: 'procurement', title: '采购', items: modulePages.filter((p) => p.path.startsWith('/procurement/')) },
  { key: 'inventory', title: '库存', items: modulePages.filter((p) => p.path.startsWith('/inventory/')) },
  { key: 'sales', title: '销售', items: modulePages.filter((p) => p.path.startsWith('/sales/')) },
  { key: 'retail', title: '实销', items: modulePages.filter((p) => p.path.startsWith('/retail/')) },
  { key: 'policy', title: '政策与返利', items: modulePages.filter((p) => p.path.startsWith('/policy/')) },
  { key: 'billing', title: '票据资金', items: modulePages.filter((p) => p.path.startsWith('/billing/')) },
  { key: 'reports', title: '报表', items: modulePages.filter((p) => p.path.startsWith('/reports/')) },
]

export const hideablePages = modulePages.filter((page) => page.hideable)

export const pageTitles: Record<string, string> = Object.fromEntries(
  modulePages.map((page) => [page.path, page.title]),
)

const routerHistory = import.meta.env.VITE_ROUTER_MODE === 'hash'
  ? createWebHashHistory(import.meta.env.BASE_URL)
  : createWebHistory(import.meta.env.BASE_URL)

export const router = createRouter({
  history: routerHistory,
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/auth/LoginView.vue'), meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', name: 'dashboard', component: () => import('../views/dashboard/DashboardView.vue') },
    ...modulePages.map((page) => ({
      path: page.path,
      name: page.name,
      component: page.component ?? Placeholder,
      props: page.component ? undefined : { title: page.title, description: page.description },
      meta: { permission: page.permission },
    })),
    { path: '/system/organizations', name: 'organization-management', component: () => import('../views/system/OrganizationManagementView.vue'), meta: { permission: 'USER_MANAGE' } },
    { path: '/system/users', name: 'user-management', component: () => import('../views/system/UserManagementView.vue'), meta: { permission: 'USER_MANAGE' } },
    { path: '/system/roles', name: 'role-permission', component: () => import('../views/system/RolePermissionView.vue'), meta: { permission: 'USER_MANAGE' } },
    { path: '/system/audit', name: 'audit-log', component: () => import('../views/system/AuditLogView.vue'), meta: { permission: 'USER_MANAGE' } },
    { path: '/system/migration', name: 'data-migration', component: () => import('../views/system/DataMigrationView.vue'), meta: { permission: 'USER_MANAGE' } },
    { path: '/system/settings', name: 'system-settings', component: () => import('../views/system/SystemSettingView.vue'), meta: { permission: 'USER_MANAGE' } },
    { path: '/system/update', name: 'system-update', component: () => import('../views/system/SystemUpdateView.vue'), meta: { permission: 'IVS_READ' } },
    { path: '/system/approvals', name: 'approval-placeholder', component: Placeholder, props: { title: '审批配置', description: '审批矩阵：低价审批、库存调整、政策发布、额度池调整、资金池调整等阈值配置。' }, meta: { permission: 'USER_MANAGE' } },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.public) {
    if (to.path === '/login' && auth.token) return '/dashboard'
    return true
  }
  if (!auth.token) return '/login'
  if (!auth.user) {
    try {
      await auth.loadCurrentUser()
    } catch {
      auth.clear()
      return '/login'
    }
  }
  const permission = to.meta.permission as string | undefined
  if (permission && !auth.can(permission)) return '/dashboard'
  return true
})











