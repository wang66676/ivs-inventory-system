<template>
  <MasterDataCrudView
    title="供应商"
    :description="description"
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
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { SUPPLIER_TYPE_LABELS } from '../../types/masterdata'
import type { Supplier } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')
const description = '供应商档案。整车厂标记为 OEM，是返利申报、红字发票与返利资金池的主体；编码留空自动生成 SUP-000001。'

const columns: CrudColumn[] = [
  { key: 'code', title: '编码', width: 120 },
  { key: 'name', title: '名称', width: 220 },
  { key: 'supplierType', title: '类型', width: 140, render: (row) => SUPPLIER_TYPE_LABELS[(row as unknown as Supplier).supplierType] },
  { key: 'contactName', title: '联系人', width: 110 },
  { key: 'contactPhone', title: '联系电话', width: 140 },
  { key: 'paymentTerms', title: '结算条款', width: 140 },
]

const fields: CrudField[] = [
  { key: 'code', label: '编码（留空自动生成）' },
  { key: 'name', label: '供应商名称', required: true },
  {
    key: 'supplierType', label: '供应商类型', type: 'select', required: true,
    options: Object.entries(SUPPLIER_TYPE_LABELS).map(([value, label]) => ({ value, label })),
  },
  { key: 'contactName', label: '联系人' },
  { key: 'contactPhone', label: '联系电话' },
  { key: 'taxNo', label: '税号' },
  { key: 'paymentTerms', label: '结算条款' },
  { key: 'remark', label: '备注', type: 'textarea' },
]

const load = () => supplierApi.list() as unknown as Promise<Record<string, unknown>[]>
const create = (values: Record<string, unknown>) => supplierApi.create(values)
const update = (id: string, values: Record<string, unknown>) => supplierApi.update(id, values)
const toggle = (row: Record<string, unknown>, enable: boolean) =>
  enable ? supplierApi.enable(String(row.id)) : supplierApi.disable(String(row.id))
</script>