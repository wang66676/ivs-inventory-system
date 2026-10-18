<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6">
        <a-card :bordered="false"><a-statistic title="在库序列号（整车/电池）" :value="summary?.serialInStock ?? 0" suffix="台" /></a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false"><a-statistic title="数量商品条目" :value="summary?.quantityItems ?? 0" /></a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-statistic title="库存资金占用" :value="summary?.totalAmount ?? 0" :precision="2" prefix="¥" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-statistic title="台账与余额" :value="summary?.consistent ? '一致' : '不一致'" />
          <div class="hint">台账 {{ summary?.ledgerCount ?? 0 }} 条</div>
        </a-card>
      </a-col>
    </a-row>

    <a-alert v-if="summary && !summary.consistent" type="error" show-icon style="margin-top:16px"
             message="台账与余额不一致，请立即排查（存在绕过库存引擎的直接写入）" />

    <a-row :gutter="16" style="margin-top:16px">
      <a-col :span="12">
        <a-card title="库龄分档（按每个商品+仓库最早入库时间）" :bordered="false">
          <a-table :data-source="summary?.ageBuckets ?? []" row-key="label" size="small" :pagination="false">
            <a-table-column title="库龄" data-index="label" :width="140" />
            <a-table-column title="条目数" data-index="serialCount" :width="100" />
            <a-table-column title="金额" :width="140">
              <template #default="{ record }">{{ record.amount }}</template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="按产品大项" :bordered="false">
          <a-table :data-source="summary?.byCategory ?? []" row-key="categoryName" size="small" :pagination="false">
            <a-table-column title="大项" data-index="categoryName" :width="140" />
            <a-table-column title="条目数" data-index="itemCount" :width="100" />
            <a-table-column title="金额" data-index="amount" />
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="按仓库" :bordered="false" style="margin-top:16px">
      <a-table :data-source="summary?.byWarehouse ?? []" row-key="warehouseName" size="small" :pagination="false">
        <a-table-column title="仓库" data-index="warehouseName" :width="200" />
        <a-table-column title="条目数" data-index="itemCount" :width="120" />
        <a-table-column title="金额" data-index="amount" />
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { inventoryApi } from '../../api/inventory'
import type { InventorySummary } from '../../types/inventory'

const summary = ref<InventorySummary | null>(null)

onMounted(async () => {
  try {
    summary.value = await inventoryApi.summary()
  } catch (error) {
    message.error((error as Error).message)
  }
})
</script>

<style scoped>
.hint { color: #8b96a8; font-size: 12px; margin-top: 4px; }
</style>