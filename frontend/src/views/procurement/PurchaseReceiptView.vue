<template>
  <div class="purchase-receipt-page">
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="收货入库按明细直接展示。申请合格证日期可后补；单个 VIN 的收货仓库可点击修改，并同步调整库存位置。" />
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-button @click="exportReceipts">导出</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="lines" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 20 }" :scroll="{ x: 1310 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'unitPrice'">{{ money(record.unitPrice) }}</template>
          <template v-else-if="column.key === 'certificateApplicationDate'">
            <a-button type="link" size="small" style="padding:0" @click="openCertificate(record)">
              {{ record.certificateApplicationDate || '登记' }}
            </a-button>
          </template>
          <template v-else-if="column.key === 'serialNos'">
            <a-space v-if="record.serialNos?.length" wrap size="small">
              <a-tag v-for="serial in record.serialNos" :key="serial">{{ serial }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'warehouseName'">
            <a-button v-if="record.serialNos?.length === 1" type="link" size="small" style="padding:0"
                      @click="openWarehouse(record)">{{ record.warehouseName || '选择仓库' }}</a-button>
            <span v-else>{{ record.warehouseName || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'POSTED' ? 'green' : 'orange'">
              {{ record.status === 'POSTED' ? '已入库' : '待入库' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="certificateOpen" title="登记申请合格证日期" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" @ok="saveCertificate">
      <a-form layout="vertical">
        <a-form-item label="商品">{{ current?.productName || '-' }}</a-form-item>
        <a-form-item label="VIN / SN">{{ current?.serialNos?.join('、') || '-' }}</a-form-item>
        <a-form-item label="申请合格证日期">
          <a-date-picker v-model:value="certificateDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" allow-clear style="width:100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="warehouseOpen" title="修改单个 VIN 收货仓库" :confirm-loading="saving"
             ok-text="保存并调整库存" cancel-text="取消" @ok="saveWarehouse">
      <a-form layout="vertical">
        <a-form-item label="VIN / SN">{{ warehouseCurrent?.serialNos?.join('、') || '-' }}</a-form-item>
        <a-form-item label="收货仓库" required>
          <a-select v-model:value="warehouseId" :options="warehouseOptions" allow-clear style="width:100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { purchaseApi } from '../../api/procurement'
import { warehouseApi } from '../../api/masterdata'
import type { PurchaseReceipt, PurchaseReceiptLine } from '../../types/procurement'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'

interface ReceiptLineRow extends PurchaseReceiptLine {
  receiptId: string
  orderNo: string
  status: PurchaseReceipt['status']
}

const receipts = ref<PurchaseReceipt[]>([])
const certificateOpen = ref(false)
const certificateDate = ref<string>()
const current = ref<ReceiptLineRow>()
const warehouseOpen = ref(false)
const warehouseCurrent = ref<ReceiptLineRow>()
const warehouseId = ref<string>()
const warehouseOptions = ref<{ label: string; value: string }[]>([])
const saving = ref(false)

const lines = computed<ReceiptLineRow[]>(() => receipts.value.flatMap((receipt) =>
  receipt.lines.map((line) => ({ ...line, receiptId: receipt.id, orderNo: receipt.orderNo, status: receipt.status }))))
const columns = [
  { title: '订单编号', key: 'orderNo', dataIndex: 'orderNo', width: 150, ellipsis: true },
  { title: '车系', key: 'series', dataIndex: 'series', width: 150, ellipsis: true },
  { title: '驾驶室', key: 'cabType', dataIndex: 'cabType', width: 90, align: 'center' },
  { title: 'VIN / SN', key: 'serialNos', width: 220 },
  { title: '配置', key: 'configText', dataIndex: 'configText', width: 170, ellipsis: true },
  { title: '申请合格证日期', key: 'certificateApplicationDate', width: 150, align: 'center' },
  { title: '单价', key: 'unitPrice', width: 120, align: 'right' },
  { title: '收货仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 150, ellipsis: true },
  { title: '状态', key: 'status', width: 110, align: 'center' },
]

function money(value?: number) {
  return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function exportReceipts() {
  if (!lines.value.length) { message.warning('当前没有可导出的收货入库数据'); return }
  exportCsv<ReceiptLineRow>(`收货入库-${exportTimestamp()}`, [
    { title: '订单编号', value: (row) => row.orderNo },
    { title: '车系', value: (row) => row.series },
    { title: '驾驶室', value: (row) => row.cabType },
    { title: 'VIN/SN', value: (row) => row.serialNos?.join('、') },
    { title: '配置', value: (row) => row.configText },
    { title: '申请合格证日期', value: (row) => row.certificateApplicationDate },
    { title: '单价', value: (row) => row.unitPrice },
    { title: '收货仓库', value: (row) => row.warehouseName },
    { title: '状态', value: (row) => row.status === 'POSTED' ? '已入库' : '待入库' },
  ], lines.value)
  message.success('收货入库数据已导出')
}
function openCertificate(line: ReceiptLineRow) {
  current.value = line
  certificateDate.value = line.certificateApplicationDate
  certificateOpen.value = true
}
async function saveCertificate() {
  if (!current.value) return
  saving.value = true
  try {
    await purchaseApi.updateReceiptCertificateDate(current.value.receiptId, current.value.id,
      certificateDate.value || null)
    message.success('申请合格证日期已保存')
    certificateOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
function openWarehouse(line: ReceiptLineRow) {
  warehouseCurrent.value = line
  warehouseId.value = line.warehouseId
  warehouseOpen.value = true
}
async function saveWarehouse() {
  if (!warehouseCurrent.value || !warehouseId.value) { message.warning('请选择收货仓库'); return }
  saving.value = true
  try {
    await purchaseApi.updateReceiptWarehouse(warehouseCurrent.value.receiptId, warehouseCurrent.value.id, warehouseId.value)
    message.success('收货仓库已修改，库存位置已同步调整')
    warehouseOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
async function reload() {
  try { receipts.value = await purchaseApi.receipts() }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const warehouses = await warehouseApi.list()
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>
