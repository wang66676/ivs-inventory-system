<template>
  <div>
    <a-row :gutter="16">
      <a-col v-for="bucket in aging" :key="bucket.label" :span="6">
        <a-card :bordered="false">
          <a-statistic :title="bucket.label" :value="bucket.amount" :precision="2" prefix="¥" />
          <div class="hint">{{ bucket.count }} 笔</div>
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" style="margin-top:16px">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="应收：按出库单生成，到期日 = 出库日 + 客户账期（默认 30 天）。收款按到期日 FIFO 自动核销；核销完并完成交付后订单自动关闭（款货两清）。" />
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="customerId" :options="customerOptions" allow-clear show-search
                  placeholder="按客户筛选" style="width:240px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15 }" :scroll="{ x: 1100 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'dueDate'">
            {{ record.dueDate ?? '-' }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { salesApi } from '../../api/sales'
import { customerApi } from '../../api/masterdata'
import { RECEIVABLE_STATUS_COLORS, RECEIVABLE_STATUS_LABELS } from '../../types/sales'
import type { AgingBucket, Receivable, ReceivableStatus } from '../../types/sales'

const rows = ref<Receivable[]>([])
const aging = ref<AgingBucket[]>([])
const customerId = ref<string | undefined>(undefined)
const customerOptions = ref<{ label: string; value: string }[]>([])

const columns = [
  { title: '应收编号', key: 'receivableNo', dataIndex: 'receivableNo', width: 140 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 220 },
  { title: '订单号', key: 'orderNo', dataIndex: 'orderNo', width: 150 },
  { title: '应收金额', key: 'amount', dataIndex: 'amount', width: 130 },
  { title: '已收', key: 'receivedAmount', dataIndex: 'receivedAmount', width: 130 },
  { title: '未收', key: 'outstanding', dataIndex: 'outstanding', width: 130 },
  { title: '到期日', key: 'dueDate', width: 120 },
  { title: '状态', key: 'status', width: 110 },
]

const statusLabel = (status: string) => RECEIVABLE_STATUS_LABELS[status as ReceivableStatus]
const statusColor = (status: string) => RECEIVABLE_STATUS_COLORS[status as ReceivableStatus]

async function reload() {
  try {
    rows.value = await salesApi.receivables({ customerId: customerId.value })
    aging.value = await salesApi.aging()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  try {
    const customers = await customerApi.list()
    customerOptions.value = customers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; margin-top: 4px; }
</style>