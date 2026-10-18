<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="采购单可跨产品大项（整车 / 电池 / 配件同一张单）。整车/电池收货时直接录入 VIN / 电池 SN 与单车采购价，系统自动建立一物一档；数量商品按数量收货。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建采购单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:170px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="采购单号 / 订单编号" allow-clear style="width:220px" @press-enter="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="false"
               :scroll="{ x: 1200 }">
        <template #expandedRowRender="{ record: order }">
          <a-table :data-source="order.lines" row-key="id" size="small" :pagination="false"
                   :scroll="{ x: 1400 }">
            <a-table-column title="订单编号" :width="150">
              <template #default>{{ order.externalOrderNo || order.orderNo }}</template>
            </a-table-column>
            <a-table-column title="车系" :width="140">
              <template #default="{ record: line }">{{ line.series || '-' }}</template>
            </a-table-column>
            <a-table-column title="配置" :width="150">
              <template #default="{ record: line }">{{ line.configCode || '-' }}</template>
            </a-table-column>
            <a-table-column title="商品" :width="220">
              <template #default="{ record: line }">{{ line.productName }}（{{ line.productNo }}）</template>
            </a-table-column>
            <a-table-column title="数量" data-index="quantity" :width="100" />
            <a-table-column title="已收" data-index="receivedQuantity" :width="90" />
            <a-table-column title="待收" data-index="remainingQuantity" :width="90" />
            <a-table-column title="单价" :width="120">
              <template #default="{ record: line }">{{ money(line.unitPrice) }}</template>
            </a-table-column>
            <a-table-column title="总计车款" :width="140">
              <template #default="{ record: line }"><b>{{ money(line.amount) }}</b></template>
            </a-table-column>
            <a-table-column title="返利政策" :width="260">
              <template #default="{ record: line }">
                <a-space v-if="line.policies?.length" wrap size="small">
                  <a-tag v-for="policy in line.policies" :key="policy.id" color="blue">
                    {{ policy.name }}
                  </a-tag>
                </a-space>
                <span v-else>-</span>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'externalOrderNo'">
            {{ record.externalOrderNo || '-' }}
          </template>
          <template v-else-if="column.key === 'totalAmount'">{{ money(record.totalAmount) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a v-if="canWrite && record.status === 'DRAFT'" @click="approve(record)">审核</a>
              <a v-if="canWrite && (record.status === 'APPROVED' || record.status === 'PARTIALLY_RECEIVED')"
                 @click="openReceive(record)">收货</a>
              <a v-if="canWrite && (record.status === 'DRAFT' || record.status === 'APPROVED')"
                 @click="cancel(record)">取消</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="createOpen" title="新建采购单" :confirm-loading="saving" ok-text="提交" cancel-text="取消"
             width="1180px" @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="6">
            <a-form-item label="订单编号" required>
              <a-input v-model:value="form.externalOrderNo" placeholder="厂家/业务订单编号" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="供应商" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search
                        @change="onSupplierChange" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="收货仓库">
              <a-select v-model:value="form.warehouseId" :options="warehouseOptions" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="预计到货">
              <a-date-picker v-model:value="form.expectedDate" value-format="YYYY-MM-DD" style="width:100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-divider orientation="left" style="font-size:13px">采购明细</a-divider>
        <a-table :data-source="form.lines" row-key="_key" size="small" :pagination="false"
                 :scroll="{ x: 1390 }">
          <a-table-column title="订单编号" :width="150">
            <template #default>{{ form.externalOrderNo || '-' }}</template>
          </a-table-column>
          <a-table-column title="车系" :width="260">
            <template #default="{ record: line }">
              <a-select v-model:value="line.productId" :options="productOptions" show-search
                        placeholder="选择车系 / 配置 / 商品" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="配置" :width="160">
            <template #default="{ record: line }">{{ configOf(line.productId) }}</template>
          </a-table-column>
          <a-table-column title="数量" :width="120">
            <template #default="{ record: line }">
              <a-input-number v-model:value="line.quantity" :min="0.001" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="单价" :width="140">
            <template #default="{ record: line }">
              <a-input-number v-model:value="line.unitPrice" :min="0" :precision="2" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="总计车款" :width="150">
            <template #default="{ record: line }"><b>{{ money(lineTotal(line)) }}</b></template>
          </a-table-column>
          <a-table-column title="返利政策（可多选）" :width="330">
            <template #default="{ record: line }">
              <a-select v-model:value="line.policyIds" mode="multiple" :options="policyOptionsForSupplier"
                        :disabled="!form.supplierId" allow-clear show-search option-filter-prop="label"
                        :not-found-content="form.supplierId ? '该供应商暂无已发布返利政策' : '请先选择供应商'"
                        :placeholder="form.supplierId ? '选择一项或多项返利政策' : '请先选择供应商'" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="80">
            <template #default="{ record: line }">
              <a-button danger size="small" :disabled="form.lines.length <= 1" @click="removeLine(line._key)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>
        <a-button type="dashed" block style="margin-top:10px" @click="addLine">+ 增加采购明细</a-button>
        <a-form-item label="备注" style="margin-top:16px"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="receiveOpen" :title="`收货 - ${current?.externalOrderNo || current?.orderNo || ''}`" :confirm-loading="saving"
             ok-text="确认收货" cancel-text="取消" width="900px" @ok="submitReceive">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="整车/电池等一物一档商品请按「VIN,单车采购价」或「电池SN,单车采购价」每行一条填写，VIN/SN 不存在时自动建档；未填写价格时使用采购单单价。数量商品填写本次数量。" />
      <a-form layout="vertical">
        <a-form-item label="收货仓库" required>
          <a-select v-model:value="receiveForm.warehouseId" :options="warehouseOptions" />
        </a-form-item>
        <a-table :data-source="current?.lines ?? []" row-key="id" size="small" :pagination="false"
                 :locale="{ emptyText: '无明细' }">
          <a-table-column title="商品" :width="220">
            <template #default="{ record }">{{ record.productName }}</template>
          </a-table-column>
          <a-table-column title="待收" data-index="remainingQuantity" :width="90" />
          <a-table-column title="本次收货" :width="420">
            <template #default="{ record }">
              <a-textarea v-if="record.trackingMode === 'SERIAL'" v-model:value="receiveInput[record.id].serialNos"
                          :rows="3" :disabled="Number(record.remainingQuantity) === 0"
                          placeholder="每行：VIN,单车采购价，例如 LZZ1...20260001,420000" />
              <a-input-number v-else v-model:value="receiveInput[record.id].quantity" :min="0"
                              :max="Number(record.remainingQuantity)" :disabled="Number(record.remainingQuantity) === 0"
                              style="width:160px" />
            </template>
          </a-table-column>
        </a-table>
        <a-form-item label="备注" style="margin-top:16px"><a-input v-model:value="receiveForm.remark" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { purchaseApi } from '../../api/procurement'
import { productApi, supplierApi, warehouseApi } from '../../api/masterdata'
import { policyApi } from '../../api/policy'
import { useAuthStore } from '../../stores/auth'
import { PURCHASE_STATUS_COLORS, PURCHASE_STATUS_LABELS } from '../../types/procurement'
import type { PurchaseOrder, PurchaseOrderStatus } from '../../types/procurement'
import type { Policy } from '../../types/policy'

const statusLabel = (status: string) => PURCHASE_STATUS_LABELS[status as PurchaseOrderStatus]
const statusColor = (status: string) => PURCHASE_STATUS_COLORS[status as PurchaseOrderStatus]

const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')

const rows = ref<PurchaseOrder[]>([])
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const createOpen = ref(false)
const receiveOpen = ref(false)
const saving = ref(false)
const current = ref<PurchaseOrder | null>(null)
const supplierOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const productOptions = ref<{ label: string; value: string; series?: string; configCode?: string }[]>([])
const policies = ref<Policy[]>([])

const form = reactive<Record<string, any>>({ lines: [newLine()] })
const receiveForm = reactive<Record<string, any>>({})
const receiveInput = reactive<Record<string, { serialNos?: string; quantity?: number }>>({})

const columns = [
  { title: '采购单号', key: 'orderNo', dataIndex: 'orderNo', width: 140 },
  { title: '订单编号', key: 'externalOrderNo', width: 160 },
  { title: '供应商', key: 'supplierName', dataIndex: 'supplierName', width: 200 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '总金额', key: 'totalAmount', width: 140 },
  { title: '预计到货', key: 'expectedDate', dataIndex: 'expectedDate', width: 120 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 200 },
]

const statusOptions = Object.entries(PURCHASE_STATUS_LABELS).map(([value, label]) => ({ value, label }))

const policyOptionsForSupplier = computed(() => policies.value
  .filter((policy) => policy.supplierId === form.supplierId)
  .map((policy) => ({ label: `${policy.policyNo} ${policy.name}`, value: policy.id })))

function newLine() {
  return { _key: `line-${Date.now()}-${Math.random()}`, productId: undefined, quantity: 1, unitPrice: undefined, policyIds: [] as string[] }
}

function addLine() { form.lines.push(newLine()) }
function removeLine(key: string) {
  if (form.lines.length <= 1) return
  const index = (form.lines as Record<string, any>[]).findIndex((line) => line._key === key)
  if (index >= 0) form.lines.splice(index, 1)
}
function lineTotal(line: Record<string, any>) {
  const quantity = Number(line.quantity ?? 0)
  const unitPrice = Number(line.unitPrice ?? 0)
  return Number.isFinite(quantity * unitPrice) ? quantity * unitPrice : 0
}
function money(value?: number | null) {
  if (value == null) return '-'
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function configOf(productId?: string) {
  if (!productId) return '-'
  return productOptions.value.find((item) => item.value === productId)?.configCode || '-'
}
function onSupplierChange() {
  const allowed = new Set(policyOptionsForSupplier.value.map((item) => item.value))
  for (const line of form.lines as Record<string, any>[]) {
    line.policyIds = (line.policyIds ?? []).filter((id: string) => allowed.has(id))
  }
}

async function reload() {
  try {
    rows.value = await purchaseApi.list({ keyword: keyword.value || undefined, status: filterStatus.value })
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  form.externalOrderNo = ''
  form.lines = [newLine()]
  createOpen.value = true
}

async function submit() {
  if (!String(form.externalOrderNo ?? '').trim()) { message.warning('请填写订单编号'); return }
  if (!form.supplierId) { message.warning('请选择供应商'); return }
  const lines = (form.lines as Record<string, any>[])
    .filter((line) => line.productId && line.quantity > 0 && line.unitPrice != null)
  if (!lines.length) { message.warning('请至少填写一行有效明细'); return }
  saving.value = true
  try {
    await purchaseApi.create({
      supplierId: form.supplierId,
      externalOrderNo: String(form.externalOrderNo).trim(),
      warehouseId: form.warehouseId,
      expectedDate: form.expectedDate,
      remark: form.remark,
      lines: lines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
        policyIds: line.policyIds ?? [],
      })),
    })
    message.success('采购单已创建')
    createOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function approve(record: PurchaseOrder) {
  try {
    await purchaseApi.approve(record.id)
    message.success('已审核')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function cancel(record: PurchaseOrder) {
  try {
    await purchaseApi.cancel(record.id)
    message.success('已取消')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openReceive(record: PurchaseOrder) {
  current.value = record
  Object.keys(receiveForm).forEach((key) => delete receiveForm[key])
  Object.keys(receiveInput).forEach((key) => delete receiveInput[key])
  receiveForm.warehouseId = record.warehouseId
  record.lines.forEach((line) => { receiveInput[line.id] = {} })
  receiveOpen.value = true
}

async function submitReceive() {
  if (!current.value) return
  if (!receiveForm.warehouseId) { message.warning('请选择收货仓库'); return }
  const lines: Record<string, unknown>[] = []
  for (const line of current.value.lines) {
    if (Number(line.remainingQuantity) === 0) continue
    const input = receiveInput[line.id] ?? {}
    if (line.trackingMode === 'SERIAL') {
      const serialRows = String(input.serialNos ?? '').split('\n').map((item) => item.trim()).filter(Boolean)
      for (const row of serialRows) {
        const parts = row.split(/[,，\t]/).map((item) => item.trim()).filter(Boolean)
        if (!parts.length) continue
        const serialNo = parts[0]
        const unitPrice = parts[1] != null && parts[1] !== '' ? Number(parts[1]) : Number(line.unitPrice)
        if (!Number.isFinite(unitPrice) || unitPrice < 0) { message.warning(`VIN ${serialNo} 的单车采购价无效`); return }
        lines.push({ orderLineId: line.id, quantity: 1, serialNos: [serialNo], unitPrice })
      }
    } else if (input.quantity && Number(input.quantity) > 0) {
      lines.push({ orderLineId: line.id, quantity: input.quantity, unitPrice: line.unitPrice })
    }
  }
  if (!lines.length) { message.warning('请填写本次收货内容'); return }
  saving.value = true
  try {
    const receipt = await purchaseApi.receive(current.value.id, {
      warehouseId: receiveForm.warehouseId, remark: receiveForm.remark, lines,
    })
    message.success(`收货完成：${receipt.receiptNo}，金额 ${receipt.amount}`)
    receiveOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const [suppliers, warehouses, products, publishedPolicies] = await Promise.all([
      supplierApi.list(), warehouseApi.list(), productApi.list(), policyApi.list({ status: 'PUBLISHED' }),
    ])
    supplierOptions.value = suppliers.map((item) => ({
      label: `${item.name}${item.supplierType === 'OEM' ? '（整车厂）' : ''}`, value: item.id,
    }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    productOptions.value = products.map((item) => ({
      label: `${item.series || '未填车系'} · ${item.configCode || '未填配置'} · ${item.name}${item.model ? ` · ${item.model}` : ''}（${item.productNo}）`,
      value: item.id,
      series: item.series,
      configCode: item.configCode,
    }))
    policies.value = publishedPolicies
  } catch { /* ignore */ }
  await reload()
})
</script>











