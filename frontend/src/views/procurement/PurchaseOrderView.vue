<template>
  <div class="purchase-order-page">
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px" :message="scopeHint" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新建采购单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-button @click="downloadTemplate">模板下载</a-button>
        <a-button @click="exportOrders">导出采购单</a-button>
        <a-select v-model:value="filterStatus" :options="statusOptions" allow-clear placeholder="按状态筛选"
                  style="width:170px" @change="reload" />
        <a-input v-model:value="keyword" placeholder="订单编号" allow-clear style="width:220px" @press-enter="reload" />
      </a-space>
      <a-table v-resizable-columns :data-source="purchaseRows" :columns="columns" row-key="rowKey" size="middle"
               :pagination="tablePagination" :scroll="{ x: 2260 }" @change="onTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orderNo'">{{ record.order.externalOrderNo || record.order.orderNo }}</template>
          <template v-else-if="column.key === 'series'">{{ record.line.series || record.line.productName || '-' }}</template>
          <template v-else-if="column.key === 'model'">{{ modelOf(record.line.productId) }}</template>
          <template v-else-if="column.key === 'config'">{{ record.line.configText || record.line.configCode || '-' }}</template>
          <template v-else-if="column.key === 'arrivalDate'">{{ record.line.arrivalDate || '-' }}</template>
          <template v-else-if="column.key === 'quantity'">{{ record.line.quantity }}</template>
          <template v-else-if="column.key === 'unitPrice'">{{ money(record.line.unitPrice) }}</template>
          <template v-else-if="column.key === 'batteryBoxManufacturer'">{{ record.line.batteryBoxManufacturer || '-' }}</template>
          <template v-else-if="column.key === 'batteryBoxModel'">{{ record.line.batteryBoxModel || '-' }}</template>
          <template v-else-if="column.key === 'batteryBoxUnitPrice'">{{ money(record.line.batteryBoxUnitPrice) }}</template>
          <template v-else-if="column.key === 'batteryPackManufacturer'">{{ record.line.batteryPackManufacturer || '-' }}</template>
          <template v-else-if="column.key === 'batteryEnergyKwh'">{{ record.line.batteryEnergyKwh ?? '-' }}</template>
          <template v-else-if="column.key === 'batteryPackUnitPrice'">{{ money(record.line.batteryPackUnitPrice) }}</template>
          <template v-else-if="column.key === 'amount'"><b>{{ money(record.line.amount) }}</b></template>
          <template v-else-if="column.key === 'policies'">
            <a-space v-if="record.line.policies?.length" wrap size="small">
              <a-tag v-for="policy in record.line.policies" :key="policy.id" color="blue">{{ policy.name }}</a-tag>
            </a-space>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'warehouse'">{{ record.line.warehouseName || record.order.warehouseName || '收货时确认' }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space v-if="record.isFirstLine">
              <a v-if="canWrite && record.order.status === 'DRAFT'" @click="openEdit(record.order)">编辑</a>
              <a v-if="canWrite && record.order.status === 'DRAFT'" @click="approve(record.order)">审核</a>
              <a v-if="canWrite && (record.order.status === 'APPROVED' || record.order.status === 'PARTIALLY_RECEIVED')"
                 @click="openReceive(record.order)">收货</a>
              <a v-if="canWrite && (record.order.status === 'DRAFT' || record.order.status === 'APPROVED')" @click="cancel(record.order)">取消</a>
            </a-space>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="createOpen" :title="editingId ? '编辑采购单' : '新建采购单'" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="1650px" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }"
             @ok="submit">
      <a-form layout="vertical">
        <a-row :gutter="12">
          <a-col :span="8">
            <a-form-item label="供应商" required>
              <a-select v-model:value="form.supplierId" :options="supplierOptions" show-search @change="onSupplierChange" />
            </a-form-item>
          </a-col>
          <a-col v-if="props.scope === 'BATTERY'" :span="8">
            <a-form-item label="订单编号" required><a-input v-model:value="form.externalOrderNo" placeholder="电池采购订单编号" /></a-form-item>
          </a-col>
          <a-col v-else :span="8">
            <a-form-item label="预计到货"><a-date-picker v-model:value="form.expectedDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item>
          </a-col>
          <a-col :span="8"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
        <a-space wrap style="margin-bottom:10px">
          <a-button @click="downloadTemplate">下载导入模板</a-button>
          <a-button :disabled="!form.supplierId" @click="triggerUpload">批量上传</a-button>
          <a-button @click="exportCurrent">导出当前明细</a-button>
          <span class="hint">CSV 同时兼容车辆与电池字段；电池按电池箱、电池包分项计价，返利政策留空</span>
          <input ref="fileInput" type="file" accept=".csv,text/csv" style="display:none" @change="onFileChange" />
        </a-space>
        <a-divider orientation="left" style="font-size:13px">采购明细</a-divider>
        <a-table v-if="props.scope === 'BATTERY'" :data-source="form.lines" row-key="_key" size="small"
                 :pagination="false" :scroll="{ x: 1670 }">
          <a-table-column title="商品" :width="190">
            <template #default="{ record: line }">
              <a-select v-model:value="line.productId" :options="seriesOptions" show-search placeholder="选择电池商品"
                        option-filter-prop="label" style="width:100%" @change="() => onProductChange(line)" />
            </template>
          </a-table-column>
          <a-table-column title="到货时间" :width="150">
            <template #default="{ record: line }"><a-date-picker v-model:value="line.arrivalDate" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" /></template>
          </a-table-column>
          <a-table-column title="电池箱制造商" :width="170"><template #default="{ record: line }"><a-input v-model:value="line.batteryBoxManufacturer" placeholder="电池箱制造商" /></template></a-table-column>
          <a-table-column title="电池箱型号" :width="140"><template #default="{ record: line }"><a-input v-model:value="line.batteryBoxModel" placeholder="电池箱型号" /></template></a-table-column>
          <a-table-column title="电池箱单价" :width="140"><template #default="{ record: line }"><MoneyInput v-model:value="line.batteryBoxUnitPrice" :min="0" :precision="2" style="width:100%" /></template></a-table-column>
          <a-table-column title="电池包制造商" :width="170"><template #default="{ record: line }"><a-input v-model:value="line.batteryPackManufacturer" placeholder="电池包制造商" /></template></a-table-column>
          <a-table-column title="能量（kWh）" :width="130"><template #default="{ record: line }"><a-input-number v-model:value="line.batteryEnergyKwh" :min="0.001" :precision="3" style="width:100%" /></template></a-table-column>
          <a-table-column title="电池包单价" :width="140"><template #default="{ record: line }"><MoneyInput v-model:value="line.batteryPackUnitPrice" :min="0" :precision="2" style="width:100%" /></template></a-table-column>
          <a-table-column title="数量" :width="90"><template #default="{ record: line }"><a-input-number v-model:value="line.quantity" :min="0.001" style="width:100%" /></template></a-table-column>
          <a-table-column title="合计单价" :width="140"><template #default="{ record: line }">{{ money(unitPriceOf(line)) }}</template></a-table-column>
          <a-table-column title="合计总价" :width="130" align="right"><template #default="{ record: line }"><b>{{ money(lineTotal(line)) }}</b></template></a-table-column>
          <a-table-column title="操作" :width="90" align="center">
            <template #default="{ record: line }">
              <a-button danger size="small" :disabled="form.lines.length <= 1" @click="removeLine(line._key)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>
        <a-table v-else :data-source="form.lines" row-key="_key" size="small" :pagination="false" :scroll="{ x: 1310 }">
          <a-table-column title="订单编号" :width="140"><template #default="{ record: line }"><a-input v-model:value="line.orderNo" placeholder="订单编号" /></template></a-table-column>
          <a-table-column title="车系" :width="110">
            <template #default="{ record: line }">
              <a-select v-model:value="line.productId" :options="seriesOptions" show-search placeholder="车系"
                        option-filter-prop="label" style="width:100%" @change="() => onProductChange(line)" />
            </template>
          </a-table-column>
          <a-table-column title="型号" :width="120"><template #default="{ record: line }">{{ modelOf(line.productId) }}</template></a-table-column>
          <a-table-column title="配置" :width="140"><template #default="{ record: line }"><a-input v-model:value="line.configText" placeholder="人工输入配置" /></template></a-table-column>
          <a-table-column title="数量" :width="70"><template #default="{ record: line }"><a-input-number v-model:value="line.quantity" :min="0.001" style="width:100%" /></template></a-table-column>
          <a-table-column title="单价" :width="150"><template #default="{ record: line }"><MoneyInput v-model:value="line.unitPrice" :min="0" :precision="2" style="width:100%" /></template></a-table-column>
          <a-table-column title="总计车款" :width="110" align="right"><template #default="{ record: line }"><b>{{ money(lineTotal(line)) }}</b></template></a-table-column>
          <a-table-column title="返利政策（可多选）" :width="230">
            <template #default="{ record: line }">
              <a-select v-model:value="line.policyIds" mode="multiple" :options="policyOptionsForSupplier"
                        :disabled="!form.supplierId" allow-clear show-search option-filter-prop="label"
                        :not-found-content="form.supplierId ? '该供应商暂无已发布返利政策' : '请先选择供应商'"
                        :placeholder="form.supplierId ? '可后补' : '请先选择供应商'" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="收货仓库" :width="150">
            <template #default="{ record: line }">
              <a-select v-model:value="line.warehouseId" :options="warehouseOptions" allow-clear placeholder="可后补" style="width:100%" />
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="90" align="center">
            <template #default="{ record: line }">
              <a-button danger size="small" :disabled="form.lines.length <= 1" @click="removeLine(line._key)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>
        <a-button type="dashed" block style="margin-top:10px" @click="addLine">+ 增加采购明细</a-button>
      </a-form>
    </a-modal>

    <a-modal v-model:open="receiveOpen" :title="`收货 - ${current?.externalOrderNo || current?.orderNo || ''}`"
             :confirm-loading="saving" :ok-button-props="{ disabled: offsetSelectionBlocked }"
             ok-text="确认收货" cancel-text="取消" width="1180px" @ok="submitReceive">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               :message="props.scope === 'BATTERY' ? '动力电池统一收货到天津容大仓库，无需填写仓库。按电池 SN 逐块录入。' : 'VIN 在收货时逐台录入，收货入库会按 VIN 生成独立明细并关联采购明细。仓库可按实际发运情况逐台指定。'" />
      <a-form layout="vertical">
        <a-form-item v-if="props.scope !== 'BATTERY'" label="默认收货仓库（可选）">
          <a-select v-model:value="receiveForm.warehouseId" :options="warehouseOptions" allow-clear />
        </a-form-item>
        <template v-if="props.scope !== 'BATTERY' && canOffset">
          <a-divider orientation="left" style="font-size:13px">返利抵扣</a-divider>
          <a-spin :spinning="offsetLoading">
            <a-descriptions bordered size="small" :column="4" style="margin-bottom:12px">
              <a-descriptions-item label="本单金额">{{ money(receiveAmount) }}</a-descriptions-item>
              <a-descriptions-item label="可用返利">{{ money(availableRebate) }}</a-descriptions-item>
              <a-descriptions-item label="本次抵扣">{{ money(offsetAppliedAmount) }}</a-descriptions-item>
              <a-descriptions-item label="抵扣后应付">{{ money(netPayable) }}</a-descriptions-item>
            </a-descriptions>
            <a-alert v-if="availableRebate <= 0" type="info" show-icon style="margin-bottom:10px"
                     message="该供应商当前没有已到账返利，本次收货不抵扣。" />
            <template v-else>
              <div class="rebate-selection-head">
                <a-checkbox :checked="allCreditsSelected" :indeterminate="someCreditsSelected" @change="toggleAllCredits">
                  全选可用返利来源
                </a-checkbox>
                <span class="hint">已选返利可用金额：{{ money(selectedCreditAmount) }}</span>
              </div>
              <a-table :data-source="offsetPreview?.credits ?? []" row-key="txnId" size="small"
                       :pagination="false" :scroll="{ x: 760 }" style="margin-bottom:12px">
                <a-table-column title="选择" :width="70" align="center">
                  <template #default="{ record }">
                    <a-checkbox :checked="selectedOffsetTxnIds.includes(record.txnId)"
                                @change="toggleOffsetCredit(record.txnId, $event.target.checked)" />
                  </template>
                </a-table-column>
                <a-table-column title="返利来源类型" :width="150">
                  <template #default="{ record }">{{ record.sourceType || '-' }}</template>
                </a-table-column>
                <a-table-column title="返利来源单号" :width="220">
                  <template #default="{ record }">{{ record.sourceNo || '-' }}</template>
                </a-table-column>
                <a-table-column title="剩余可用金额" :width="160" align="right">
                  <template #default="{ record }">{{ money(record.remainingAmount) }}</template>
                </a-table-column>
                <a-table-column title="入池时间" :width="180">
                  <template #default="{ record }">{{ formatDateTime(record.occurredAt) }}</template>
                </a-table-column>
              </a-table>
              <a-alert v-if="offsetSelectionBlocked" type="warning" show-icon style="margin-bottom:12px"
                       message="所选返利来源的剩余金额不足，请继续勾选或取消本次抵扣。" />
            </template>
          </a-spin>
        </template>
        <a-divider orientation="left" style="font-size:13px">收货明细</a-divider>
        <a-table :data-source="current?.lines ?? []" row-key="id" size="small" :pagination="false"
                 :scroll="{ x: 1360 }" :locale="{ emptyText: '无明细' }">
          <a-table-column title="车系" :width="180"><template #default="{ record }">{{ record.series || '-' }}</template></a-table-column>
          <a-table-column title="电池信息" :width="360"><template #default="{ record }">{{ batteryText(record) }}</template></a-table-column>
          <a-table-column title="待收" data-index="remainingQuantity" :width="80" />
          <a-table-column title="本次收货" :width="620">
            <template #default="{ record }">
              <a-textarea v-if="record.trackingMode === 'SERIAL'" v-model:value="receiveInput[record.id].serialRows"
                          :rows="3" :disabled="Number(record.remainingQuantity) === 0"
                          :placeholder="receivePlaceholder" />
              <a-space v-else wrap>
                <a-input-number v-model:value="receiveInput[record.id].quantity" :min="0"
                                :max="Number(record.remainingQuantity)" placeholder="本次数量" style="width:130px" />
                <a-select v-if="props.scope !== 'BATTERY'" v-model:value="receiveInput[record.id].warehouseId" :options="warehouseOptions"
                          placeholder="收货仓库" style="width:210px" allow-clear />
                <span v-else class="hint">固定仓库：天津容大</span>
              </a-space>
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
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import { purchaseApi } from '../../api/procurement'
import { productApi, supplierApi, warehouseApi } from '../../api/masterdata'
import { policyApi } from '../../api/policy'
import { useAuthStore } from '../../stores/auth'
import { PURCHASE_STATUS_COLORS, PURCHASE_STATUS_LABELS } from '../../types/procurement'
import type { PurchaseOrder, PurchaseOrderLine, PurchaseOrderStatus } from '../../types/procurement'
import type { FundOffsetPreview, Policy } from '../../types/policy'
import type { BusinessScope } from '../../utils/businessScope'

const props = withDefaults(defineProps<{ scope?: BusinessScope }>(), { scope: 'VEHICLE' })
const scopeHint = computed(() => props.scope === 'BATTERY'
  ? '电池采购独立入口：按电池箱 + 电池包分项计价，电池无返利政策，不允许与整车混单。'
  : '整车采购入口：车型、配置、数量、单价、返利政策和收货仓库保持原流程，不允许与动力电池混单。')
const receivePlaceholder = computed(() => props.scope === 'BATTERY'
  ? '每行：电池SN,单块采购价,申请合格证日期\n例如：BAT-SN-001,300000,2026-11-05'
  : '每行：VIN,单车采购价,收货仓库,申请合格证日期\n例如：LZZ1...20260001,420000,主仓,2026-10-21')

const statusLabel = (status: string) => PURCHASE_STATUS_LABELS[status as PurchaseOrderStatus]
const statusColor = (status: string) => PURCHASE_STATUS_COLORS[status as PurchaseOrderStatus]
const auth = useAuthStore()
const canWrite = auth.can('IVS_WRITE')
const canOffset = auth.can('IVS_FUND_OFFSET')
const rows = ref<PurchaseOrder[]>([])
const keyword = ref('')
const filterStatus = ref<string | undefined>(undefined)
const tableCurrent = ref(1)
const tablePageSize = ref(20)
const tablePagination = computed(() => ({
  current: tableCurrent.value,
  pageSize: tablePageSize.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条采购明细`,
}))
function onTableChange(pagination: { current?: number; pageSize?: number }) {
  tableCurrent.value = pagination.current || 1
  tablePageSize.value = pagination.pageSize || 20
}
const createOpen = ref(false)
const receiveOpen = ref(false)
const saving = ref(false)
const editingId = ref<string>()
const current = ref<PurchaseOrder | null>(null)
const fileInput = ref<HTMLInputElement>()
const supplierOptions = ref<{ label: string; value: string }[]>([])
const warehouseOptions = ref<{ label: string; value: string; name: string; code: string }[]>([])
const productOptions = ref<{ label: string; value: string; productNo: string; name: string; series?: string; model?: string; categoryCode?: string; categoryName?: string; isBattery: boolean }[]>([])
const policies = ref<Policy[]>([])
interface PurchaseListRow {
  rowKey: string
  order: PurchaseOrder
  line: PurchaseOrderLine
  isFirstLine: boolean
}
const form = reactive<Record<string, any>>({ lines: [newLine()] })
const receiveForm = reactive<Record<string, any>>({})
const receiveInput = reactive<Record<string, { serialRows?: string; quantity?: number; warehouseId?: string }>>({})
const offsetLoading = ref(false)
const offsetPreview = ref<FundOffsetPreview>()
const selectedOffsetTxnIds = ref<string[]>([])
const receiveAmount = computed(() => {
  let total = 0
  for (const line of current.value?.lines ?? []) {
    const input = receiveInput[line.id]
    if (!input) continue
    if (line.trackingMode === 'SERIAL') {
      for (const row of String(input.serialRows ?? '').split('\n').map((item) => item.trim()).filter(Boolean)) {
        const parts = row.split(/[,，\t]/).map((item) => item.trim())
        if (!parts[0]) continue
        const price = parts[1] ? Number(parts[1]) : Number(line.unitPrice)
        if (Number.isFinite(price) && price >= 0) total += price
      }
    } else {
      total += Number(input.quantity ?? 0) * Number(line.unitPrice ?? 0)
    }
  }
  return roundMoney(total)
})
const availableRebate = computed(() => Number(offsetPreview.value?.availableBalance ?? 0))
const selectedCreditAmount = computed(() => roundMoney((offsetPreview.value?.credits ?? [])
  .filter((item) => selectedOffsetTxnIds.value.includes(item.txnId))
  .reduce((sum, item) => sum + Number(item.remainingAmount ?? 0), 0)))
const offsetTargetAmount = computed(() => selectedOffsetTxnIds.value.length
  ? roundMoney(Math.min(availableRebate.value, receiveAmount.value))
  : 0)
const offsetSelectionReady = computed(() => selectedOffsetTxnIds.value.length === 0
  || selectedCreditAmount.value >= offsetTargetAmount.value)
const offsetAppliedAmount = computed(() => offsetSelectionReady.value ? offsetTargetAmount.value : 0)
const netPayable = computed(() => roundMoney(receiveAmount.value - offsetAppliedAmount.value))
const offsetSelectionBlocked = computed(() => offsetTargetAmount.value > 0 && !offsetSelectionReady.value)
const allCreditsSelected = computed(() => {
  const credits = offsetPreview.value?.credits ?? []
  return credits.length > 0 && selectedOffsetTxnIds.value.length === credits.length
})
const someCreditsSelected = computed(() => selectedOffsetTxnIds.value.length > 0 && !allCreditsSelected.value)
const purchaseRows = computed<PurchaseListRow[]>(() => rows.value.flatMap((order) => order.lines.map((line, index) => ({
  rowKey: `${order.id}-${line.id}`,
  order,
  line,
  isFirstLine: index === 0,
}))))
function compareText(left?: string | number | null, right?: string | number | null) {
  return String(left ?? '').localeCompare(String(right ?? ''), 'zh-CN', { numeric: true })
}
function compareNumber(left?: number | null, right?: number | null) {
  return Number(left ?? 0) - Number(right ?? 0)
}
const orderNoOf = (row: PurchaseListRow) => row.order.externalOrderNo || row.order.orderNo
const seriesOfRow = (row: PurchaseListRow) => row.line.series || row.line.productName || ''
const configOfRow = (row: PurchaseListRow) => row.line.configText || row.line.configCode || ''
const warehouseOfRow = (row: PurchaseListRow) => row.line.warehouseName || row.order.warehouseName || ''
const policiesOfRow = (row: PurchaseListRow) => (row.line.policies ?? []).map((policy) => policy.name).join('|')
const vehicleColumns = [
  { title: '订单编号', key: 'orderNo', width: 150, ellipsis: true, fixed: 'left', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(orderNoOf(a), orderNoOf(b)) },
  { title: '车系', key: 'series', width: 120, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(seriesOfRow(a), seriesOfRow(b)) },
  { title: '型号', key: 'model', width: 120, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(modelOf(a.line.productId), modelOf(b.line.productId)) },
  { title: '配置', key: 'config', width: 140, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(configOfRow(a), configOfRow(b)) },
  { title: '数量', key: 'quantity', width: 80, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.quantity, b.line.quantity) },
  { title: '单价', key: 'unitPrice', width: 110, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.unitPrice, b.line.unitPrice) },
  { title: '总计车款', key: 'amount', width: 140, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.amount, b.line.amount) },
  { title: '返利政策', key: 'policies', width: 240, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(policiesOfRow(a), policiesOfRow(b)) },
  { title: '收货仓库', key: 'warehouse', width: 150, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(warehouseOfRow(a), warehouseOfRow(b)) },
  { title: '操作', key: 'actions', width: 300, fixed: 'right' },
]
const batteryColumns = [
  { title: '订单编号', key: 'orderNo', width: 150, ellipsis: true, fixed: 'left', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(orderNoOf(a), orderNoOf(b)) },
  { title: '商品', key: 'series', width: 170, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(seriesOfRow(a), seriesOfRow(b)) },
  { title: '到货时间', key: 'arrivalDate', width: 120, align: 'center', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(a.line.arrivalDate, b.line.arrivalDate) },
  { title: '电池箱制造商', key: 'batteryBoxManufacturer', width: 160, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(a.line.batteryBoxManufacturer, b.line.batteryBoxManufacturer) },
  { title: '电池箱型号', key: 'batteryBoxModel', width: 130, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(a.line.batteryBoxModel, b.line.batteryBoxModel) },
  { title: '电池箱单价', key: 'batteryBoxUnitPrice', width: 120, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.batteryBoxUnitPrice, b.line.batteryBoxUnitPrice) },
  { title: '电池包制造商', key: 'batteryPackManufacturer', width: 160, ellipsis: true, sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareText(a.line.batteryPackManufacturer, b.line.batteryPackManufacturer) },
  { title: '能量（kWh）', key: 'batteryEnergyKwh', width: 110, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.batteryEnergyKwh, b.line.batteryEnergyKwh) },
  { title: '电池包单价', key: 'batteryPackUnitPrice', width: 120, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.batteryPackUnitPrice, b.line.batteryPackUnitPrice) },
  { title: '数量', key: 'quantity', width: 80, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.quantity, b.line.quantity) },
  { title: '合计单价', key: 'unitPrice', width: 120, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.unitPrice, b.line.unitPrice) },
  { title: '合计总价', key: 'amount', width: 140, align: 'right', sorter: (a: PurchaseListRow, b: PurchaseListRow) => compareNumber(a.line.amount, b.line.amount) },
  { title: '操作', key: 'actions', width: 120, fixed: 'right' },
]
const columns = computed(() => props.scope === 'BATTERY' ? batteryColumns : vehicleColumns)
const statusOptions = Object.entries(PURCHASE_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const scopedProductOptions = computed(() => productOptions.value.filter((item) =>
  item.isBattery === (props.scope === 'BATTERY')))
const seriesOptions = computed(() => {
  const bySeries = new Map<string, string>()
  for (const item of scopedProductOptions.value) {
    const series = item.isBattery ? item.name : (item.series || item.name)
    if (series && !bySeries.has(series)) bySeries.set(series, item.value)
  }
  return [...bySeries.entries()].sort(([a], [b]) => a.localeCompare(b, 'zh-CN'))
    .map(([series, productId]) => ({ label: series, value: productId }))
})
const policyOptionsForSupplier = computed(() => policies.value
  .filter((policy) => policy.supplierId === form.supplierId)
  .map((policy) => ({ label: `${policy.policyNo} ${policy.name}`, value: policy.id })))
function newLine() {
  return { _key: `line-${Date.now()}-${Math.random()}`, orderNo: '', productId: undefined,
    configText: '', quantity: 1, unitPrice: undefined, policyIds: [] as string[], warehouseId: undefined,
    batteryBoxManufacturer: '', batteryBoxModel: '', batteryBoxUnitPrice: undefined,
    batteryPackManufacturer: '', batteryEnergyKwh: undefined, batteryPackUnitPrice: undefined,
    arrivalDate: undefined }
}
function modelOf(productId?: string) { return productOptions.value.find((item) => item.value === productId)?.model || '-' }
function isBatteryCategory(categoryCode?: string, categoryName?: string) {
  const code = (categoryCode || '').toUpperCase()
  return code === 'BATTERY' || code.startsWith('BATTERY-') || (categoryName || '').includes('电池')
}
function isBatteryLine(line: Record<string, any>) {
  return productOptions.value.find((item) => item.value === line.productId)?.isBattery === true
}
function unitPriceOf(line: Record<string, any>) {
  if (!isBatteryLine(line)) return Number(line.unitPrice ?? 0)
  return Number(line.batteryBoxUnitPrice ?? 0) + Number(line.batteryPackUnitPrice ?? 0)
}
function onProductChange(line: Record<string, any>) {
  if (isBatteryLine(line)) {
    line.policyIds = []
    line.configText = ''
  } else {
    line.batteryBoxManufacturer = ''
    line.batteryBoxModel = ''
    line.batteryBoxUnitPrice = undefined
    line.batteryPackManufacturer = ''
    line.batteryEnergyKwh = undefined
    line.batteryPackUnitPrice = undefined
  }
}
function addLine() { form.lines.push(newLine()) }
function removeLine(key: string) {
  if (form.lines.length <= 1) return
  const index = (form.lines as Record<string, any>[]).findIndex((line) => line._key === key)
  if (index >= 0) form.lines.splice(index, 1)
}
function lineTotal(line: Record<string, any>) { return Number(line.quantity ?? 0) * unitPriceOf(line) }
function money(value?: number | null) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function roundMoney(value: number) { return Math.round((Number(value) + Number.EPSILON) * 100) / 100 }
function formatDateTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function toggleOffsetCredit(txnId: string, checked: boolean) {
  selectedOffsetTxnIds.value = checked
    ? [...new Set([...selectedOffsetTxnIds.value, txnId])]
    : selectedOffsetTxnIds.value.filter((id) => id !== txnId)
}
function toggleAllCredits(event: { target: { checked: boolean } }) {
  selectedOffsetTxnIds.value = event.target.checked
    ? (offsetPreview.value?.credits ?? []).map((item) => item.txnId)
    : []
}
function seriesOf(productId?: string) { const item = productOptions.value.find((option) => option.value === productId); return item?.series || item?.name || '' }
function warehouseLabel(id?: string) { return warehouseOptions.value.find((item) => item.value === id)?.name || '' }
function batteryText(line: PurchaseOrderLine) {
  if (!line.batteryBoxManufacturer && !line.batteryPackManufacturer) return '-'
  return `箱：${line.batteryBoxManufacturer || '-'} / ${line.batteryBoxModel || '-'}；包：${line.batteryPackManufacturer || '-'} / ${line.batteryEnergyKwh ?? '-'}kWh`
}
function policyNames(ids: string[]) { return ids.map((id) => policies.value.find((policy) => policy.id === id)?.name).filter(Boolean).join('|') }
function onSupplierChange() {
  const allowed = new Set(policyOptionsForSupplier.value.map((item) => item.value))
  for (const line of form.lines as Record<string, any>[]) line.policyIds = (line.policyIds ?? []).filter((id: string) => allowed.has(id))
}
async function reload() {
  try {
    const result = await purchaseApi.list({ keyword: keyword.value || undefined, status: filterStatus.value })
    rows.value = result.filter((order) => (order.businessScope || 'VEHICLE') === props.scope)
    tableCurrent.value = 1
  }
  catch (error) { message.error((error as Error).message) }
}
function resetForm() { Object.keys(form).forEach((key) => delete form[key]); form.lines = [newLine()] }
function openCreate() { editingId.value = undefined; resetForm(); createOpen.value = true }
function openEdit(record: PurchaseOrder) {
  editingId.value = record.id; resetForm(); form.supplierId = record.supplierId
  form.expectedDate = record.expectedDate; form.remark = record.remark
  form.externalOrderNo = record.externalOrderNo || record.orderNo
  form.lines = record.lines.map((line) => ({ _key: line.id, orderNo: record.externalOrderNo || record.orderNo,
    productId: line.productId, configText: line.configText || line.configCode || '',
    quantity: line.quantity, unitPrice: line.unitPrice, policyIds: line.policies.map((policy) => policy.id),
    warehouseId: line.warehouseId, batteryBoxManufacturer: line.batteryBoxManufacturer || '',
    batteryBoxModel: line.batteryBoxModel || '', batteryBoxUnitPrice: line.batteryBoxUnitPrice,
    batteryPackManufacturer: line.batteryPackManufacturer || '', batteryEnergyKwh: line.batteryEnergyKwh,
    batteryPackUnitPrice: line.batteryPackUnitPrice, arrivalDate: line.arrivalDate }))
  createOpen.value = true
}
function resolveWarehouse(value: string) {
  const key = value.trim().toLowerCase()
  return warehouseOptions.value.find((item) => item.value.toLowerCase() === key)
    || warehouseOptions.value.find((item) => item.name.toLowerCase() === key)
    || warehouseOptions.value.find((item) => item.code.toLowerCase() === key)
}
function resolveProduct(series: string) {
  const key = series.trim().toLowerCase()
  return scopedProductOptions.value.find((item) => [item.series, item.name, item.productNo]
    .filter(Boolean).some((value) => String(value).toLowerCase() === key))
}
function parseCsv(text: string): string[][] {
  const source = text.replace(/^\uFEFF/, ''); const result: string[][] = []; let row: string[] = []; let cell = ''; let quoted = false
  for (let index = 0; index < source.length; index += 1) {
    const char = source[index]
    if (quoted) { if (char === '"' && source[index + 1] === '"') { cell += '"'; index += 1 } else if (char === '"') quoted = false; else cell += char }
    else if (char === '"') quoted = true; else if (char === ',') { row.push(cell); cell = '' }
    else if (char === '\n') { row.push(cell); result.push(row); row = []; cell = '' } else if (char !== '\r') cell += char
  }
  if (cell || row.length) { row.push(cell); result.push(row) }
  return result.filter((item) => item.some((value) => value.trim()))
}
function field(row: string[], headers: Map<string, number>, ...names: string[]) {
  for (const name of names) { const index = headers.get(name); if (index != null) return (row[index] ?? '').trim() }
  return ''
}
async function importCsv(file: File) {
  if (!form.supplierId) { message.warning('请先选择供应商，再批量上传'); return }
  try {
    const csvRows = parseCsv(await file.text()); if (csvRows.length < 2) throw new Error('CSV 中没有采购明细')
    const headers = new Map(csvRows[0].map((header, index) => [header.trim(), index])); const imported: Record<string, any>[] = []
    for (let index = 1; index < csvRows.length; index += 1) {
      const row = csvRows[index]; const scopedBattery = props.scope === 'BATTERY'
      const orderNo = field(row, headers, '订单编号', '订单号') || (scopedBattery ? String(form.externalOrderNo || '').trim() : '')
      const series = field(row, headers, '商品 / 车系', '商品', '车系'); const product = resolveProduct(series)
      const quantity = Number(field(row, headers, '数量'))
      if (!orderNo) throw new Error(`第 ${index + 1} 行缺少订单编号`)
      if (!product) throw new Error(`第 ${index + 1} 行无法匹配商品/车系：${series}`)
      if (!Number.isFinite(quantity) || quantity <= 0) throw new Error(`第 ${index + 1} 行数量无效`)
      const battery = scopedBattery
      const boxUnitPrice = Number(field(row, headers, '电池箱单价'))
      const packUnitPrice = Number(field(row, headers, '电池包单价'))
      const unitPrice = battery ? boxUnitPrice + packUnitPrice : Number(field(row, headers, '合计单价', '单价'))
      if (!Number.isFinite(unitPrice) || unitPrice < 0) throw new Error(`第 ${index + 1} 行单价无效`)
      if (battery) {
        if (!field(row, headers, '电池箱制造商') || !field(row, headers, '电池箱型号') || !field(row, headers, '电池包制造商')) {
          throw new Error(`第 ${index + 1} 行电池分项信息不完整`)
        }
        if (!(Number(field(row, headers, '能量（kWh）', '能量(kWh)', '能量')) > 0)) {
          throw new Error(`第 ${index + 1} 行电池能量无效`)
        }
        if (!Number.isFinite(boxUnitPrice) || boxUnitPrice < 0 || !Number.isFinite(packUnitPrice) || packUnitPrice < 0) {
          throw new Error(`第 ${index + 1} 行电池箱/电池包单价无效`)
        }
        const arrivalDate = field(row, headers, '到货时间')
        if (!arrivalDate || !/^\d{4}-\d{2}-\d{2}$/.test(arrivalDate) || !dayjs(arrivalDate).isValid()) {
          throw new Error(`第 ${index + 1} 行到货时间格式应为 YYYY-MM-DD`)
        }
      }
      const warehouseText = battery ? '' : field(row, headers, '收货仓库'); const warehouse = warehouseText ? resolveWarehouse(warehouseText) : undefined
      if (warehouseText && !warehouse) throw new Error(`第 ${index + 1} 行仓库无法匹配：${warehouseText}`)
      const policyText = battery ? '' : field(row, headers, '返利政策'); const policyIds: string[] = []
      if (policyText) for (const token of policyText.split(/[|；;]/).map((item) => item.trim()).filter(Boolean)) {
        const found = policyOptionsForSupplier.value.find((option) => { const policy = policies.value.find((item) => item.id === option.value)
          return policy && (policy.name === token || policy.policyNo === token || option.label === token) })
        if (!found) throw new Error(`第 ${index + 1} 行返利政策无法匹配：${token}`)
        policyIds.push(found.value)
      }
      imported.push({ _key: `line-${Date.now()}-${index}-${Math.random()}`, orderNo,
        productId: product.value, configText: battery ? '' : field(row, headers, '配置'), quantity, unitPrice, policyIds,
        warehouseId: battery ? undefined : warehouse?.value, batteryBoxManufacturer: battery ? field(row, headers, '电池箱制造商') : '',
        batteryBoxModel: battery ? field(row, headers, '电池箱型号') : '', batteryBoxUnitPrice: battery ? boxUnitPrice : undefined,
        batteryPackManufacturer: battery ? field(row, headers, '电池包制造商') : '',
        batteryEnergyKwh: battery ? Number(field(row, headers, '能量（kWh）', '能量(kWh)', '能量')) : undefined,
        batteryPackUnitPrice: battery ? packUnitPrice : undefined,
        arrivalDate: battery ? field(row, headers, '到货时间') : undefined })
    }
    form.lines = imported; message.success(`已导入 ${imported.length} 条采购明细`)
  } catch (error) { message.error((error as Error).message) }
}
function triggerUpload() { fileInput.value?.click() }
async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (file) await importCsv(file); input.value = ''
}
function csvValue(value: unknown) { return `"${String(value ?? '').replace(/"/g, '""')}"` }
function downloadCsv(filename: string, dataRows: unknown[][]) {
  const text = dataRows.map((row) => row.map(csvValue).join(',')).join('\r\n'); const blob = new Blob([`\uFEFF${text}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url)
}
function downloadTemplate() {
  if (props.scope === 'BATTERY') {
    const headers = ['商品', '到货时间', '电池箱制造商', '电池箱型号', '电池箱单价', '电池包制造商',
      '能量（kWh）', '电池包单价', '数量', '合计单价', '合计总价']
    downloadCsv('电池采购导入模板.csv', [headers,
      ['282kWh 动力电池', '2026-11-10', '电池箱厂A', 'BOX-282', 60000, '电池包厂B', 282, 180000, 1, 240000, 240000]])
    return
  }
  downloadCsv('整车采购导入模板.csv', [
    ['订单编号', '车系', '型号', '配置', '数量', '单价', '总计车款', '返利政策', '收货仓库'],
    ['PO-2026-001', '示例车系', '6x4', '人工输入配置', 2, 420000, 840000, '政策A|政策B', '主仓'],
  ])
}
function exportCurrent() {
  if (props.scope === 'BATTERY') {
    const headers = ['商品', '到货时间', '电池箱制造商', '电池箱型号', '电池箱单价', '电池包制造商',
      '能量（kWh）', '电池包单价', '数量', '合计单价', '合计总价']
    const data = (form.lines as Record<string, any>[]).map((line) => [seriesOf(line.productId), line.arrivalDate || '',
      line.batteryBoxManufacturer || '', line.batteryBoxModel || '', line.batteryBoxUnitPrice ?? '',
      line.batteryPackManufacturer || '', line.batteryEnergyKwh ?? '', line.batteryPackUnitPrice ?? '',
      line.quantity, unitPriceOf(line), lineTotal(line)])
    downloadCsv('电池采购明细.csv', [headers, ...data])
    return
  }
  const data = (form.lines as Record<string, any>[]).map((line) => [line.orderNo, seriesOf(line.productId),
    modelOf(line.productId), line.configText || '', line.quantity, line.unitPrice, lineTotal(line),
    policyNames(line.policyIds ?? []), warehouseLabel(line.warehouseId)])
  downloadCsv('整车采购明细.csv', [['订单编号', '车系', '型号', '配置', '数量', '单价', '总计车款', '返利政策', '收货仓库'], ...data])
}
function exportOrders() {
  if (props.scope === 'BATTERY') {
    const headers = ['订单编号', '商品', '到货时间', '电池箱制造商', '电池箱型号', '电池箱单价',
      '电池包制造商', '能量（kWh）', '电池包单价', '数量', '合计单价', '合计总价']
    const data: unknown[][] = []
    for (const order of rows.value) for (const line of order.lines) data.push([
      order.externalOrderNo || order.orderNo, line.productName, line.arrivalDate || '', line.batteryBoxManufacturer || '',
      line.batteryBoxModel || '', line.batteryBoxUnitPrice ?? '', line.batteryPackManufacturer || '',
      line.batteryEnergyKwh ?? '', line.batteryPackUnitPrice ?? '', line.quantity, line.unitPrice, line.amount,
    ])
    downloadCsv('电池采购单导出.csv', [headers, ...data])
    return
  }
  const data: unknown[][] = []
  for (const order of rows.value) for (const line of order.lines) data.push([order.externalOrderNo || order.orderNo,
    line.series || line.productName, modelOf(line.productId), line.configText || line.configCode || '', line.quantity,
    line.unitPrice, line.amount, line.policies.map((policy) => policy.name).join('|'),
    line.warehouseName || order.warehouseName || ''])
  downloadCsv('整车采购单导出.csv', [['订单编号', '车系', '型号', '配置', '数量', '单价', '总计车款', '返利政策', '收货仓库'], ...data])
}
async function submit() {
  if (!form.supplierId) { message.warning('请选择供应商'); return }
  const batteryScope = props.scope === 'BATTERY'
  if (batteryScope && !String(form.externalOrderNo ?? '').trim()) {
    message.warning('请填写订单编号'); return
  }
  const lines = (form.lines as Record<string, any>[]).filter((line) => line.orderNo || line.productId || line.unitPrice != null)
  if (!lines.length) { message.warning('请至少填写一行有效明细'); return }
  for (const line of lines) {
    if (!batteryScope && !String(line.orderNo ?? '').trim()) { message.warning('每条明细都必须填写订单编号'); return }
    if (!line.productId) { message.warning(batteryScope ? '请选择电池商品' : '请选择车系'); return }
    if (!(Number(line.quantity) > 0)) { message.warning('数量必须大于 0'); return }
    if (isBatteryLine(line)) {
      if (!line.arrivalDate) { message.warning('请选择到货时间'); return }
      if (!String(line.batteryBoxManufacturer ?? '').trim() || !String(line.batteryBoxModel ?? '').trim()
          || !String(line.batteryPackManufacturer ?? '').trim()) {
        message.warning('请填写电池箱制造商、电池箱型号和电池包制造商'); return
      }
      if (!(Number(line.batteryEnergyKwh) > 0)) { message.warning('电池能量（kWh）必须大于 0'); return }
      if (!(Number(line.batteryBoxUnitPrice) >= 0) || !(Number(line.batteryPackUnitPrice) >= 0)) {
        message.warning('请填写有效的电池箱单价和电池包单价'); return
      }
      if (!(unitPriceOf(line) > 0)) { message.warning('合计单价必须大于 0'); return }
    } else if (line.unitPrice == null || !(Number(line.unitPrice) >= 0)) {
      message.warning('请填写有效单价'); return
    }
  }
  saving.value = true
  try {
    const bodyFor = (orderLines: Record<string, any>[]) => ({ supplierId: form.supplierId, warehouseId: null,
      expectedDate: batteryScope ? null : form.expectedDate, remark: form.remark,
      lines: orderLines.map((line) => ({ productId: line.productId, quantity: line.quantity,
        unitPrice: unitPriceOf(line), configText: batteryScope ? null : (line.configText || null),
        warehouseId: batteryScope ? null : (line.warehouseId || null),
        policyIds: isBatteryLine(line) ? [] : (line.policyIds ?? []),
        batteryBoxManufacturer: isBatteryLine(line) ? line.batteryBoxManufacturer : null,
        batteryBoxModel: isBatteryLine(line) ? line.batteryBoxModel : null,
        batteryBoxUnitPrice: isBatteryLine(line) ? line.batteryBoxUnitPrice : null,
        batteryPackManufacturer: isBatteryLine(line) ? line.batteryPackManufacturer : null,
        batteryEnergyKwh: isBatteryLine(line) ? line.batteryEnergyKwh : null,
        batteryPackUnitPrice: isBatteryLine(line) ? line.batteryPackUnitPrice : null,
        arrivalDate: batteryScope ? line.arrivalDate : null })) })
    if (batteryScope) {
      const body = { externalOrderNo: String(form.externalOrderNo).trim(), ...bodyFor(lines) }
      if (editingId.value) await purchaseApi.update(editingId.value, body)
      else await purchaseApi.create(body)
      message.success(editingId.value ? '电池采购单已更新' : '电池采购单已创建')
    } else {
      const existingOrderNos = new Set(rows.value
        .filter((order) => order.id !== editingId.value)
        .map((order) => order.externalOrderNo || order.orderNo)
        .filter(Boolean))
      for (const line of lines) {
        const orderNo = String(line.orderNo ?? '').trim()
        if (existingOrderNos.has(orderNo)) {
          message.warning(`订单编号 ${orderNo} 已存在，不能重复新建`)
          return
        }
      }
      const grouped = new Map<string, Record<string, any>[]>()
      for (const line of lines) {
        const orderNo = String(line.orderNo).trim()
        if (!grouped.has(orderNo)) grouped.set(orderNo, [])
        grouped.get(orderNo)!.push(line)
      }
      if (editingId.value) { await purchaseApi.update(editingId.value, { externalOrderNo: [...grouped.keys()][0], ...bodyFor([...grouped.values()][0]) }); message.success('采购单已更新') }
      else { for (const [orderNo, orderLines] of grouped) await purchaseApi.create({ externalOrderNo: orderNo, ...bodyFor(orderLines) }); message.success(`已创建 ${grouped.size} 张采购单`) }
    }
    createOpen.value = false; await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
function approve(record: PurchaseOrder) {
  Modal.confirm({
    title: `审核采购订单 ${record.externalOrderNo || record.orderNo}？`,
    content: props.scope === 'VEHICLE'
      ? '请确认本订单所有整车明细均已选定返利政策。确认后订单将审核通过并进入收货流程。'
      : '确认后订单将审核通过并进入收货流程。',
    okText: '已确认，审核通过',
    cancelText: '返回检查',
    onOk: async () => {
      try {
        await purchaseApi.approve(record.id)
        message.success('采购订单已审核')
        await reload()
      } catch (error) { message.error((error as Error).message) }
    },
  })
}
function cancel(record: PurchaseOrder) {
  Modal.confirm({
    title: `取消采购订单 ${record.externalOrderNo || record.orderNo}？`,
    content: '点击“返回上一步”将保留订单并回到当前列表；只有点击“确认取消订单”才会真正取消。',
    okText: '确认取消订单',
    okType: 'danger',
    cancelText: '返回上一步',
    onOk: async () => {
      try {
        await purchaseApi.cancel(record.id)
        message.success('采购订单已取消')
        await reload()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}
async function openReceive(record: PurchaseOrder) {
  try {
    const latest = await purchaseApi.get(record.id)
    const receivable = latest.status === 'APPROVED' || latest.status === 'PARTIALLY_RECEIVED'
    const hasRemaining = latest.lines.some((line) => Number(line.remainingQuantity) > 0)
    if (!receivable || !hasRemaining) {
      message.info('采购单已全部收货，无需重复收货')
      await reload()
      return
    }
    current.value = latest
    Object.keys(receiveForm).forEach((key) => delete receiveForm[key])
    Object.keys(receiveInput).forEach((key) => delete receiveInput[key])
    receiveForm.warehouseId = latest.warehouseId
    latest.lines.forEach((line) => { receiveInput[line.id] = { warehouseId: line.warehouseId, serialRows: '' } })
    offsetPreview.value = undefined
    selectedOffsetTxnIds.value = []
    receiveOpen.value = true
    if (props.scope !== 'BATTERY' && canOffset) {
      offsetLoading.value = true
      try {
        const preview = await policyApi.fundOffsetPreview({ supplierId: latest.supplierId })
        offsetPreview.value = preview
        selectedOffsetTxnIds.value = preview.credits.map((item) => item.txnId)
      } catch (error) {
        message.warning(`返利余额加载失败：${(error as Error).message}`)
      } finally {
        offsetLoading.value = false
      }
    }
  } catch (error) { message.error((error as Error).message) }
}
async function submitReceive() {
  if (!current.value) return
  const batteryScope = props.scope === 'BATTERY'
  const lines: Record<string, unknown>[] = []; const seenVin = new Set<string>()
  for (const line of current.value.lines) {
    if (Number(line.remainingQuantity) === 0) continue
    const input = receiveInput[line.id] ?? {}
    if (line.trackingMode === 'SERIAL') {
      for (const row of String(input.serialRows ?? '').split('\n').map((item) => item.trim()).filter(Boolean)) {
        const parts = row.split(/[,，\t]/).map((item) => item.trim()); const serialNo = parts[0]?.toUpperCase(); if (!serialNo) continue
        if (seenVin.has(serialNo)) { message.warning('有重复项，请核查后再提交'); return } ; seenVin.add(serialNo)
        const unitPrice = parts[1] ? Number(parts[1]) : Number(line.unitPrice)
        if (!Number.isFinite(unitPrice) || unitPrice < 0) { message.warning(`${batteryScope ? '电池 SN' : 'VIN'} ${serialNo} 的采购价无效`); return }
        let warehouseId: string | undefined
        let certDate: string | undefined
        if (batteryScope) {
          certDate = parts[2] || undefined
        } else {
          const warehouseText = parts[2]; const warehouse = warehouseText ? resolveWarehouse(warehouseText) : undefined
          if (warehouseText && !warehouse) { message.warning(`VIN ${serialNo} 的仓库无法匹配：${warehouseText}`); return }
          warehouseId = warehouse?.value || line.warehouseId || receiveForm.warehouseId
          if (!warehouseId) { message.warning(`VIN ${serialNo} 请指定收货仓库`); return }
          certDate = parts[3] || undefined
        }
        if (certDate && (!/^\d{4}-\d{2}-\d{2}$/.test(certDate) || !dayjs(certDate).isValid())) { message.warning(`${batteryScope ? '电池 SN' : 'VIN'} ${serialNo} 的申请合格证日期格式应为 YYYY-MM-DD`); return }
        lines.push({ orderLineId: line.id, quantity: 1, serialNos: [serialNo], unitPrice,
          warehouseId: warehouseId || null, certificateApplicationDate: certDate || null })
      }
    } else if (input.quantity && Number(input.quantity) > 0) {
      const warehouseId = batteryScope ? null : (input.warehouseId || line.warehouseId || receiveForm.warehouseId)
      if (!batteryScope && !warehouseId) { message.warning(`商品 ${line.productName} 请指定收货仓库`); return }
      lines.push({ orderLineId: line.id, quantity: input.quantity, unitPrice: line.unitPrice, warehouseId })
    }
  }
  if (!lines.length) { message.warning('请填写本次收货内容'); return }
  if (offsetSelectionBlocked.value) { message.warning('所选返利来源剩余金额不足，请继续勾选或取消抵扣'); return }
  const offsetTxnIds = offsetAppliedAmount.value > 0 ? selectedOffsetTxnIds.value : []
  saving.value = true
  try {
    const receipt = await purchaseApi.receive(current.value.id, {
      warehouseId: batteryScope ? null : (receiveForm.warehouseId || null),
      remark: receiveForm.remark,
      lines,
      offsetTxnIds,
    })
    message.success(`收货完成，金额 ${money(receipt.amount)}，返利抵扣 ${money(receipt.offsetAmount || 0)}，应付 ${money(receipt.netAmount ?? receipt.amount)}`)
    receiveOpen.value = false
    await reload()
  } catch (error) {
    const text = (error as Error).message
    message.error(text)
    if (text.includes('已全部收货')) { receiveOpen.value = false; await reload() }
  } finally { saving.value = false }
}
onMounted(async () => {
  try {
    const [suppliers, warehouses, products, publishedPolicies] = await Promise.all([supplierApi.list(), warehouseApi.list(), productApi.list(), policyApi.list({ status: 'PUBLISHED' })])
    supplierOptions.value = suppliers.map((item) => ({ label: `${item.name}${item.supplierType === 'OEM' ? '（整车厂）' : ''}`, value: item.id }))
    warehouseOptions.value = warehouses.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id, name: item.name, code: item.code }))
    productOptions.value = products.map((item) => ({ label: item.name, value: item.id, productNo: item.productNo,
      name: item.name, series: item.series, model: item.model, categoryCode: item.categoryCode,
      categoryName: item.categoryName, isBattery: isBatteryCategory(item.categoryCode, item.categoryName) }))
    policies.value = publishedPolicies
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; }
.rebate-selection-head { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:8px; }
:deep(.ant-table-expanded-row > td) { padding-left: 0 !important; padding-right: 0 !important; }
:deep(.ant-table-expanded-row .ant-table-wrapper) { margin-left: 0 !important; margin-right: 0 !important; }
</style>

<style>
.purchase-order-page .ant-table-expanded-row > td { padding-left: 0 !important; padding-right: 0 !important; }
.purchase-order-page .ant-table-expanded-row .ant-table-wrapper { margin-left: 0 !important; margin-right: 0 !important; }
.purchase-order-page .ant-table-expanded-row .ant-table { margin-left: 0 !important; margin-right: 0 !important; }
</style>
