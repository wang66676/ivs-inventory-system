<template>
  <Table v-bind="$attrs" :pagination="normalizedPagination">
    <template v-for="(_, name) in $slots" #[name]="slotProps">
      <slot :name="name" v-bind="slotProps || {}" />
    </template>
  </Table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Table } from 'ant-design-vue'

defineOptions({ name: 'GlobalTable', inheritAttrs: false })

const props = defineProps<{
  pagination?: false | Record<string, any>
}>()


const normalizedPagination = computed<Record<string, any>>(() => {
  const source = props.pagination && typeof props.pagination === 'object' ? props.pagination : {}
  // 全站表格默认每页 10 条；用户仍可通过分页器切换 20/50 条。
  const defaultPageSize = 10
  const { pageSize: _ignoredPageSize, ...rest } = source
  return {
    ...rest,
    defaultPageSize,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '50'],
    showTotal: source.showTotal || ((total: number, range: [number, number]) =>
      `第 ${range[0]}-${range[1]} 条 / 共 ${total} 条`),
  }
})
</script>
