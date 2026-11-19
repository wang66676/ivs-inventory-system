<template>
  <div class="purchase-receipt-page">
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               :message="props.scope === 'BATTERY' ? '电池收货入库按电池 SN 展示，统一仓库为天津容大，申请合格证日期可后补。' : '收货入库按明细直接展示。申请合格证日期可后补；单个 VIN 的收货仓库可点击修改，并同步调整库存位置。'" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-button @click="exportReceipts">导出</a-button>
        <template v-if="props.scope === 'VEHICLE' && canWrite">
          <a-button @click="downloadReceiptTemplate">下载导入模板</a-button>
          <a-button type="primary" :loading="importing" @click="triggerImport">批量导入</a-button>
          <input ref="importInput" type="file" accept=".csv,text/csv" style="display:none" @change="handleImportFile" />
        </template>
      </a-space>
      <a-table v-resizable-columns :data-source="lines" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 20 }" :scroll="{ x: 1600 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'unitPrice'">{{ money(record.unitPrice) }}</template>
          <template v-else-if="column.key === 'batteryInfo'">{{ batteryText(record) }}</template>
          <template v-else-if="column.key === 'certificateApplicationDate'">
            <a-button v-if="record.status === 'POSTED'" type="link" size="small" style="padding:0" @click="openCertificate(record)">
              {{ record.certificateApplicationDate || '登记' }}
            </a-button>
            <span v-else>{{ record.certificateApplicationDate || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'serialNos'">
            <a-space v-if="record.serialNos?.length" wrap size="small">
              <a-tag v-for="serial in record.serialNos" :key="serial">{{ serial }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'warehouseName'">
            <a-button v-if="props.scope !== 'BATTERY' && record.status === 'POSTED' && record.serialNos?.length === 1"
                      type="link" size="small" style="padding:0" @click="openWarehouse(record)">{{ record.warehouseName || '选择仓库' }}</a-button>
            <span v-else>{{ record.warehouseName || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-space size="small">
              <a-tag :color="receiptStatusColor(record.status)">{{ receiptStatusLabel(record.status) }}</a-tag>
              <a v-if="canWrite && record.status === 'POSTED'" danger @click="returnReceipt(record)">退回</a>
            </a-space>
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
import { message, Modal } from 'ant-design-vue'
import { purchaseApi } from '../../api/procurement'
import { productApi, supplierApi, warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { PurchaseReceipt, PurchaseReceiptLine } from '../../types/procurement'
import type { Product, Supplier } from '../../types/masterdata'
import { exportCsv, exportTimestamp, parseCsv } from '../../utils/csvExport'
import type { BusinessScope } from '../../utils/businessScope'

const props = withDefaults(defineProps<{ scope?: BusinessScope }>(), { scope: 'VEHICLE' })
const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')

interface ReceiptLineRow extends PurchaseReceiptLine {
  receiptId: string
  receiptNo: string
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
const importing = ref(false)
const importInput = ref<HTMLInputElement>()
const products = ref<Product[]>([])
const suppliers = ref<Supplier[]>([])

const lines = computed<ReceiptLineRow[]>(() => receipts.value.flatMap((receipt) =>
  (receipt.businessScope || 'VEHICLE') !== props.scope ? [] : receipt.lines.map((line) => ({
    ...line, receiptId: receipt.id, receiptNo: receipt.receiptNo, orderNo: receipt.orderNo, status: receipt.status,
  }))))
const vehicleColumns = [
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
const batteryColumns = [
  { title: '订单编号', key: 'orderNo', dataIndex: 'orderNo', width: 150, ellipsis: true },
  { title: '商品', key: 'series', dataIndex: 'series', width: 180, ellipsis: true },
  { title: '电池信息', key: 'batteryInfo', width: 320, ellipsis: true },
  { title: '电池 SN', key: 'serialNos', width: 220 },
  { title: '申请合格证日期', key: 'certificateApplicationDate', width: 150, align: 'center' },
  { title: '采购单价', key: 'unitPrice', width: 120, align: 'right' },
  { title: '收货仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 140, ellipsis: true },
  { title: '状态', key: 'status', width: 110, align: 'center' },
]
const columns = computed(() => props.scope === 'BATTERY' ? batteryColumns : vehicleColumns)

function money(value?: number) {
  return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function receiptStatusLabel(status: PurchaseReceipt['status']) {
  return status === 'POSTED' ? '已入库' : status === 'RETURNED' ? '已退回' : '待入库'
}
function receiptStatusColor(status: PurchaseReceipt['status']) {
  return status === 'POSTED' ? 'green' : status === 'RETURNED' ? 'red' : 'orange'
}
function batteryText(line: PurchaseReceiptLine) {
  if (!line.batteryBoxManufacturer && !line.batteryPackManufacturer) return '-'
  return `箱：${line.batteryBoxManufacturer || '-'} / ${line.batteryBoxModel || '-'}；包：${line.batteryPackManufacturer || '-'} / ${line.batteryEnergyKwh ?? '-'}kWh`
}
function exportReceipts() {
  if (!lines.value.length) { message.warning('当前没有可导出的收货入库数据'); return }
  if (props.scope === 'BATTERY') {
    exportCsv<ReceiptLineRow>(`电池收货入库-${exportTimestamp()}`, [
      { title: '订单编号', value: (row) => row.orderNo },
      { title: '商品', value: (row) => row.series },
      { title: '电池信息', value: (row) => batteryText(row) },
      { title: '电池 SN', value: (row) => row.serialNos?.join('、') },
      { title: '申请合格证日期', value: (row) => row.certificateApplicationDate },
      { title: '采购单价', value: (row) => row.unitPrice },
      { title: '收货仓库', value: () => '天津容大' },
      { title: '状态', value: (row) => receiptStatusLabel(row.status) },
    ], lines.value)
    message.success('电池收货入库数据已导出')
    return
  }
  exportCsv<ReceiptLineRow>(`收货入库-${exportTimestamp()}`, [
    { title: '订单编号', value: (row) => row.orderNo },
    { title: '车系', value: (row) => row.series },
    { title: '驾驶室', value: (row) => row.cabType },
    { title: 'VIN/SN', value: (row) => row.serialNos?.join('、') },
    { title: '配置', value: (row) => row.configText },
    { title: '申请合格证日期', value: (row) => row.certificateApplicationDate },
    { title: '单价', value: (row) => row.unitPrice },
    { title: '收货仓库', value: (row) => row.warehouseName },
    { title: '状态', value: (row) => receiptStatusLabel(row.status) },
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
function returnReceipt(line: ReceiptLineRow) {
  const receipt = receipts.value.find((item) => item.id === line.receiptId)
  if (!receipt) return
  Modal.confirm({
    title: `退回收货单 ${receipt.receiptNo}？`,
    content: `将整张收货单退回，冲销 ${receipt.lines.flatMap((item) => item.serialNos || []).join('、') || receipt.receiptNo} 的库存、应付和订单收货数量。存在返利抵扣、已付款或已匹配进项发票时系统会阻止退回。`,
    okText: '确认退回',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await purchaseApi.returnReceipt(receipt.id, '收货信息有误，退回重录')
        message.success('收货单已退回，订单已恢复为可收货状态')
        await reload()
      } catch (error) { message.error((error as Error).message) }
    },
  })
}
function downloadReceiptTemplate() {
  exportCsv('整车收货批量导入模板', [
    { title: '订单编号', value: () => 'CG2026111301' },
    { title: '供应商', value: () => '示例整车厂' },
    { title: '车系/商品', value: () => '示例车系或商品名称' },
    { title: '配置', value: () => '示例配置' },
    { title: 'VIN/SN', value: () => 'LZZ1EXAMPLE000001' },
    { title: '采购价', value: () => 420000 },
    { title: '收货仓库', value: () => '主体仓' },
    { title: '申请合格证日期', value: () => '2026-11-13' },
  ], [{}] as Record<string, never>[])
}
function triggerImport() { importInput.value?.click() }
async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) await importReceipts(file)
  input.value = ''
}
interface ImportRow {
  rowIndex: number
  orderNo: string
  supplierText: string
  product: Product
  configText: string
  vin: string
  price: number
  warehouseId?: string
  certificateDate: string | null
}

async function importReceipts(file: File) {
  importing.value = true
  try {
    const csv = parseCsv(await file.text())
    if (csv.length < 2) throw new Error('CSV 没有有效数据')
    const headers = new Map(csv[0].map((header, index) => [header.trim(), index]))
    const column = (row: string[], ...names: string[]) => {
      for (const name of names) {
        const value = row[headers.get(name) ?? -1]?.trim()
        if (value) return value
      }
      return ''
    }
    const orders = (await purchaseApi.list()).filter((order) => (order.businessScope || 'VEHICLE') === 'VEHICLE')
    const orderByNo = new Map(orders.flatMap((order) => [[order.externalOrderNo || '', order], [order.orderNo, order]] as const)
      .filter(([no]) => Boolean(no)))
    const warehouseByName = new Map(warehouseOptions.value.map((item) => [item.label.replace(/（.*/, ''), item.value]))
    const supplierByText = new Map(suppliers.value.flatMap((item) =>
      [[item.name.toLowerCase(), item], [item.code.toLowerCase(), item]] as const))
    const seen = new Set<string>()
    const rowsToImport: ImportRow[] = []
    const errors: string[] = []
    for (let index = 1; index < csv.length; index += 1) {
      const row = csv[index]
      const orderNo = column(row, '订单编号')
      const supplierText = column(row, '供应商')
      const productText = column(row, '车系/商品', '商品', '车系')
      const configText = column(row, '配置')
      const vin = column(row, 'VIN/SN').toUpperCase()
      if (!orderNo && !productText && !vin) continue
      if (!orderNo || !productText || !vin) { errors.push(`第${index + 1}行缺少订单编号、车系/商品或 VIN/SN`); continue }
      if (seen.has(vin)) { errors.push(`第${index + 1}行 VIN/SN 重复：${vin}`); continue }
      seen.add(vin)
      const order = orderByNo.get(orderNo)
      if (order && supplierText) {
        const supplier = supplierByText.get(supplierText.toLowerCase())
        if (!supplier || supplier.id !== order.supplierId) {
          errors.push(`第${index + 1}行供应商与已有采购订单不一致`)
          continue
        }
      }
      let productCandidates = products.value.filter((item) =>
        [item.productNo, item.name, item.series, item.model, item.configCode]
          .some((value) => value != null && value.toLowerCase() === productText.toLowerCase()))
      if (productCandidates.length > 1 && configText) {
        const narrowed = productCandidates.filter((item) => [item.model, item.configCode, item.name].filter(Boolean).includes(configText))
        if (narrowed.length) productCandidates = narrowed
      }
      if (productCandidates.length !== 1) {
        errors.push(`第${index + 1}行无法唯一匹配商品：${productText}${configText ? ` / ${configText}` : ''}`)
        continue
      }
      const product = productCandidates[0]
      const warehouseText = column(row, '收货仓库')
      const warehouseId = warehouseText ? warehouseByName.get(warehouseText)
        : order?.lines.find((line) => line.productId === product.id)?.warehouseId
      if (warehouseText && !warehouseId) { errors.push(`第${index + 1}行收货仓库不存在：${warehouseText}`); continue }
      const priceText = column(row, '采购价', '单价')
      const existingLine = order?.lines.find((line) => line.productId === product.id
        && (!configText || [line.configText, line.configCode].includes(configText)) && Number(line.remainingQuantity) > 0)
      const price = priceText ? Number(priceText) : Number(existingLine?.unitPrice || 0)
      if (!Number.isFinite(price) || price < 0) { errors.push(`第${index + 1}行采购价无效`); continue }
      const certificateDate = column(row, '申请合格证日期') || null
      if (certificateDate && !/^\d{4}-\d{2}-\d{2}$/.test(certificateDate)) {
        errors.push(`第${index + 1}行申请合格证日期格式应为 YYYY-MM-DD`); continue
      }
      rowsToImport.push({ rowIndex: index + 1, orderNo, supplierText, product, configText, vin, price,
        warehouseId, certificateDate })
    }

    const missingOrderRows = rowsToImport.filter((row) => !orderByNo.has(row.orderNo))
    const newOrderGroups = new Map<string, ImportRow[]>()
    for (const row of missingOrderRows) {
      const group = newOrderGroups.get(row.orderNo) || []
      group.push(row)
      newOrderGroups.set(row.orderNo, group)
    }
    for (const [orderNo, groupRows] of newOrderGroups) {
      const supplierIds = new Set<string>()
      for (const row of groupRows) {
        const supplier = row.supplierText ? supplierByText.get(row.supplierText.toLowerCase()) : undefined
        if (supplier) supplierIds.add(supplier.id)
        else if (suppliers.value.length === 1) supplierIds.add(suppliers.value[0].id)
      }
      if (supplierIds.size !== 1) {
        errors.push(`订单 ${orderNo} 在采购单中不存在，且无法唯一确定供应商；请在模板“供应商”列填写供应商名称或编码`)
        continue
      }
      const supplierId = [...supplierIds][0]
      const newLines = new Map<string, { productId: string; quantity: number; unitPrice: number; configText: string; warehouseId?: string }>()
      for (const row of groupRows) {
        const key = `${row.product.id}|${row.price.toFixed(2)}|${row.configText}|${row.warehouseId || ''}`
        const line = newLines.get(key) || { productId: row.product.id, quantity: 0, unitPrice: row.price,
          configText: row.configText, warehouseId: row.warehouseId }
        line.quantity += 1
        newLines.set(key, line)
      }
      let created = await purchaseApi.create({
        supplierId,
        externalOrderNo: orderNo,
        warehouseId: groupRows.find((row) => row.warehouseId)?.warehouseId || null,
        expectedDate: null,
        remark: '整车批量入库联动创建',
        lines: [...newLines.values()].map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          unitPrice: line.unitPrice,
          configText: line.configText || null,
          warehouseId: line.warehouseId || null,
          policyIds: [],
        })),
      })
      created = await purchaseApi.approve(created.id)
      orderByNo.set(orderNo, created)
      orderByNo.set(created.orderNo, created)
    }

    const receiveGroups = new Map<string, Record<string, unknown>[]>()
    for (const row of rowsToImport) {
      const order = orderByNo.get(row.orderNo)
      if (!order) { errors.push(`第${row.rowIndex}行采购订单未创建成功：${row.orderNo}`); continue }
      const orderLine = order.lines.find((line) => line.productId === row.product.id
        && (!row.configText || [line.configText, line.configCode].includes(row.configText))
        && Number(line.remainingQuantity) > 0)
      if (!orderLine) { errors.push(`第${row.rowIndex}行未找到可收货明细：${row.product.name}`); continue }
      const commands = receiveGroups.get(order.id) || []
      commands.push({
        orderLineId: orderLine.id,
        quantity: 1,
        serialNos: [row.vin],
        unitPrice: row.price,
        warehouseId: row.warehouseId || orderLine.warehouseId || null,
        certificateApplicationDate: row.certificateDate,
      })
      receiveGroups.set(order.id, commands)
    }
    let success = 0
    for (const [orderId, commands] of receiveGroups) {
      await purchaseApi.receive(orderId, {
        warehouseId: null, remark: '整车收货批量导入', lines: commands, offsetTxnIds: [],
      })
      success += commands.length
    }
    if (errors.length) message.warning(`已导入 ${success} 台；${errors.length} 项未导入：${errors.slice(0, 3).join('；')}`)
    else message.success(`已批量导入 ${success} 台车辆；不存在的采购订单已自动创建并审核`)
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { importing.value = false }
}
async function reload() {
  try { receipts.value = await purchaseApi.receipts() }
  catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const [warehouses, productRows, supplierRows] = await Promise.all([
      warehouseApi.list(), productApi.list(), supplierApi.list({ supplierType: 'VEHICLE_OEM' }),
    ])
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
    products.value = productRows
    suppliers.value = supplierRows
  } catch { /* ignore */ }
  await reload()
})
</script>
