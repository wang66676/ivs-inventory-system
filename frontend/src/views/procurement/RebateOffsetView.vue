<template>
  <div class="rebate-offset-page">
    <a-card :bordered="false" class="hero-card">
      <div class="hero-row">
        <div>
          <a-tag color="blue">采购收货联动</a-tag>
          <div class="hero-title">返利抵扣工作台</div>
          <div class="hero-note">
            抵扣由采购收货发起；本页负责追溯返利来源、查看关联订单，并在取消时同步恢复采购应付。
          </div>
        </div>
        <a-space wrap>
          <a-button @click="router.push('/policy/fund-pool')">查看资金池</a-button>
          <a-button type="primary" @click="router.push('/procurement/receipts')">去采购收货发起抵扣</a-button>
        </a-space>
      </div>
    </a-card>

    <a-row :gutter="16" class="stats-row">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false"><a-statistic title="有效抵扣金额" :value="activeAmount" :precision="2" prefix="¥" /></a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false"><a-statistic title="已取消金额" :value="cancelledAmount" :precision="2" prefix="¥" /></a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false"><a-statistic title="有效抵扣单" :value="activeCount" suffix="张" /></a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false"><a-statistic title="覆盖厂家" :value="supplierCount" suffix="家" /></a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false">
      <div class="filter-row">
        <a-input-search v-model:value="keyword" allow-clear placeholder="抵扣单号 / 订单编号 / 收货单号"
                        style="width: 280px" />
        <a-select v-model:value="supplierId" :options="supplierOptions" allow-clear show-search
                  placeholder="按厂家筛选" style="width: 230px" />
        <a-select v-model:value="status" :options="statusOptions" allow-clear
                  placeholder="按状态筛选" style="width: 150px" />
        <a-range-picker v-model:value="dateRange" value-format="YYYY-MM-DD" :placeholder="['开始日期', '结束日期']"
                        style="width: 260px" />
        <a-button @click="resetFilters">重置</a-button>
        <a-button :loading="loading" @click="reload">刷新</a-button>
        <a-button @click="exportRows">导出</a-button>
      </div>

      <a-alert type="info" show-icon class="policy-note"
               message="取消抵扣会同时回补返利余额、恢复采购收货抵扣额和应付金额；已付款或已匹配进项发票的抵扣不可直接取消。" />

      <a-table v-resizable-columns :data-source="filteredRows" :columns="columns" row-key="id" size="middle"
               :loading="loading" :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1420 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'offsetNo'">
            <a-button type="link" size="small" class="link-button" @click="openDetail(record)">
              {{ record.offsetNo }}
            </a-button>
          </template>
          <template v-else-if="column.key === 'supplierName'">{{ record.supplierName }}</template>
          <template v-else-if="column.key === 'orderNo'">
            <a-button v-if="record.orderId" type="link" size="small" class="link-button" @click="openOrder(record)">
              {{ record.orderNo || record.orderId }}
            </a-button>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'receiptNo'">
            <a-button type="link" size="small" class="link-button" @click="openDetail(record)">
              {{ record.receiptNo || '-' }}
            </a-button>
          </template>
          <template v-else-if="column.key === 'appliedAmount'">{{ money(record.appliedAmount) }}</template>
          <template v-else-if="column.key === 'balanceAfter'">{{ money(record.balanceAfter) }}</template>
          <template v-else-if="column.key === 'sourceCount'">{{ record.lines.length }} 笔</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">
              {{ record.status === 'ACTIVE' ? '有效' : '已取消' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space size="small">
              <a-button size="small" @click="openDetail(record)">详情</a-button>
              <a-button v-if="canOffset && record.status === 'ACTIVE'" danger size="small"
                        @click="askCancel(record)">取消抵扣</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="detailOpen" title="返利抵扣单详情" width="820" placement="right">
      <template #extra>
        <a-button v-if="canOffset && detailOffset?.status === 'ACTIVE'" danger
                  @click="detailOffset && askCancel(detailOffset)">取消抵扣</a-button>
      </template>
      <a-descriptions bordered :column="2" size="small" class="detail-descriptions">
        <a-descriptions-item label="抵扣单号">{{ detailOffset?.offsetNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="detailOffset?.status === 'ACTIVE' ? 'green' : 'default'">
            {{ detailOffset?.status === 'ACTIVE' ? '有效' : '已取消' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="厂家">{{ detailOffset?.supplierName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="抵扣时间">{{ formatTime(detailOffset?.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="采购订单">
          <a-button v-if="detailOffset?.orderId" type="link" size="small" class="link-button"
                    @click="openOrder(detailOffset)">{{ detailOffset?.orderNo || detailOffset?.orderId }}</a-button>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="收货单号">{{ detailOffset?.receiptNo || '-' }}</a-descriptions-item>
        <a-descriptions-item label="抵扣金额">{{ money(detailOffset?.appliedAmount) }}</a-descriptions-item>
        <a-descriptions-item label="抵扣后厂家余额">{{ money(detailOffset?.balanceAfter) }}</a-descriptions-item>
      </a-descriptions>

      <div class="section-title">返利来源追溯</div>
      <a-table :data-source="detailOffset?.lines || []" row-key="fundTxnId" size="small" :pagination="false">
        <a-table-column title="返利来源单号" key="sourceNo" :width="220">
          <template #default="{ record }">{{ record.sourceNo || record.fundTxnId }}</template>
        </a-table-column>
        <a-table-column title="来源类型" key="sourceType" :width="180">
          <template #default="{ record }">{{ sourceTypeLabel(record.sourceType) }}</template>
        </a-table-column>
        <a-table-column title="本次抵扣金额" key="amount" :width="160" align="right">
          <template #default="{ record }">{{ money(record.amount) }}</template>
        </a-table-column>
      </a-table>
      <a-alert class="drawer-alert" type="warning" show-icon
               message="采购收货抵扣取消后，采购应付会恢复为抵扣前金额；若该应付已付款或已匹配进项发票，系统将阻止取消。" />
    </a-drawer>

    <a-modal v-model:open="orderOpen" title="采购订单明细" :footer="null" width="1100">
      <a-descriptions v-if="orderDetail" bordered :column="3" size="small" class="detail-descriptions">
        <a-descriptions-item label="订单编号">{{ orderDetail.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="供应商">{{ orderDetail.supplierName }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ PURCHASE_STATUS_LABELS[orderDetail.status] }}</a-descriptions-item>
        <a-descriptions-item label="订单金额">{{ money(orderDetail.totalAmount) }}</a-descriptions-item>
        <a-descriptions-item label="预计到货">{{ orderDetail.expectedDate || '-' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ formatTime(orderDetail.createdAt) }}</a-descriptions-item>
      </a-descriptions>
      <a-table :data-source="orderDetail?.lines || []" row-key="id" size="small" :pagination="false"
               :scroll="{ x: 900 }">
        <a-table-column title="商品" key="productName" :width="260">
          <template #default="{ record }">{{ record.series || record.productName }}</template>
        </a-table-column>
        <a-table-column title="配置" data-index="configText" :width="180" />
        <a-table-column title="数量" data-index="quantity" :width="100" align="right" />
        <a-table-column title="已收" data-index="receivedQuantity" :width="100" align="right" />
        <a-table-column title="单价" key="unitPrice" :width="130" align="right">
          <template #default="{ record }">{{ money(record.unitPrice) }}</template>
        </a-table-column>
        <a-table-column title="金额" key="amount" :width="140" align="right">
          <template #default="{ record }">{{ money(record.amount) }}</template>
        </a-table-column>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { purchaseApi } from '../../api/procurement'
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { FundOffset } from '../../types/policy'
import type { PurchaseOrder } from '../../types/procurement'
import { PURCHASE_STATUS_LABELS } from '../../types/procurement'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'

interface OffsetRow extends FundOffset {
  supplierName: string
  orderId?: string
  orderNo?: string
}

const router = useRouter()
const auth = useAuthStore()
const canOffset = auth.can('IVS_FUND_OFFSET')
const loading = ref(false)
const offsets = ref<FundOffset[]>([])
const supplierNames = ref<Record<string, string>>({})
const supplierOptions = ref<{ label: string; value: string }[]>([])
const purchaseOrders = ref<Record<string, PurchaseOrder>>({})
const keyword = ref('')
const supplierId = ref<string>()
const status = ref<string>()
const dateRange = ref<[string, string]>()
const detailOpen = ref(false)
const detailOffset = ref<OffsetRow>()
const orderOpen = ref(false)
const orderDetail = ref<PurchaseOrder>()

const statusOptions = [
  { label: '有效', value: 'ACTIVE' },
  { label: '已取消', value: 'CANCELLED' },
]

const columns = [
  { title: '抵扣单号', key: 'offsetNo', width: 150 },
  { title: '厂家', key: 'supplierName', width: 210 },
  { title: '采购订单编号', key: 'orderNo', width: 170 },
  { title: '收货单号', key: 'receiptNo', width: 150 },
  { title: '抵扣金额', key: 'appliedAmount', width: 140, align: 'right' },
  { title: '抵扣后余额', key: 'balanceAfter', width: 140, align: 'right' },
  { title: '返利来源', key: 'sourceCount', width: 110, align: 'center' },
  { title: '状态', key: 'status', width: 100, align: 'center' },
  { title: '抵扣时间', key: 'createdAt', width: 175 },
  { title: '操作', key: 'actions', width: 165, fixed: 'right' },
]

const rows = computed<OffsetRow[]>(() => offsets.value.map((offset) => {
  const order = offset.purchaseOrderId ? purchaseOrders.value[offset.purchaseOrderId] : undefined
  return {
    ...offset,
    supplierName: supplierNames.value[offset.supplierId] || offset.supplierId,
    orderId: offset.purchaseOrderId,
    orderNo: order?.externalOrderNo || order?.orderNo,
  }
}))

const filteredRows = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (supplierId.value && row.supplierId !== supplierId.value) return false
    if (status.value && row.status !== status.value) return false
    if (dateRange.value?.length === 2) {
      const date = row.createdAt.slice(0, 10)
      if (date < dateRange.value[0] || date > dateRange.value[1]) return false
    }
    if (!text) return true
    return [row.offsetNo, row.receiptNo, row.orderNo, row.orderId, row.supplierName]
      .some((value) => (value || '').toLowerCase().includes(text))
  })
})

const activeAmount = computed(() => filteredRows.value
  .filter((row) => row.status === 'ACTIVE')
  .reduce((sum, row) => sum + Number(row.appliedAmount || 0), 0))
const cancelledAmount = computed(() => filteredRows.value
  .filter((row) => row.status === 'CANCELLED')
  .reduce((sum, row) => sum + Number(row.appliedAmount || 0), 0))
const activeCount = computed(() => filteredRows.value.filter((row) => row.status === 'ACTIVE').length)
const supplierCount = computed(() => new Set(filteredRows.value.map((row) => row.supplierId)).size)

function money(value?: number) {
  if (value == null) return '-'
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
function sourceTypeLabel(value?: string) {
  const labels: Record<string, string> = {
    RED_INVOICE: '厂家红字发票',
    OEM_REBATE: '厂家返利',
    ENTITLEMENT_SETTLEMENT: '权益结算',
    ADJUST_IN: '人工调整',
    OPENING: '期初余额',
    OTHER: '其他',
  }
  return value ? labels[value] || value : '-'
}
function resetFilters() {
  keyword.value = ''
  supplierId.value = undefined
  status.value = undefined
  dateRange.value = undefined
}
function openDetail(record: OffsetRow) {
  detailOffset.value = record
  detailOpen.value = true
}
async function openOrder(record: OffsetRow) {
  if (!record.orderId) return
  orderDetail.value = purchaseOrders.value[record.orderId]
  if (!orderDetail.value) {
    try {
      orderDetail.value = await purchaseApi.get(record.orderId)
    } catch (error) {
      message.error((error as Error).message)
      return
    }
  }
  orderOpen.value = true
}
function askCancel(record: OffsetRow) {
  Modal.confirm({
    title: `确认取消抵扣 ${record.offsetNo}？`,
    content: `将回补厂家返利余额 ${money(record.appliedAmount)}，并把关联采购收货与应付恢复为抵扣前金额。已付款或已匹配进项发票时系统会拒绝取消。`,
    okText: '确认取消',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await policyApi.cancelFundOffset(record.id)
        message.success('抵扣已取消，资金池、采购收货和应付已同步恢复')
        detailOpen.value = false
        await reload()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}
function exportRows() {
  if (!filteredRows.value.length) {
    message.warning('当前没有可导出的返利抵扣数据')
    return
  }
  exportCsv<OffsetRow>(`返利抵扣-${exportTimestamp()}`, [
    { title: '抵扣单号', value: (row) => row.offsetNo },
    { title: '厂家', value: (row) => row.supplierName },
    { title: '采购订单编号', value: (row) => row.orderNo },
    { title: '收货单号', value: (row) => row.receiptNo },
    { title: '抵扣金额', value: (row) => row.appliedAmount },
    { title: '抵扣后余额', value: (row) => row.balanceAfter },
    { title: '返利来源数', value: (row) => row.lines.length },
    { title: '状态', value: (row) => row.status === 'ACTIVE' ? '有效' : '已取消' },
    { title: '抵扣时间', value: (row) => formatTime(row.createdAt) },
  ], filteredRows.value)
  message.success('返利抵扣数据已导出')
}
async function reload() {
  loading.value = true
  try {
    offsets.value = await policyApi.fundOffsets({ supplierId: supplierId.value })
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  const [supplierResult, orderResult] = await Promise.allSettled([supplierApi.list(), purchaseApi.list()])
  if (supplierResult.status === 'fulfilled') {
    supplierNames.value = Object.fromEntries(supplierResult.value.map((item) => [item.id, item.name]))
    supplierOptions.value = supplierResult.value.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  }
  if (orderResult.status === 'fulfilled') {
    purchaseOrders.value = Object.fromEntries(orderResult.value.map((item) => [item.id, item]))
  }
  await reload()
})
</script>

<style scoped>
.rebate-offset-page { display: flex; flex-direction: column; gap: 16px; }
.hero-card :deep(.ant-card-body) { padding: 22px 24px; }
.hero-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.hero-title { margin-top: 10px; color: #172033; font-size: 22px; font-weight: 700; }
.hero-note { margin-top: 6px; color: #697386; line-height: 1.6; }
.stats-row :deep(.ant-card) { height: 100%; }
.filter-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 14px; }
.policy-note { margin-bottom: 14px; }
.link-button { padding-inline: 0; }
.detail-descriptions { margin-bottom: 18px; }
.section-title { margin: 4px 0 10px; color: #172033; font-size: 15px; font-weight: 600; }
.drawer-alert { margin-top: 16px; }
@media (max-width: 900px) {
  .hero-row { align-items: flex-start; flex-direction: column; }
}
</style>
