<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="权益条数" :value="rows.length" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="权益面值" :value="faceTotal" :precision="2" prefix="¥" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="已确认" :value="confirmedTotal" :precision="2" prefix="¥" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="已兑现" :value="settledTotal" :precision="2" prefix="¥" /></a-card></a-col>
    </a-row>

    <a-card :bordered="false" style="margin-top:16px">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="实销确认后生成正式权益。勾选「待申请」权益可批量发起兑现，按厂家+政策+周期归集到申报单，也可合并同厂家同周期政策。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canApply" type="primary" :disabled="!selectedRowKeys.length" @click="openApply">
          申请兑现（已选 {{ selectedRowKeys.length }}）
        </a-button>
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="periodCode" placeholder="期间，如 2026-10" allow-clear style="width:180px" @press-enter="reload" />
        <a-select v-model:value="status" :options="statusOptions" allow-clear placeholder="状态" style="width:170px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="{ pageSize: 15 }"
               :row-selection="rowSelection" :scroll="{ x: 1300 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'amounts'">
            <div>面值 {{ money(record.faceAmount) }}</div>
            <div class="sub">确认 {{ money(record.confirmedAmount) }} · 兑现 {{ money(record.settledAmount) }}</div>
          </template>
          <template v-else-if="column.key === 'purchaseOrderNo'">
            <a v-if="record.purchaseOrderId" @click="openOrderDetail(record)">{{ record.purchaseOrderNo || '-' }}</a>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'invoiceIssuedAt'">{{ formatDate(record.invoiceIssuedAt) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ ENTITLEMENT_STATUS_LABELS[record.status] || record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button v-if="canApply && record.status === 'PENDING_CLAIM'" size="small" @click="redeem(record)">发起兑现</a-button>
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
            <a-descriptions-item label="金额">{{ money(orderDetail.totalAmount) }}</a-descriptions-item>
          </a-descriptions>
          <a-table :data-source="orderDetail.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="商品" data-index="productName" />
            <a-table-column title="车系" data-index="series" />
            <a-table-column title="配置" data-index="configText" />
            <a-table-column title="数量" data-index="quantity" :width="90" />
            <a-table-column title="单价" :width="120"><template #default="{ record }">{{ money(record.unitPrice) }}</template></a-table-column>
            <a-table-column title="返利政策"><template #default="{ record }"><a-tag v-for="policy in record.policies" :key="policy.id">{{ policy.name }}</a-tag></template></a-table-column>
          </a-table>
        </template>
      </a-spin>
    </a-modal>

    <a-modal v-model:open="applyOpen" title="实销后申请兑现" :confirm-loading="saving" ok-text="归集到申报单"
             cancel-text="取消" width="560px" @ok="submitApply">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="未合并时按「厂家 + 政策 + 周期」生成多张申报单；合并时按「厂家 + 周期」生成一张，明细保留政策维度。" />
      <a-form layout="vertical">
        <a-form-item label="合并同厂家同周期政策"><a-switch v-model:checked="merge" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="remark" :rows="2" /></a-form-item>
        <a-descriptions :column="1" size="small" bordered>
          <a-descriptions-item label="已选权益">{{ selectedRowKeys.length }} 条</a-descriptions-item>
          <a-descriptions-item label="权益面值合计">{{ money(selectedFaceTotal) }}</a-descriptions-item>
        </a-descriptions>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { purchaseApi } from '../../api/procurement'
import { useAuthStore } from '../../stores/auth'
import { ENTITLEMENT_STATUS_LABELS } from '../../types/policy'
import type { Entitlement } from '../../types/policy'
import type { PurchaseOrder } from '../../types/procurement'

const auth = useAuthStore()
const canApply = auth.can('IVS_ENTITLEMENT_APPLY')
const rows = ref<Entitlement[]>([])
const selectedRowKeys = ref<string[]>([])
const periodCode = ref('')
const status = ref<string | undefined>()
const applyOpen = ref(false)
const saving = ref(false)
const merge = ref(false)
const remark = ref('')
const orderDetailOpen = ref(false)
const orderLoading = ref(false)
const orderDetail = ref<PurchaseOrder>()

const statusOptions = Object.entries(ENTITLEMENT_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const columns = [
  { title: '权益编号', key: 'entitlementNo', dataIndex: 'entitlementNo', width: 140 },
  { title: '采购订单编号', key: 'purchaseOrderNo', width: 160 },
  { title: '政策名目', key: 'policyName', dataIndex: 'policyName', width: 220 },
  { title: '终端发票时间', key: 'invoiceIssuedAt', width: 140 },
  { title: '折让金额', key: 'amounts', width: 220 },
  { title: '状态', key: 'status', width: 120 },
  { title: '创建时间', key: 'createdAt', width: 170, customRender: ({ record }: any) => formatTime(record.createdAt) },
  { title: '操作', key: 'actions', width: 110 },
]

function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }

async function openOrderDetail(record: Entitlement) {
  if (!record.purchaseOrderId) return
  orderDetailOpen.value = true
  orderLoading.value = true
  orderDetail.value = undefined
  try { orderDetail.value = await purchaseApi.get(record.purchaseOrderId) }
  catch (error) { message.error((error as Error).message); orderDetailOpen.value = false }
  finally { orderLoading.value = false }
}

const faceTotal = computed(() => rows.value.reduce((sum, row) => sum + Number(row.faceAmount), 0))
const confirmedTotal = computed(() => rows.value.reduce((sum, row) => sum + Number(row.confirmedAmount), 0))
const settledTotal = computed(() => rows.value.reduce((sum, row) => sum + Number(row.settledAmount), 0))
const selectedFaceTotal = computed(() => rows.value.filter((row) => selectedRowKeys.value.includes(row.id))
  .reduce((sum, row) => sum + Number(row.faceAmount), 0))

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => { selectedRowKeys.value = keys.map(String) },
  getCheckboxProps: (record: Entitlement) => ({ disabled: record.status !== 'PENDING_CLAIM' }),
}))

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function statusColor(statusValue: string) {
  if (statusValue === 'SETTLED' || statusValue === 'CONFIRMED') return 'green'
  if (statusValue === 'DISPUTED' || statusValue === 'DIFFERENCE') return 'red'
  if (statusValue === 'CLAIMED' || statusValue === 'SUBMITTED') return 'orange'
  return 'blue'
}

function openApply() {
  if (!selectedRowKeys.value.length) { message.warning('请先勾选待申请权益'); return }
  merge.value = false
  remark.value = ''
  applyOpen.value = true
}

async function submitApply() {
  saving.value = true
  try {
    const claims = await policyApi.applyClaims({ entitlementIds: selectedRowKeys.value, merge: merge.value, remark: remark.value || undefined })
    message.success(`已生成 ${claims.length} 张申报草稿`)
    applyOpen.value = false
    selectedRowKeys.value = []
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function redeem(record: Entitlement) {
  try {
    const claims = await policyApi.redeemEntitlement(record.id)
    message.success(`已归集到申报单 ${claims[0]?.claimNo || ''}`)
    await reload()
  } catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try {
    rows.value = await policyApi.entitlements({
      periodCode: periodCode.value || undefined, status: status.value,
    })
    selectedRowKeys.value = selectedRowKeys.value.filter((id) => rows.value.some((row) => row.id === id))
  } catch (error) { message.error((error as Error).message) }
}

onMounted(reload)
</script>

<style scoped>
.sub { margin-top: 2px; color: #8b96a8; font-size: 12px; }
</style>
