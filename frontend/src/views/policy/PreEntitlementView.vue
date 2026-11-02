<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="采购单选择返利政策后自动同步预享受项，金额仅作预估。实销后按实销日期生成正式权益并申请兑现。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="orderKeyword" placeholder="订单编号" allow-clear style="width:260px" />
      </a-space>
      <a-table v-resizable-columns :data-source="filteredRows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1260 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'serialNos'">{{ record.serialNos || '-' }}</template>
          <template v-else-if="column.key === 'orderNo'">
            <a @click="openOrderDetail(record)">{{ record.orderNo || '-' }}</a>
          </template>
          <template v-else-if="column.key === 'amount'">{{ money(record.estimateAmount) }}</template>
          <template v-else-if="column.key === 'invoiceIssuedAt'">{{ formatDateTime(record.invoiceIssuedAt) }}</template>
          <template v-else-if="column.key === 'retailDate'">{{ record.retailDate || '-' }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ PRE_STATUS_LABELS[record.status] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button v-if="canManage && record.status !== 'SETTLED' && record.status !== 'CANCELLED'"
                      size="small" danger @click="cancel(record)">取消</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="orderDetailOpen" title="采购订单详情" footer="" width="1100px">
      <a-spin :spinning="orderLoading">
        <template v-if="orderDetail">
          <a-descriptions bordered size="small" :column="3" style="margin-bottom:12px">
            <a-descriptions-item label="订单编号">{{ orderDetail.externalOrderNo || orderDetail.orderNo }}</a-descriptions-item>
            <a-descriptions-item label="供应商">{{ orderDetail.supplierName }}</a-descriptions-item>
            <a-descriptions-item label="状态">{{ PURCHASE_STATUS_LABELS[orderDetail.status] }}</a-descriptions-item>
            <a-descriptions-item label="预计到货">{{ orderDetail.expectedDate || '-' }}</a-descriptions-item>
            <a-descriptions-item label="总金额">{{ money(orderDetail.totalAmount) }}</a-descriptions-item>
            <a-descriptions-item label="备注">{{ orderDetail.remark || '-' }}</a-descriptions-item>
          </a-descriptions>
          <a-table :data-source="orderDetail.lines" row-key="id" size="small" :pagination="false" :scroll="{ x: 1100 }">
            <a-table-column title="车系" :width="140"><template #default="{ record }">{{ record.series || '-' }}</template></a-table-column>
            <a-table-column title="商品名称" :width="190"><template #default="{ record }">{{ record.productName }}</template></a-table-column>
            <a-table-column title="配置" :width="160"><template #default="{ record }">{{ record.configText || record.configCode || '-' }}</template></a-table-column>
            <a-table-column title="数量" data-index="quantity" :width="80" />
            <a-table-column title="单价" :width="110"><template #default="{ record }">{{ money(record.unitPrice) }}</template></a-table-column>
            <a-table-column title="总计车款" :width="120"><template #default="{ record }">{{ money(record.amount) }}</template></a-table-column>
            <a-table-column title="返利政策" :width="220"><template #default="{ record }"><a-tag v-for="policy in record.policies" :key="policy.id" color="blue">{{ policy.name }}</a-tag></template></a-table-column>
            <a-table-column title="收货仓库" :width="140"><template #default="{ record }">{{ record.warehouseName || orderDetail?.warehouseName || '-' }}</template></a-table-column>
          </a-table>
        </template>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { purchaseApi } from '../../api/procurement'
import { useAuthStore } from '../../stores/auth'
import { PRE_STATUS_LABELS } from '../../types/policy'
import type { PreEntitlement } from '../../types/policy'
import type { PurchaseOrder } from '../../types/procurement'
import { PURCHASE_STATUS_LABELS } from '../../types/procurement'

const auth = useAuthStore()
const canManage = auth.can('IVS_PRE_ENTITLEMENT_MANAGE')
const rows = ref<PreEntitlement[]>([])
const orderKeyword = ref('')
const orderDetailOpen = ref(false)
const orderLoading = ref(false)
const orderDetail = ref<PurchaseOrder>()

const filteredRows = computed(() => {
  const keyword = orderKeyword.value.trim().toLowerCase()
  if (!keyword) return rows.value
  return rows.value.filter((row) => (row.orderNo || '').toLowerCase().includes(keyword))
})

const columns = [
  { title: 'VIN/SN', key: 'serialNos', width: 220 },
  { title: '订单编号', key: 'orderNo', width: 160 },
  { title: '政策', key: 'policyName', dataIndex: 'policyName', width: 240 },
  { title: '预计金额', key: 'amount', width: 140 },
  { title: '状态', key: 'status', width: 110 },
  { title: '终端开票日期', key: 'invoiceIssuedAt', width: 130 },
  { title: '实销时间', key: 'retailDate', width: 120 },
  { title: '操作', key: 'actions', width: 100 },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDateTime(value?: string) { return value ? new Date(value).toLocaleDateString('sv-SE') : '-' }
function statusColor(status: string) {
  if (status === 'READY') return 'blue'
  if (status === 'APPLIED') return 'orange'
  if (status === 'SETTLED') return 'green'
  if (status === 'CANCELLED') return 'red'
  return 'default'
}

async function cancel(record: PreEntitlement) {
  try { await policyApi.cancelPreEntitlement(record.id); message.success('预享受项已取消并释放额度'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try { rows.value = await policyApi.preEntitlements() }
  catch (error) { message.error((error as Error).message) }
}

async function openOrderDetail(record: PreEntitlement) {
  if (!record.orderId) return
  orderDetailOpen.value = true
  orderLoading.value = true
  orderDetail.value = undefined
  try { orderDetail.value = await purchaseApi.get(record.orderId) }
  catch (error) { message.error((error as Error).message); orderDetailOpen.value = false }
  finally { orderLoading.value = false }
}

onMounted(async () => {
  await reload()
})
</script>

<style scoped>
.sub { margin-top: 2px; color: #8b96a8; font-size: 12px; }
</style>
