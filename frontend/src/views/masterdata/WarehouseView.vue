<template>
  <MasterDataCrudView
    title="仓库"
    description="仓库 / 门店仓 / 在途仓 / 资产池（二期电池租赁使用）。仓库是库存引擎的最小维度。"
    :can-write="canWrite"
    :columns="columns"
    :fields="fields"
    :load="load"
    :create="create"
    :update="update"
    :toggle="toggle"
  />
</template>

<script setup lang="ts">
import MasterDataCrudView, { type CrudColumn, type CrudField } from './MasterDataCrudView.vue'
import { warehouseApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { WAREHOUSE_TYPE_LABELS } from '../../types/masterdata'
import type { Warehouse } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')

const columns: CrudColumn[] = [
  { key: 'code', title: '编码', width: 120 },
  { key: 'name', title: '名称', width: 200 },
  { key: 'type', title: '类型', width: 120, render: (row) => WAREHOUSE_TYPE_LABELS[(row as unknown as Warehouse).type] },
  { key: 'address', title: '地址', width: 260 },
]

const fields: CrudField[] = [
  { key: 'code', label: '仓库编码', required: true, placeholder: '如 WH-MAIN' },
  { key: 'name', label: '仓库名称', required: true },
  {
    key: 'type', label: '仓库类型', type: 'select',
    options: Object.entries(WAREHOUSE_TYPE_LABELS).map(([value, label]) => ({ value, label })),
  },
  { key: 'address', label: '地址' },
]

const load = () => warehouseApi.list() as unknown as Promise<Record<string, unknown>[]>
const create = (values: Record<string, unknown>) => warehouseApi.create(values)
const update = (id: string, values: Record<string, unknown>) => warehouseApi.update(id, values)
const toggle = (row: Record<string, unknown>, enable: boolean) =>
  enable ? warehouseApi.enable(String(row.id)) : warehouseApi.disable(String(row.id))
</script>