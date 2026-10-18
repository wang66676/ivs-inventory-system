<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="预享受项是订单选政策的结果，金额仅作预估。实销后生成正式权益；订单之后新增政策可通过「后加补挂」留痕加入。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canSelect" type="primary" @click="openCreate">订单选政策</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="orderId" placeholder="订单 ID" allow-clear style="width:300px" @press-enter="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="{ pageSize: 15 }"
               :scroll="{ x: 1250 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'amount'">{{ money(record.estimateAmount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ PRE_STATUS_LABELS[record.status] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'added'">
            <a-tag v-if="record.addedAfterOrder" color="orange">后加补挂</a-tag>
            <span v-else>订单时选择</span>
            <div v-if="record.addReason" class="sub">{{ record.addReason }}</div>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-button v-if="canManage && record.status !== 'SETTLED' && record.status !== 'CANCELLED'"
                      size="small" danger @click="cancel(record)">取消</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="订单选择政策" :confirm-loading="saving" ok-text="生成预享受项"
             cancel-text="取消" width="760px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="订单 ID" required><a-input v-model:value="form.orderId" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="订单行 ID"><a-input v-model:value="form.orderLineId" placeholder="同订单行政策防重" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="商品" required>
              <a-select v-model:value="form.productId" :options="productOptions" show-search />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="序列号（可选）">
              <a-select v-model:value="form.serialId" :options="serialOptions" show-search allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="订单行金额" required>
              <a-input-number v-model:value="form.orderLineAmount" :min="0" :precision="2" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="是否后加政策">
              <a-switch v-model:checked="form.addedAfterOrder" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="选择政策（可多选，金额累加）" required>
              <a-select v-model:value="form.policyIds" mode="multiple" :options="policyOptions" show-search allow-clear />
            </a-form-item>
          </a-col>
          <a-col v-if="form.addedAfterOrder" :span="24">
            <a-form-item label="补挂原因" required><a-textarea v-model:value="form.reason" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { productApi, serialApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { PRE_STATUS_LABELS } from '../../types/policy'
import type { PreEntitlement } from '../../types/policy'

const auth = useAuthStore()
const canSelect = auth.can('IVS_POLICY_SELECT')
const canManage = auth.can('IVS_PRE_ENTITLEMENT_MANAGE')
const rows = ref<PreEntitlement[]>([])
const orderId = ref('')
const formOpen = ref(false)
const saving = ref(false)
const policyOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string }[]>([])
const serialOptions = ref<{ label: string; value: string }[]>([])
const form = reactive<Record<string, any>>({})

const columns = [
  { title: '预享受编号', key: 'preNo', dataIndex: 'preNo', width: 140 },
  { title: '订单 ID', key: 'orderId', dataIndex: 'orderId', width: 260 },
  { title: '政策', key: 'policyName', dataIndex: 'policyName', width: 240 },
  { title: '预计金额', key: 'amount', width: 140 },
  { title: '状态', key: 'status', width: 110 },
  { title: '来源', key: 'added', width: 220 },
  { title: '创建时间', key: 'createdAt', width: 170, customRender: ({ record }: any) => formatTime(record.createdAt) },
  { title: '操作', key: 'actions', width: 100 },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function statusColor(status: string) {
  if (status === 'READY') return 'blue'
  if (status === 'APPLIED') return 'orange'
  if (status === 'SETTLED') return 'green'
  if (status === 'CANCELLED') return 'red'
  return 'default'
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { addedAfterOrder: false, orderLineAmount: 0 })
  formOpen.value = true
}

async function save() {
  if (!form.orderId || !form.productId || !form.policyIds?.length) {
    message.warning('请填写订单、商品并至少选择一条政策'); return
  }
  if (form.addedAfterOrder && !form.reason?.trim()) { message.warning('后加政策必须填写补挂原因'); return }
  saving.value = true
  try {
    const created = await policyApi.applyPreEntitlement({
      orderId: form.orderId,
      orderLineId: form.orderLineId || null,
      productId: form.productId,
      serialId: form.serialId || null,
      orderLineAmount: form.orderLineAmount,
      policyIds: form.policyIds,
      addedAfterOrder: !!form.addedAfterOrder,
      reason: form.reason || null,
    })
    message.success(`已生成 ${created.length} 条预享受项，合计 ${money(created.reduce((sum, row) => sum + Number(row.estimateAmount), 0))}`)
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function cancel(record: PreEntitlement) {
  try { await policyApi.cancelPreEntitlement(record.id); message.success('预享受项已取消并释放额度'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try { rows.value = await policyApi.preEntitlements({ orderId: orderId.value || undefined }) }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [policies, products, serials] = await Promise.all([
      policyApi.list({ status: 'PUBLISHED' }), productApi.list(), serialApi.list({ status: 'IN_STOCK' }),
    ])
    policyOptions.value = policies.map((item) => ({ label: `${item.name}（${item.policyNo}）`, value: item.id }))
    productOptions.value = products.map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
    serialOptions.value = serials.map((item) => ({ label: item.serialNo, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.sub { margin-top: 2px; color: #8b96a8; font-size: 12px; }
</style>
