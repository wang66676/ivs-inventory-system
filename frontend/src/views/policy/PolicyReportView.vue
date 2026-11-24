<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="政策补贴是厂家返给经销商的返利，通过进项红字发票冲减应付厂家款。本报表不引用销售发票、成交价或销售收入。" />
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="periodCode" placeholder="期间，如 2026-10" allow-clear style="width:190px"
                 @press-enter="reload" />
      </a-space>
      <a-tabs v-model:activeKey="tab" @change="reload">
        <a-tab-pane key="aging" tab="厂家返利应收账龄">
          <a-table :data-source="aging" :columns="agingColumns" :row-key="agingKey" size="middle"
                   :pagination="{ pageSize: 15 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'period'">{{ record.periodCode }}</template>
              <template v-else-if="column.key === 'policy'">{{ record.policyName || record.policyId }}</template>
              <template v-else-if="column.key === 'outstanding'">{{ money(record.outstandingAmount) }}</template>
              <template v-else-if="column.key === 'face'">{{ money(record.faceAmount) }}</template>
              <template v-else-if="column.key === 'settled'">{{ money(record.settledAmount) }}</template>
              <template v-else-if="column.key === 'bucket'">
                <a-tag :color="record.bucket === '90+' ? 'red' : record.bucket === '61-90' ? 'orange' : 'green'">
                  {{ record.bucket }} 天
                </a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="summary" tab="厂家返利汇总">
          <a-table :data-source="summaries" :columns="summaryColumns" row-key="summaryKey" size="middle"
                   :pagination="{ pageSize: 15 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'face'">{{ money(record.faceAmount) }}</template>
              <template v-else-if="column.key === 'claimed'">{{ money(record.claimedAmount) }}</template>
              <template v-else-if="column.key === 'confirmed'">{{ money(record.confirmedAmount) }}</template>
              <template v-else-if="column.key === 'settled'">{{ money(record.settledAmount) }}</template>
              <template v-else-if="column.key === 'outstanding'">{{ money(record.outstandingAmount) }}</template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="unit" tab="单车厂家返利">
          <a-table :data-source="unitCosts" :columns="unitColumns" row-key="entitlementId" size="small"
                   :pagination="{ pageSize: 15 }" :scroll="{ x: 1580 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'invoiceIssuedAt'">{{ formatDate(record.invoiceIssuedAt) }}</template>
              <template v-else-if="column.key === 'claimed'">{{ money(record.claimedAmount) }}</template>
              <template v-else-if="column.key === 'confirmed'">{{ money(record.confirmedAmount) }}</template>
              <template v-else-if="column.key === 'settled'">{{ money(record.settledAmount) }}</template>
              <template v-else-if="column.key === 'status'"><a-tag>{{ record.status }}</a-tag></template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="differences" tab="申报差异">
          <a-table :data-source="differences" :columns="differenceColumns" :row-key="differenceKey" size="middle"
                   :pagination="{ pageSize: 15 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'amount'">{{ money(record.amount) }}</template>
              <template v-else-if="column.key === 'disposition'">
                {{ DISPOSITION_LABELS[record.disposition] || record.disposition }}
              </template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="record.status === 'RESOLVED' ? 'green' : 'orange'">{{ record.status }}</a-tag>
              </template>
              <template v-else-if="column.key === 'age'">
                <a-tag :color="record.ageDays > 30 ? 'red' : 'default'">{{ record.ageDays }} 天</a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { DISPOSITION_LABELS } from '../../types/policy'
import type {
  ClaimDifferenceRow, RebateSummaryRow, ReceivableAgingRow, UnitCostRow,
} from '../../types/policy'

const tab = ref('aging')
const periodCode = ref('')
const aging = ref<ReceivableAgingRow[]>([])
const summaries = ref<RebateSummaryRow[]>([])
const unitCosts = ref<UnitCostRow[]>([])
const differences = ref<ClaimDifferenceRow[]>([])

const agingColumns = [
  { title: '厂家', key: 'supplierName', dataIndex: 'supplierName', width: 180 },
  { title: '政策', key: 'policy', width: 220 },
  { title: '期间', key: 'period', width: 110 },
  { title: '权益数', key: 'count', dataIndex: 'entitlementCount', width: 90 },
  { title: '应返面值', key: 'face', width: 140 },
  { title: '已兑现', key: 'settled', width: 140 },
  { title: '未到账', key: 'outstanding', width: 140 },
  { title: '账龄', key: 'bucket', width: 100 },
]
const summaryColumns = [
  { title: '期间', key: 'periodCode', dataIndex: 'periodCode', width: 110 },
  { title: '厂家', key: 'supplierName', dataIndex: 'supplierName', width: 190 },
  { title: '政策名称', key: 'policyName', dataIndex: 'policyName', width: 220 },
  { title: '权益数', key: 'count', dataIndex: 'entitlementCount', width: 90 },
  { title: '返利面值', key: 'face', width: 140 },
  { title: '已申请', key: 'claimed', width: 140 },
  { title: '厂家确认', key: 'confirmed', width: 140 },
  { title: '已兑现', key: 'settled', width: 140 },
  { title: '待兑现', key: 'outstanding', width: 140 },
]
const unitColumns = [
  { title: '期间', key: 'periodCode', dataIndex: 'periodCode', width: 100 },
  { title: '采购订单编号', key: 'purchaseOrderNo', dataIndex: 'purchaseOrderNo', width: 160 },
  { title: '终端开票日期', key: 'invoiceIssuedAt', width: 130 },
  { title: 'VIN / SN', key: 'serialNo', dataIndex: 'serialNo', width: 180 },
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 200 },
  { title: '政策', key: 'policyName', dataIndex: 'policyName', width: 200 },
  { title: '申报金额', key: 'claimed', width: 130 },
  { title: '厂家确认返利', key: 'confirmed', width: 150 },
  { title: '已兑现', key: 'settled', width: 130 },
  { title: '申报单号', key: 'claimNo', dataIndex: 'claimNo', width: 150 },
  { title: '红字发票', key: 'redInvoiceNo', dataIndex: 'redInvoiceNo', width: 150 },
  { title: '状态', key: 'status', width: 100 },
]
const differenceColumns = [
  { title: '申报单号', key: 'claimNo', dataIndex: 'claimNo', width: 150 },
  { title: '厂家', key: 'supplierName', dataIndex: 'supplierName', width: 170 },
  { title: '权益编号', key: 'entitlementNo', dataIndex: 'entitlementNo', width: 150 },
  { title: '差异额', key: 'amount', width: 130 },
  { title: '原因', key: 'reason', dataIndex: 'reason', width: 260 },
  { title: '处置', key: 'disposition', width: 130 },
  { title: '状态', key: 'status', width: 100 },
  { title: '账龄', key: 'age', width: 100 },
]

function money(value?: number) {
  return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '-' }
function agingKey(row: ReceivableAgingRow) { return [row.supplierId, row.policyId, row.periodCode].join('|') }
function summaryKey(row: RebateSummaryRow) { return [row.periodCode, row.supplierId, row.policyId].join('|') }
function differenceKey(row: ClaimDifferenceRow) { return row.id }

async function reload() {
  try {
    if (tab.value === 'aging') {
      aging.value = await policyApi.reportAging({ periodCode: periodCode.value || undefined })
    } else if (tab.value === 'summary') {
      summaries.value = await policyApi.reportRebateSummary({ periodCode: periodCode.value || undefined })
    } else if (tab.value === 'unit') {
      unitCosts.value = await policyApi.reportUnitCost({ periodCode: periodCode.value || undefined })
    } else {
      differences.value = await policyApi.reportClaimDifferences()
    }
  } catch (error) { message.error((error as Error).message) }
}

onMounted(reload)
</script>
