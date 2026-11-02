<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="价目表给出成交价与最低限价，是「政策之外」的定价基础。取价规则：优先命中「适用客户类型 + 生效期内」的价目表，未命中回落到商品指导价；低于最低限价成交在 M3 触发审批。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canWrite" type="primary" @click="openCreate">新增价目表</a-button>
        <a-button @click="reload">刷新</a-button>
        <span class="hint">共 {{ rows.length }} 张</span>
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'customerType'">
            {{ customerTypeLabel(record.customerType) }}
          </template>
          <template v-else-if="column.key === 'period'">
            {{ (record.effectiveFrom ?? '不限') + ' ~ ' + (record.effectiveTo ?? '不限') }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a @click="openLines(record)">明细（{{ record.lineCount }}）</a>
              <a v-if="canWrite" @click="openEdit(record)">编辑</a>
              <a v-if="canWrite && record.status === 'ACTIVE'" @click="toggle(record, false)">停用</a>
              <a v-else-if="canWrite" @click="toggle(record, true)">启用</a>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalOpen" :title="(editing ? '编辑' : '新增') + '价目表'" :confirm-loading="saving"
             ok-text="保存" cancel-text="取消" width="620px" @ok="submit">
      <a-form layout="vertical">
        <a-form-item label="编码（留空自动生成 PL-000001）"><a-input v-model:value="form.code" :disabled="!!editing" /></a-form-item>
        <a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="适用客户类型（留空为通用）">
          <a-select v-model:value="form.customerType" :options="customerTypeOptions" allow-clear />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="生效日期"><a-date-picker v-model:value="form.effectiveFrom" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="失效日期"><a-date-picker v-model:value="form.effectiveTo" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="lineModalOpen" :title="`价目表明细 - ${current?.name ?? ''}`" :footer="null" width="820px">
      <a-table :data-source="current?.lines ?? []" row-key="id" size="small" :pagination="false"
               :locale="{ emptyText: '暂无明细' }">
        <a-table-column title="商品编码" data-index="productNo" :width="130" />
        <a-table-column title="商品名称" data-index="productName" :width="200" />
        <a-table-column title="成交价" data-index="price" :width="120" />
        <a-table-column title="最低限价" :width="120">
          <template #default="{ record }">{{ record.minPrice ?? '-' }}</template>
        </a-table-column>
        <a-table-column title="操作" :width="80">
          <template #default="{ record }">
            <a v-if="canWrite" @click="removeLine(record)">删除</a>
          </template>
        </a-table-column>
      </a-table>
      <a-divider style="margin:16px 0" />
      <a-space wrap>
        <a-select v-model:value="lineForm.productId" :options="productOptions" show-search placeholder="选择商品"
                  style="width:260px" />
        <MoneyInput v-model:value="lineForm.price" :min="0" :precision="2" placeholder="成交价" style="width:160px" />
        <MoneyInput v-model:value="lineForm.minPrice" :min="0" :precision="2" placeholder="最低限价" style="width:160px" />
        <a-button v-if="canWrite" type="primary" @click="saveLine">添加 / 更新</a-button>
      </a-space>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { priceListApi, productApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import { CUSTOMER_TYPE_LABELS } from '../../types/masterdata'
import type { CustomerType, PriceList, PriceListLine, Product } from '../../types/masterdata'
const customerTypeLabel = (type?: string) => (type ? CUSTOMER_TYPE_LABELS[type as CustomerType] : '通用')

const auth = useAuthStore()
const canWrite = auth.can('IVS_PRODUCT_MANAGE')

const rows = ref<PriceList[]>([])
const products = ref<Product[]>([])
const modalOpen = ref(false)
const lineModalOpen = ref(false)
const saving = ref(false)
const editing = ref<PriceList | null>(null)
const current = ref<PriceList | null>(null)
const form = reactive<Record<string, any>>({})
const lineForm = reactive<Record<string, any>>({})

const columns = [
  { title: '编码', key: 'code', dataIndex: 'code', width: 130 },
  { title: '名称', key: 'name', dataIndex: 'name', width: 220 },
  { title: '适用客户', key: 'customerType', width: 130 },
  { title: '有效期', key: 'period', width: 210 },
  { title: '明细数', key: 'lineCount', dataIndex: 'lineCount', width: 90 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'actions', width: 220 },
]

const customerTypeOptions = Object.entries(CUSTOMER_TYPE_LABELS).map(([value, label]) => ({ value, label }))
const productOptions = ref<{ label: string; value: string }[]>([])

async function reload() {
  try {
    rows.value = await priceListApi.list()
    if (current.value) {
      current.value = rows.value.find((item) => item.id === current.value?.id) ?? null
    }
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openCreate() {
  editing.value = null
  Object.keys(form).forEach((key) => delete form[key])
  modalOpen.value = true
}

function openEdit(record: PriceList) {
  editing.value = record
  Object.assign(form, {
    code: record.code, name: record.name, customerType: record.customerType,
    effectiveFrom: record.effectiveFrom, effectiveTo: record.effectiveTo, remark: record.remark,
  })
  modalOpen.value = true
}

async function submit() {
  if (!form.name) { message.warning('请填写名称'); return }
  saving.value = true
  try {
    if (editing.value) await priceListApi.update(editing.value.id, form)
    else await priceListApi.create(form)
    message.success('已保存')
    modalOpen.value = false
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function toggle(record: PriceList, enable: boolean) {
  try {
    if (enable) await priceListApi.enable(record.id)
    else await priceListApi.disable(record.id)
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openLines(record: PriceList) {
  current.value = record
  Object.keys(lineForm).forEach((key) => delete lineForm[key])
  lineModalOpen.value = true
}

async function saveLine() {
  if (!current.value) return
  if (!lineForm.productId || lineForm.price == null) { message.warning('请选择商品并填写成交价'); return }
  try {
    await priceListApi.saveLine(current.value.id, { ...lineForm })
    message.success('已保存明细')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function removeLine(line: PriceListLine) {
  if (!current.value) return
  try {
    await priceListApi.removeLine(current.value.id, line.id)
    message.success('已删除')
    await reload()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  try {
    const list = await productApi.list()
    products.value = list
    productOptions.value = list.map((item) => ({ label: `${item.name}（${item.productNo}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { color: #96a0b3; font-size: 12px; }
</style>
