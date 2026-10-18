<template>
  <MasterDataCrudView
    title="客户"
    description="客户档案：个人 / 单位 / 大客户（车队）。信用额度供 M3 销售与收款环节使用；编码留空自动生成 CUS-000001。"
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
import { customerApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { CUSTOMER_TYPE_LABELS } from '../../types/masterdata'
import type { Customer } from '../../types/masterdata'

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')

const columns: CrudColumn[] = [
  { key: 'code', title: '编码', width: 120 },
  { key: 'name', title: '客户名称', width: 240 },
  { key: 'customerType', title: '类型', width: 130, render: (row) => CUSTOMER_TYPE_LABELS[(row as unknown as Customer).customerType] },
  { key: 'contactName', title: '联系人', width: 110 },
  { key: 'contactPhone', title: '联系电话', width: 140 },
  { key: 'creditLimit', title: '信用额度', width: 130, render: (row) => String((row as unknown as Customer).creditLimit ?? 0) },
]

const fields: CrudField[] = [
  { key: 'code', label: '编码（留空自动生成）' },
  { key: 'name', label: '客户名称', required: true },
  {
    key: 'customerType', label: '客户类型', type: 'select', required: true,
    options: Object.entries(CUSTOMER_TYPE_LABELS).map(([value, label]) => ({ value, label })),
  },
  { key: 'contactName', label: '联系人' },
  { key: 'contactPhone', label: '联系电话' },
  { key: 'taxNo', label: '税号' },
  { key: 'creditLimit', label: '信用额度', type: 'number' },
  { key: 'remark', label: '备注', type: 'textarea' },
]

const load = () => customerApi.list() as unknown as Promise<Record<string, unknown>[]>
const create = (values: Record<string, unknown>) => customerApi.create(values)
const update = (id: string, values: Record<string, unknown>) => customerApi.update(id, values)
const toggle = (row: Record<string, unknown>, enable: boolean) =>
  enable ? customerApi.enable(String(row.id)) : customerApi.disable(String(row.id))
</script>