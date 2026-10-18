<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="实销是政策返利的唯一时间基准。实销日期不得早于终端发票开票时间；复核确认后进入政策权益生成，冲正后允许重新登记。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">登记实销</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:160px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1320 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'vehicle'">
            <div>{{ record.productName || '-' }}</div>
            <div class="sub">{{ record.serialNo }}</div>
          </template>
          <template v-else-if="column.key === 'invoiceIssuedAt'">
            {{ formatTime(record.invoiceIssuedAt) }}
          </template>
          <template v-else-if="column.key === 'salePrice'">{{ money(record.salePrice) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button v-if="canWrite && record.status === 'DRAFT'" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button v-if="canWrite && record.status === 'DRAFT'" size="small" type="primary"
                        @click="submitRecord(record)">提交复核</a-button>
              <a-button v-if="canVerify && record.status === 'SUBMITTED'" size="small" type="primary"
                        @click="verifyRecord(record)">确认实销</a-button>
              <a-button v-if="canVerify && record.status === 'VERIFIED'" size="small"
                        @click="openGenerate(record)">生成权益</a-button>
              <a-button v-if="canVerify && record.status !== 'REVERSED'" size="small" danger
                        @click="openReverse(record)">冲正</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" :title="editingId ? '编辑实销记录' : '登记实销'" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="760px" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="序列号 VIN / 电池 SN" required>
              <a-select v-model:value="form.serialId" :options="serialOptions" show-search
                        :disabled="!!editingId" placeholder="选择在库序列号" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="终端客户">
              <a-select v-model:value="form.customerId" :options="customerOptions" allow-clear show-search placeholder="可选" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="实销日期" required><a-date-picker v-model:value="form.retailDate" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="终端发票开票时间" required>
              <a-date-picker v-model:value="form.invoiceIssuedAt" show-time style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="车辆使用人" required><a-input v-model:value="form.userName" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="手机号" required><a-input v-model:value="form.userMobile" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="车牌照号"><a-input v-model:value="form.plateNo" placeholder="选填" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="成交价"><a-input-number v-model:value="form.salePrice" :min="0" :precision="2" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="关联订单 ID"><a-input v-model:value="form.orderId" placeholder="用于生成政策权益" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="上牌日期"><a-date-picker v-model:value="form.registerDate" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="reverseOpen" title="实销冲正" :confirm-loading="saving" ok-text="确认冲正" cancel-text="取消"
             @ok="submitReverse">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="冲正后该序列号可重新登记；已生成的权益需按差异/下一期负数行处理。" />
      <a-form layout="vertical">
        <a-form-item label="冲正原因" required><a-textarea v-model:value="reverseReason" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="generateOpen" title="生成政策权益" :confirm-loading="saving"
             ok-text="生成权益" cancel-text="取消" @ok="submitGenerate">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="权益按实销日期与政策申报周期归期；同一订单的预享受项将转为正式权益。" />
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="实销单">{{ current?.retailNo }}</a-descriptions-item>
        <a-descriptions-item label="序列号">{{ current?.serialNo }}</a-descriptions-item>
        <a-descriptions-item label="实销日期">{{ current?.retailDate }}</a-descriptions-item>
      </a-descriptions>
      <a-form layout="vertical" style="margin-top:12px">
        <a-form-item label="关联销售订单 ID" required><a-input v-model:value="generateOrderId" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { retailApi } from '../../api/retail'
import { policyApi } from '../../api/policy'
import { customerApi, serialApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { RETAIL_STATUS_COLORS, RETAIL_STATUS_LABELS } from '../../types/retail'
import type { RetailRecord, RetailStatus } from '../../types/retail'
import type { ProductSerial } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_RETAIL_WRITE')
const canVerify = auth.can('IVS_RETAIL_VERIFY')

const rows = ref<RetailRecord[]>([])
const serials = ref<ProductSerial[]>([])
const customerOptions = ref<{ label: string; value: string }[]>([])
const filterStatus = ref<RetailStatus | undefined>()
const saving = ref(false)
const formOpen = ref(false)
const reverseOpen = ref(false)
const generateOpen = ref(false)
const editingId = ref<string>()
const current = ref<RetailRecord>()
const reverseReason = ref('')
const generateOrderId = ref('')

const statusOptions = Object.entries(RETAIL_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const serialOptions = computed(() => serials.value.map((item) => ({
  label: `${item.serialNo} · ${item.productName || item.productNo || ''}`,
  value: item.id,
})))

const form = reactive<Record<string, any>>({})
const columns = [
  { title: '实销单号', key: 'retailNo', dataIndex: 'retailNo', width: 140 },
  { title: '车辆', key: 'vehicle', width: 230 },
  { title: '实销日期', key: 'retailDate', dataIndex: 'retailDate', width: 110 },
  { title: '开票时间', key: 'invoiceIssuedAt', width: 170 },
  { title: '使用人', key: 'userName', dataIndex: 'userName', width: 110 },
  { title: '手机号', key: 'userMobile', dataIndex: 'userMobile', width: 130 },
  { title: '车牌', key: 'plateNo', dataIndex: 'plateNo', width: 100 },
  { title: '成交价', key: 'salePrice', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 360, fixed: 'right' },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value?: string) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }
function statusLabel(value: string) { return RETAIL_STATUS_LABELS[value as RetailStatus] }
function statusColor(value: string) { return RETAIL_STATUS_COLORS[value as RetailStatus] }

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  form.retailDate = dayjs()
  form.invoiceIssuedAt = dayjs()
  form.salePrice = 0
}

function openCreate() {
  editingId.value = undefined
  resetForm()
  formOpen.value = true
}

function openEdit(record: RetailRecord) {
  editingId.value = record.id
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, {
    serialId: record.serialId,
    customerId: record.customerId,
    retailDate: dayjs(record.retailDate),
    invoiceIssuedAt: dayjs(record.invoiceIssuedAt),
    registerDate: record.registerDate ? dayjs(record.registerDate) : undefined,
    userName: record.userName,
    userMobile: record.userMobile,
    plateNo: record.plateNo,
    salePrice: record.salePrice,
    orderId: record.orderId,
    remark: record.remark,
  })
  formOpen.value = true
}

function payload() {
  if (!form.serialId) throw new Error('请选择序列号')
  if (!form.retailDate || !form.invoiceIssuedAt) throw new Error('请填写实销日期与开票时间')
  if (!form.userName || !form.userMobile) throw new Error('请填写车辆使用人与手机号')
  return {
    serialId: form.serialId,
    customerId: form.customerId || null,
    retailDate: (form.retailDate as Dayjs).format('YYYY-MM-DD'),
    invoiceIssuedAt: (form.invoiceIssuedAt as Dayjs).toISOString(),
    registerDate: form.registerDate ? (form.registerDate as Dayjs).format('YYYY-MM-DD') : null,
    deliveryDate: null,
    userName: form.userName,
    userMobile: form.userMobile,
    plateNo: form.plateNo || null,
    salePrice: form.salePrice ?? null,
    orderId: form.orderId || null,
    remark: form.remark || null,
  }
}

async function save() {
  saving.value = true
  try {
    const body = payload()
    if (editingId.value) await retailApi.update(editingId.value, body)
    else await retailApi.create(body)
    message.success('实销记录已保存')
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function submitRecord(record: RetailRecord) {
  try { await retailApi.submit(record.id); message.success('已提交复核'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function verifyRecord(record: RetailRecord) {
  try { await retailApi.verify(record.id); message.success('实销已确认，可继续生成政策权益'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

function openReverse(record: RetailRecord) {
  current.value = record
  reverseReason.value = ''
  reverseOpen.value = true
}

async function submitReverse() {
  if (!current.value || !reverseReason.value.trim()) { message.warning('请填写冲正原因'); return }
  saving.value = true
  try {
    await retailApi.reverse(current.value.id, { reason: reverseReason.value.trim() })
    message.success('实销已冲正')
    reverseOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function openGenerate(record: RetailRecord) {
  current.value = record
  generateOrderId.value = record.orderId || ''
  generateOpen.value = true
}

async function submitGenerate() {
  if (!current.value || !generateOrderId.value) { message.warning('请填写关联销售订单 ID'); return }
  saving.value = true
  try {
    const created = await policyApi.generateEntitlements({
      orderId: generateOrderId.value,
      retailId: current.value.id,
      retailDate: current.value.retailDate,
      serialId: current.value.serialId,
    })
    message.success(`已生成 ${created.length} 条政策权益`)
    generateOpen.value = false
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function reload() {
  try { rows.value = await retailApi.list({ status: filterStatus.value }) }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [serialRows, customers] = await Promise.all([serialApi.list({ status: 'IN_STOCK' }), customerApi.list()])
    serials.value = serialRows
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.sub { color: #8b96a8; font-size: 12px; margin-top: 2px; }
</style>

