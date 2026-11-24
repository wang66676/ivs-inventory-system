<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="采购单选择返利政策后自动同步预享受项；当前是否可申请，按政策名目配置的参考时间判定。" />
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
          <template v-else-if="column.key === 'reference'">
            <div>{{ referenceLabel(record.referenceDateType) }}</div>
            <div class="sub">{{ record.referenceDate || '-' }}</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="canManage && record.status !== 'APPLIED' && record.status !== 'SETTLED' && record.status !== 'CANCELLED'" size="small">
              <a-button size="small" @click="openPolicyManager(record)">变更政策</a-button>
              <a-button size="small" danger @click="removePolicy(record)">{{ auth.isAdmin ? '删除' : '申请删除' }}</a-button>
            </a-space>
            <span v-else>-</span>
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

    <a-modal v-model:open="policyOpen" title="管理 VIN/SN 返利政策" :confirm-loading="policySaving"
             ok-text="保存政策" cancel-text="取消" width="680px" @ok="savePolicies">
      <a-descriptions bordered :column="2" size="small" style="margin-bottom:16px">
        <a-descriptions-item label="VIN/SN">{{ policyCurrent?.serialNos || '-' }}</a-descriptions-item>
        <a-descriptions-item label="订单编号">{{ policyCurrent?.orderNo || '-' }}</a-descriptions-item>
      </a-descriptions>
      <a-alert type="info" show-icon style="margin-bottom:14px"
               message="勾选该 VIN/SN 应享受的返利政策。取消勾选并保存即删除对应政策；选择新政策即追加或替换，系统会自动释放和重新预占额度。" />
      <a-form layout="vertical">
        <a-form-item label="返利政策">
          <a-select v-model:value="policyIds" mode="multiple" :options="policyOptions" show-search
                    allow-clear option-filter-prop="label" placeholder="选择该 VIN/SN 适用的政策" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { deletionApi } from '../../api/deletion'
import { purchaseApi } from '../../api/procurement'
import { useAuthStore } from '../../stores/auth'
import { PRE_STATUS_LABELS } from '../../types/policy'
import type { Policy, PreEntitlement } from '../../types/policy'
import type { PurchaseOrder } from '../../types/procurement'
import { PURCHASE_STATUS_LABELS } from '../../types/procurement'

const auth = useAuthStore()
const canManage = auth.can('IVS_PRE_ENTITLEMENT_MANAGE')
const rows = ref<PreEntitlement[]>([])
const orderKeyword = ref('')
const orderDetailOpen = ref(false)
const orderLoading = ref(false)
const orderDetail = ref<PurchaseOrder>()
const policies = ref<Policy[]>([])
const policyOpen = ref(false)
const policySaving = ref(false)
const policyCurrent = ref<PreEntitlement>()
const policyIds = ref<string[]>([])
const policyOptions = computed(() => policies.value
  .filter((policy) => !policyCurrent.value?.supplierId || policy.supplierId === policyCurrent.value.supplierId)
  .map((policy) => ({ label: `${policy.name}（${policy.policyNo}）`, value: policy.id })))

const filteredRows = computed(() => {
  const keyword = orderKeyword.value.trim().toLowerCase()
  if (!keyword) return rows.value
  return rows.value.filter((row) => (row.orderNo || '').toLowerCase().includes(keyword))
})

const columns = [
  { title: 'VIN/SN', key: 'serialNos', width: 220 },
  { title: '订单编号', key: 'orderNo', width: 160 },
  { title: '政策', key: 'policyName', dataIndex: 'policyName', width: 240 },
  { title: '参考时间', key: 'reference', width: 170 },
  { title: '预计金额', key: 'amount', width: 140 },
  { title: '状态', key: 'status', width: 110 },
  { title: '终端开票日期', key: 'invoiceIssuedAt', width: 130 },
  { title: '实销时间', key: 'retailDate', width: 120 },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatDateTime(value?: string) { return value ? new Date(value).toLocaleDateString('sv-SE') : '-' }
function referenceLabel(type?: string) {
  if (type === 'ORDER_DATE') return '采购订单时间'
  if (type === 'INVOICE_DATE') return '终端开票时间'
  if (type === 'DELIVERY_DATE') return '交付日期'
  if (type === 'REGISTER_DATE') return '上牌日期'
  return '实销日期'
}
function statusLabel(record: PreEntitlement) {
  if (record.status !== 'PENDING_RETAIL') return PRE_STATUS_LABELS[record.status]
  if (record.referenceDateType === 'ORDER_DATE') return '待采购订单时间'
  if (record.referenceDateType === 'INVOICE_DATE') return '待终端开票'
  if (record.referenceDateType === 'DELIVERY_DATE') return '待交付'
  if (record.referenceDateType === 'REGISTER_DATE') return '待上牌'
  return '待实销'
}
function statusColor(status: string) {
  if (status === 'READY') return 'blue'
  if (status === 'APPLIED') return 'orange'
  if (status === 'SETTLED') return 'green'
  if (status === 'CANCELLED') return 'red'
  return 'default'
}

function openPolicyManager(record: PreEntitlement) {
  if (!record.orderId || !record.orderLineId || !record.serialId || !record.productId) {
    message.warning('该政策记录缺少订单或 VIN/SN 信息，无法变更')
    return
  }
  policyCurrent.value = record
  policyIds.value = rows.value
    .filter((row) => row.orderId === record.orderId && row.orderLineId === record.orderLineId
      && row.serialId === record.serialId && row.status !== 'CANCELLED')
    .map((row) => row.policyId)
  policyOpen.value = true
}

async function savePolicies() {
  const record = policyCurrent.value
  if (!record?.orderId || !record.orderLineId || !record.serialId || !record.productId) return
  policySaving.value = true
  try {
    await policyApi.updateSerialPolicies({
      orderId: record.orderId,
      orderLineId: record.orderLineId,
      serialId: record.serialId,
      productId: record.productId,
      policyIds: policyIds.value,
    })
    message.success(policyIds.value.length ? '该 VIN/SN 的返利政策已更新' : '该 VIN/SN 的返利政策已删除')
    policyOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { policySaving.value = false }
}

function removePolicy(record: PreEntitlement) {
  const direct = auth.isAdmin
  Modal.confirm({
    title: direct ? `删除该 VIN/SN 的政策「${record.policyName}」？` : `申请删除该 VIN/SN 的政策「${record.policyName}」？`,
    content: direct
      ? '删除后会释放对应的政策额度；已申请或已兑现的政策不能删除。'
      : '提交后需管理员批准，批准后才会删除政策并释放对应额度。',
    okText: direct ? '确认删除' : '提交申请',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        if (direct) {
          await policyApi.deletePreEntitlement(record.id)
          message.success('政策已删除并释放额度')
        } else {
          await deletionApi.create({ businessType: 'PRE_ENTITLEMENT', businessId: record.id, reason: '预享受项删除申请' })
          message.success('删除申请已提交，等待管理员审批')
        }
        await reload()
      } catch (error) { message.error((error as Error).message) }
    },
  })
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
  try { policies.value = await policyApi.list({ status: 'PUBLISHED' }) }
  catch (error) { message.error((error as Error).message) }
  await reload()
})
</script>

<style scoped>
.sub { margin-top: 2px; color: #8b96a8; font-size: 12px; }
</style>
