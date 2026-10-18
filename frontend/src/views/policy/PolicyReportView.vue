<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="政策报表口径：应收按权益面值减已兑现；单车成本按订单/序列号落到政策科目；成本率 = 政策成本 / 同期间同车型实销收入。" />
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="periodCode" placeholder="期间，如 2026-10" allow-clear style="width:190px" @press-enter="reload" />
      </a-space>
      <a-tabs v-model:activeKey="tab" @change="reload">
        <a-tab-pane key="aging" tab="返利应收账龄">
          <a-table :data-source="aging" :columns="agingColumns" :row-key="agingKey" size="middle" :pagination="{ pageSize: 15 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'period'">{{ record.periodCode }}</template>
              <template v-else-if="column.key === 'policy'">{{ record.policyName || record.policyId }}</template>
              <template v-else-if="column.key === 'outstanding'">{{ money(record.outstandingAmount) }}</template>
              <template v-else-if="column.key === 'face'">{{ money(record.faceAmount) }}</template>
              <template v-else-if="column.key === 'settled'">{{ money(record.settledAmount) }}</template>
              <template v-else-if="column.key === 'bucket'"><a-tag :color="record.bucket === '90+' ? 'red' : record.bucket === '61-90' ? 'orange' : 'green'">{{ record.bucket }} 天</a-tag></template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="rate" tab="政策成本率">
          <a-table :data-source="rates" :columns="rateColumns" :row-key="rateKey" size="middle" :pagination="{ pageSize: 15 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'cost'">{{ money(record.policyCost) }}</template>
              <template v-else-if="column.key === 'revenue'">{{ money(record.salesRevenue) }}</template>
              <template v-else-if="column.key === 'rate'"><a-tag :color="Number(record.costRate) > 5 ? 'red' : 'blue'">{{ Number(record.costRate).toFixed(2) }}%</a-tag></template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="unit" tab="单车政策成本">
          <a-table :data-source="unitCosts" :columns="unitColumns" row-key="entitlementId" size="small"
                   :pagination="{ pageSize: 15 }" :scroll="{ x: 1350 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'sale'">{{ money(record.salePrice) }}</template>
              <template v-else-if="column.key === 'cost'">{{ money(record.policyCost) }}</template>
              <template v-else-if="column.key === 'net'">{{ money(record.netPrice) }}</template>
              <template v-else-if="column.key === 'status'"><a-tag>{{ record.status }}</a-tag></template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="differences" tab="申报差异">
          <a-table :data-source="differences" :columns="differenceColumns" :row-key="differenceKey" size="middle" :pagination="{ pageSize: 15 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'amount'">{{ money(record.amount) }}</template>
              <template v-else-if="column.key === 'disposition'">{{ DISPOSITION_LABELS[record.disposition] || record.disposition }}</template>
              <template v-else-if="column.key === 'status'"><a-tag :color="record.status === 'RESOLVED' ? 'green' : 'orange'">{{ record.status }}</a-tag></template>
              <template v-else-if="column.key === 'age'"><a-tag :color="record.ageDays > 30 ? 'red' : 'default'">{{ record.ageDays }} 天</a-tag></template>
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
  ClaimDifferenceRow, CostRateRow, ReceivableAgingRow, UnitCostRow,
} from '../../types/policy'

const tab = ref('aging')
const periodCode = ref('')
const aging = ref<ReceivableAgingRow[]>([])
const rates = ref<CostRateRow[]>([])
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
const rateColumns = [
  { title: '期间', key: 'periodCode', dataIndex: 'periodCode', width: 110 },
  { title: '商品编码', key: 'productNo', dataIndex: 'productNo', width: 130 },
  { title: '商品名称', key: 'productName', dataIndex: 'productName', width: 220 },
  { title: '政策成本', key: 'cost', width: 150 },
  { title: '销售收入', key: 'revenue', width: 160 },
  { title: '成本率', key: 'rate', width: 100 },
]
const unitColumns = [
  { title: '期间', key: 'periodCode', dataIndex: 'periodCode', width: 100 },
  { title: '权益编号', key: 'entitlementNo', dataIndex: 'entitlementNo', width: 140 },
  { title: '实销单', key: 'retailNo', dataIndex: 'retailNo', width: 150 },
  { title: 'VIN / SN', key: 'serialNo', dataIndex: 'serialNo', width: 180 },
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 200 },
  { title: '政策', key: 'policyName', dataIndex: 'policyName', width: 220 },
  { title: '成交价', key: 'sale', width: 130 },
  { title: '政策成本', key: 'cost', width: 130 },
  { title: '净车价', key: 'net', width: 130 },
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

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }

function agingKey(row: ReceivableAgingRow) { return [row.supplierId, row.policyId, row.periodCode].join('|') }
function rateKey(row: CostRateRow) { return [row.periodCode, row.productId].join('|') }
function differenceKey(row: ClaimDifferenceRow) { return row.id }
async function reload() {
  try {
    if (tab.value === 'aging') aging.value = await policyApi.reportAging({ periodCode: periodCode.value || undefined })
    else if (tab.value === 'rate') rates.value = await policyApi.reportCostRate({ periodCode: periodCode.value || undefined })
    else if (tab.value === 'unit') unitCosts.value = await policyApi.reportUnitCost({ periodCode: periodCode.value || undefined })
    else differences.value = await policyApi.reportClaimDifferences()
  } catch (error) { message.error((error as Error).message) }
}

onMounted(reload)
</script>


