<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="出库单：一次出库一条。出库即写库存台账（SALE_OUT）、结转成本并生成应收；毛利 = 出库金额 − 出库成本（成本来自库存台账结转）。" />
      <a-space style="margin-bottom:12px"><a-button @click="reload">刷新</a-button></a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="false">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="商品" :width="260">
              <template #default="{ record: line }">{{ line.productName }}（{{ line.productNo }}）</template>
            </a-table-column>
            <a-table-column title="数量" data-index="quantity" :width="100" />
            <a-table-column title="成交价" data-index="unitPrice" :width="120" />
            <a-table-column title="结转成本" data-index="costAmount" :width="130" />
            <a-table-column title="序列号" :width="220">
              <template #default="{ record: line }">
                <a-tag v-for="id in line.serialIds" :key="id">{{ shortId(id) }}</a-tag>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'deliveredAt'">
            {{ String(record.deliveredAt).replace('T', ' ').slice(0, 19) }}
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
import type { SalesDelivery } from '../../types/sales'

const rows = ref<SalesDelivery[]>([])
const columns = [
  { title: '出库单号', key: 'deliveryNo', dataIndex: 'deliveryNo', width: 150 },
  { title: '订单号', key: 'orderNo', dataIndex: 'orderNo', width: 150 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 220 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 130 },
  { title: '金额', key: 'amount', dataIndex: 'amount', width: 140 },
  { title: '成本', key: 'costAmount', dataIndex: 'costAmount', width: 140 },
  { title: '毛利', key: 'grossProfit', dataIndex: 'grossProfit', width: 130 },
  { title: '出库时间', key: 'deliveredAt', width: 180 },
]

const shortId = (id: string) => id.slice(0, 8)

async function reload() {
  try {
    rows.value = await salesApi.deliveries()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(reload)
</script>