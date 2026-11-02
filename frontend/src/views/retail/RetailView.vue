<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="实销是政策返利的唯一时间基准。实销日期不得早于终端发票开票时间；复核确认后进入政策权益生成，冲正后允许重新登记。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">登记实销</a-button>
        <a-upload v-if="canWrite && isRegistration" :show-upload-list="false" accept=".csv,text/csv"
                  :before-upload="importRetails" :disabled="importing">
          <a-button :loading="importing">导入实销</a-button>
        </a-upload>
        <a-button v-if="canWrite && isRegistration" @click="downloadImportTemplate">下载导入模板</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-button @click="exportRetail">导出</a-button>
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
              <a-tag v-if="entitlementCount(record) > 0" color="green">权益已生成</a-tag>
              <a-button v-else-if="canVerify && record.status === 'VERIFIED'" size="small"
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
                        :disabled="!!editingId" placeholder="选择尚未核销的 VIN / SN"
                        @change="onSerialChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="终端客户">
              <a-radio-group v-model:value="customerMode" button-style="solid" style="margin-bottom:8px">
                <a-radio-button value="MANUAL">手工输入客户</a-radio-button>
                <a-radio-button value="EXISTING">选择未实销客户</a-radio-button>
              </a-radio-group>
              <a-select v-if="customerMode === 'EXISTING'" v-model:value="form.customerId"
                        :options="candidateCustomerOptions" allow-clear show-search
                        placeholder="选择尚未实销的客户" style="width:100%" />
              <div v-else class="hint">保存时将车辆使用人和手机号同步到客户模块</div>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="实销日期" required>
              <a-date-picker v-model:value="form.retailDate" format="YYYY-MM-DD" style="width:100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="终端发票日期" required>
              <a-date-picker v-model:value="form.invoiceIssuedAt" format="YYYY-MM-DD" style="width:100%" />
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
            <a-form-item label="成交价"><MoneyInput v-model:value="form.salePrice" :min="0" :precision="2" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="关联订单编号"><a-input v-model:value="form.orderNo" readonly placeholder="选择 VIN/SN 后自动带出" /></a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="上牌日期"><a-date-picker v-model:value="form.registerDate" format="YYYY-MM-DD" style="width:100%" /></a-form-item>
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
        <a-form-item label="关联销售订单" required>
          <a-input :value="salesOrderNoOf(current?.orderId)" readonly />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="importResultOpen" title="实销导入结果" footer="" width="720px">
      <a-alert :type="importErrors.length ? 'warning' : 'success'" show-icon :message="importSummary"
               style="margin-bottom:12px" />
      <a-table v-if="importErrors.length" :data-source="importErrors" row-key="lineNo" size="small"
               :pagination="false" :scroll="{ y: 320 }">
        <a-table-column title="文件行号" data-index="lineNo" :width="100" />
        <a-table-column title="失败原因" data-index="message" />
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { useRoute } from 'vue-router'
import { retailApi } from '../../api/retail'
import { salesApi } from '../../api/sales'
import { policyApi } from '../../api/policy'
import { customerApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { RETAIL_STATUS_COLORS, RETAIL_STATUS_LABELS } from '../../types/retail'
import type { RetailCandidate, RetailRecord, RetailStatus } from '../../types/retail'
import type { ProductSerial } from '../../types/masterdata'
import type { SalesOrder } from '../../types/sales'
import type { Entitlement } from '../../types/policy'
import { exportCsv, exportTimestamp, parseCsv } from '../../utils/csvExport'

interface RetailImportRow {
  vin: string
  retailDate: string
  invoiceDate: string
  userName: string
  userMobile: string
  plateNo: string
  salePrice: number
}

interface RetailImportError {
  lineNo: number
  message: string
}

const auth = useAuthStore()
const route = useRoute()
const canWrite = auth.can('IVS_RETAIL_WRITE')
const canVerify = auth.can('IVS_RETAIL_VERIFY')
const isLedger = computed(() => route.name === 'retail-ledger')
const isRegistration = computed(() => route.name === 'retail-record')

const rows = ref<RetailRecord[]>([])
const serials = ref<(ProductSerial & {
  orderId?: string; orderNo?: string; purchaseOrderId?: string; purchaseOrderNo?: string
  customerId?: string; customerName?: string
})[]>([])
const customerOptions = ref<{ label: string; value: string }[]>([])
const salesOrders = ref<SalesOrder[]>([])
const entitlements = ref<Entitlement[]>([])
const customerMode = ref<'MANUAL' | 'EXISTING'>('EXISTING')
const filterStatus = ref<RetailStatus | undefined>()
const saving = ref(false)
const importing = ref(false)
const importResultOpen = ref(false)
const importSummary = ref('')
const importErrors = ref<RetailImportError[]>([])
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
const candidateCustomerOptions = computed(() => {
  const customers = new Map<string, string>()
  for (const item of serials.value) {
    if (item.customerId && item.customerName) customers.set(item.customerId, item.customerName)
  }
  return [...customers.entries()].map(([value, label]) => ({ label, value }))
})

const form = reactive<Record<string, any>>({})
const columns = [
  { title: '实销单号', key: 'retailNo', dataIndex: 'retailNo', width: 140 },
  { title: '车辆', key: 'vehicle', width: 230 },
  { title: '实销日期', key: 'retailDate', dataIndex: 'retailDate', width: 110 },
          { title: '终端发票日期', key: 'invoiceIssuedAt', width: 130 },
  { title: '使用人', key: 'userName', dataIndex: 'userName', width: 110 },
  { title: '手机号', key: 'userMobile', dataIndex: 'userMobile', width: 130 },
  { title: '车牌', key: 'plateNo', dataIndex: 'plateNo', width: 100 },
  { title: '成交价', key: 'salePrice', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 360, fixed: 'right' },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value?: string) { return value ? dayjs(value).format('YYYY-MM-DD') : '-' }
function statusLabel(value: string) { return RETAIL_STATUS_LABELS[value as RetailStatus] }
function statusColor(value: string) { return RETAIL_STATUS_COLORS[value as RetailStatus] }
function exportRetail() {
  if (!rows.value.length) { message.warning('当前没有可导出的实销数据'); return }
  const title = isLedger.value ? '实销台账' : '实销登记'
  exportCsv<RetailRecord>(`${title}-${exportTimestamp()}`, [
    { title: '实销单号', value: (row) => row.retailNo },
    { title: 'VIN/SN', value: (row) => row.serialNo },
    { title: '商品', value: (row) => row.productName },
    { title: '实销日期', value: (row) => row.retailDate },
    { title: '终端发票日期', value: (row) => formatTime(row.invoiceIssuedAt) },
    { title: '车辆使用人', value: (row) => row.userName },
    { title: '手机号', value: (row) => row.userMobile },
    { title: '车牌照号', value: (row) => row.plateNo },
    { title: '成交价', value: (row) => row.salePrice },
    { title: '状态', value: (row) => statusLabel(row.status) },
  ], rows.value)
  message.success(`${title}数据已导出`)
}

function downloadImportTemplate() {
  const today = dayjs().format('YYYY-MM-DD')
  exportCsv<RetailImportRow>(`实销导入模板-${exportTimestamp()}`, [
    { title: 'VIN/SN', value: (row) => row.vin },
    { title: '实销日期', value: (row) => row.retailDate },
    { title: '终端发票日期', value: (row) => row.invoiceDate },
    { title: '车辆使用人', value: (row) => row.userName },
    { title: '手机号', value: (row) => row.userMobile },
    { title: '车牌照号', value: (row) => row.plateNo },
    { title: '成交价', value: (row) => row.salePrice },
  ], [{
    vin: 'LZZ1CLWB0NA000001',
    retailDate: today,
    invoiceDate: today,
    userName: '张三',
    userMobile: '13800000000',
    plateNo: '津A12345',
    salePrice: 450000,
  }])
}

async function importRetails(file: File) {
  importing.value = true
  try {
    const rows = parseCsv(await file.text())
    if (rows.length < 2) throw new Error('导入文件没有数据行')
    const headers = rows[0].map((value) => value.trim().replace(/\s+/g, '').toUpperCase())
    const column = (...aliases: string[]) => {
      const normalized = aliases.map((value) => value.replace(/\s+/g, '').toUpperCase())
      return headers.findIndex((header) => normalized.includes(header))
    }
    const vinIndex = column('VIN/SN', 'VIN', '序列号VIN/电池SN')
    const retailDateIndex = column('实销日期')
    const invoiceDateIndex = column('终端发票日期', '终端发票开票日期')
    const userNameIndex = column('车辆使用人', '使用人', '终端客户')
    const mobileIndex = column('手机号', '手机号码')
    const plateIndex = column('车牌照号', '车牌')
    const salePriceIndex = column('成交价', '销售价')
    const missing = [
      vinIndex < 0 ? 'VIN/SN' : '', retailDateIndex < 0 ? '实销日期' : '',
      invoiceDateIndex < 0 ? '终端发票日期' : '', userNameIndex < 0 ? '车辆使用人' : '',
      mobileIndex < 0 ? '手机号' : '',
    ].filter(Boolean)
    if (missing.length) throw new Error(`导入模板缺少字段：${missing.join('、')}`)

    const valueAt = (row: string[], index: number) => index < 0 ? '' : String(row[index] ?? '').trim()
    const prepared: Array<RetailImportRow & {
      lineNo: number; serialId: string; orderId?: string; customerId?: string
    }> = []
    const errors: RetailImportError[] = []
    const seenVins = new Set<string>()

    for (let index = 1; index < rows.length; index += 1) {
      const row = rows[index]
      if (!row.some((value) => value.trim() !== '')) continue
      const lineNo = index + 1
      try {
        const vin = valueAt(row, vinIndex).toUpperCase()
        if (!vin) throw new Error('VIN/SN 不能为空')
        if (seenVins.has(vin)) throw new Error('导入文件内 VIN/SN 重复')
        seenVins.add(vin)
        const serial = serials.value.find((item) => item.serialNo?.toUpperCase() === vin)
        if (!serial) throw new Error('该 VIN/SN 不是可实销候选（未锁定、已实销或不存在）')
        const retailDate = valueAt(row, retailDateIndex)
        const invoiceDate = valueAt(row, invoiceDateIndex)
        if (!/^\d{4}-\d{2}-\d{2}$/.test(retailDate) || !dayjs(retailDate).isValid()) {
          throw new Error('实销日期格式应为 YYYY-MM-DD')
        }
        if (!/^\d{4}-\d{2}-\d{2}$/.test(invoiceDate) || !dayjs(invoiceDate).isValid()) {
          throw new Error('终端发票日期格式应为 YYYY-MM-DD')
        }
        if (dayjs(retailDate).isBefore(dayjs(invoiceDate), 'day')) {
          throw new Error('实销日期不能早于终端发票日期')
        }
        const userName = valueAt(row, userNameIndex)
        const userMobile = valueAt(row, mobileIndex)
        if (!userName) throw new Error('车辆使用人不能为空')
        if (!userMobile) throw new Error('手机号不能为空')
        const priceText = valueAt(row, salePriceIndex)
        const salePrice = priceText ? Number(priceText.replace(/,/g, '')) : undefined
        if (priceText && (!Number.isFinite(salePrice) || Number(salePrice) < 0)) {
          throw new Error('成交价必须是非负数字')
        }
        prepared.push({
          lineNo, vin, retailDate, invoiceDate, userName, userMobile,
          plateNo: valueAt(row, plateIndex), salePrice: Number(salePrice ?? 0),
          serialId: serial.id, orderId: serial.orderId,
          customerId: serial.customerName === userName ? serial.customerId : undefined,
        })
      } catch (error) {
        errors.push({ lineNo, message: (error as Error).message })
      }
    }

    let success = 0
    const customerCache = new Map<string, string>()
    for (const row of prepared) {
      try {
        let customerId = row.customerId
        if (!customerId) {
          const key = `${row.userName}|${row.userMobile}`
          customerId = customerCache.get(key)
          if (!customerId) {
            const customer = await customerApi.create({
              name: row.userName,
              customerType: 'INDIVIDUAL',
              contactName: row.userName,
              contactPhone: row.userMobile,
              creditLimit: 0,
            })
            customerId = customer.id
            customerCache.set(key, customerId)
          }
        }
        await retailApi.create({
          serialId: row.serialId,
          customerId,
          retailDate: row.retailDate,
          invoiceIssuedAt: dayjs(row.invoiceDate).startOf('day').toISOString(),
          registerDate: null,
          deliveryDate: null,
          userName: row.userName,
          userMobile: row.userMobile,
          plateNo: row.plateNo || null,
          salePrice: row.salePrice,
          orderId: row.orderId || null,
          remark: 'CSV批量导入',
        })
        success += 1
      } catch (error) {
        errors.push({ lineNo: row.lineNo, message: (error as Error).message })
      }
    }

    const customers = await customerApi.list()
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    await Promise.all([reload(), refreshCandidates(), refreshEntitlements()])
    importErrors.value = errors.sort((a, b) => a.lineNo - b.lineNo)
    importSummary.value = `导入完成：成功 ${success} 条，失败 ${errors.length} 条`
    importResultOpen.value = true
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    importing.value = false
  }
  return false
}

function entitlementCount(record: RetailRecord) {
  return entitlements.value.filter((item) => item.retailId === record.id || item.serialId === record.serialId).length
}

function resetForm() {
  Object.keys(form).forEach((key) => delete form[key])
  form.retailDate = dayjs()
  form.invoiceIssuedAt = dayjs()
  form.salePrice = 0
}

async function refreshCandidates() {
  serials.value = (await retailApi.candidates()).map((item: RetailCandidate) => ({
    ...item.serial, orderId: item.orderId, orderNo: item.orderNo,
    purchaseOrderId: item.purchaseOrderId, purchaseOrderNo: item.purchaseOrderNo,
    customerId: item.customerId, customerName: item.customerName,
  }))
}

async function refreshEntitlements() {
  entitlements.value = await policyApi.entitlements()
}

function onSerialChange() {
  const selected = serials.value.find((item) => item.id === form.serialId)
  form.orderId = selected?.orderId
  form.orderNo = selected?.purchaseOrderNo || selected?.orderNo
}

function salesOrderNoOf(orderId?: string) {
  const order = salesOrders.value.find((item) => item.id === orderId)
  return order?.externalOrderNo || order?.orderNo || '-'
}

async function openCreate() {
  await refreshCandidates()
  editingId.value = undefined
  customerMode.value = 'EXISTING'
  resetForm()
  formOpen.value = true
}

function openEdit(record: RetailRecord) {
  editingId.value = record.id
  if (!serials.value.some((item) => item.id === record.serialId)) {
    serials.value.push({
      id: record.serialId,
      serialNo: record.serialNo,
      productId: record.productId,
      productNo: record.productNo,
      productName: record.productName,
      categoryCode: record.categoryCode,
      status: 'IN_STOCK',
      ownershipType: 'OWNED',
      allowedNextStatuses: [],
      createdAt: record.createdAt,
    })
  }
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
    orderNo: serials.value.find((item) => item.id === record.serialId)?.purchaseOrderNo
      || salesOrderNoOf(record.orderId),
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
    if (customerMode.value === 'MANUAL') {
      const customer = await customerApi.create({
        name: body.userName,
        customerType: 'INDIVIDUAL',
        contactName: body.userName,
        contactPhone: body.userMobile,
        creditLimit: 0,
      })
      body.customerId = customer.id
      customerOptions.value.unshift({ label: `${customer.name}（${customer.code}）`, value: customer.id })
    }
    if (editingId.value) await retailApi.update(editingId.value, body)
    else await retailApi.create(body)
    message.success('实销记录已保存')
    formOpen.value = false
    await Promise.all([reload(), refreshCandidates()])
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
    await Promise.all([reload(), refreshCandidates()])
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
    await Promise.all([reload(), refreshEntitlements()])
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function reload() {
  try { rows.value = await retailApi.list({ status: filterStatus.value }) }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [serialRows, customers, orders, entitlementRows] = await Promise.all([
      retailApi.candidates(), customerApi.list(), salesApi.orders(), policyApi.entitlements(),
    ])
    serials.value = serialRows.map((item: RetailCandidate) => ({
      ...item.serial, orderId: item.orderId, orderNo: item.orderNo,
      purchaseOrderId: item.purchaseOrderId, purchaseOrderNo: item.purchaseOrderNo,
      customerId: item.customerId, customerName: item.customerName,
    }))
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    salesOrders.value = orders
    entitlements.value = entitlementRows
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.sub { color: #8b96a8; font-size: 12px; margin-top: 2px; }
</style>

