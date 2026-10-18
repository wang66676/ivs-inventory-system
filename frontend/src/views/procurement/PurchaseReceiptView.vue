<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="收货单：一次收货一条。整车/电池按 VIN / 电池 SN 逐台记录单车采购价并自动建档入库；数量商品记录数量与批次；金额进入后续应付与结算。" />
      <a-space style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="false">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="商品" :width="260">
              <template #default="{ record: line }">{{ line.productName }}（{{ line.productNo }}）</template>
            </a-table-column>
            <a-table-column title="数量" data-index="quantity" :width="110" />
            <a-table-column title="单价" data-index="unitPrice" :width="120" />
            <a-table-column title="批次" :width="120">
              <template #default="{ record: line }">{{ line.batchNo ?? '-' }}</template>
            </a-table-column>
            <a-table-column title="VIN / SN">
              <template #default="{ record: line }">
                <span v-if="!line.serialNos?.length">-</span>
                <a-tag v-for="serial in line.serialNos" :key="serial" style="margin-bottom:4px">{{ serial }}</a-tag>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'POSTED' ? 'green' : 'orange'">
              {{ record.status === 'POSTED' ? '已入库' : '待入库' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'receivedAt'">
            {{ String(record.receivedAt).replace('T', ' ').slice(0, 19) }}
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { purchaseApi } from '../../api/procurement'
import type { PurchaseReceipt } from '../../types/procurement'

const rows = ref<PurchaseReceipt[]>([])

const columns = [
  { title: '收货单号', key: 'receiptNo', dataIndex: 'receiptNo', width: 150 },
  { title: '采购单号', key: 'orderNo', dataIndex: 'orderNo', width: 150 },
  { title: '供应商', key: 'supplierName', dataIndex: 'supplierName', width: 200 },
  { title: '仓库', key: 'warehouseName', dataIndex: 'warehouseName', width: 140 },
  { title: '金额', key: 'amount', dataIndex: 'amount', width: 140 },
  { title: '收货时间', key: 'receivedAt', width: 180 },
  { title: '状态', key: 'status', width: 110 },
]

async function reload() {
  try {
    rows.value = await purchaseApi.receipts()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(reload)
</script>
