<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="电池起租后执行 LEASE_OUT 转入租赁资产池，不再计入可售库存；月租计划确认后生成销售应收，可在收款模块按期核销。" />
      <a-row :gutter="12" style="margin-bottom:14px">
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="租赁合同" :value="rows.length" suffix="份" /></a-card></a-col>
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="在租电池" :value="activeCount" suffix="块" /></a-card></a-col>
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="在租月租" :value="monthlyIncome" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :xs="12" :md="6"><a-card size="small"><a-statistic title="逾期未收" :value="overdueAmount" :precision="2" prefix="¥" /></a-card></a-col>
      </a-row>
      <a-space style="margin-bottom:12px">
        <a-button v-if="canManage" type="primary" @click="openCreate">新建租赁合同</a-button>
        <a-button v-if="canManage" @click="generateDueBatch">批量生成到期应收</a-button>
        <a-button v-if="canManage" @click="runAutomation">执行提醒</a-button>
        <a-button @click="reload">刷新</a-button>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1550 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'amount'">{{ money(record.monthlyFee) }}</template>
          <template v-else-if="column.key === 'deposit'">{{ money(record.deposit) }}</template>
          <template v-else-if="column.key === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ statusLabel(record.status) }}</a-tag></template>
          <template v-else-if="column.key === 'endMode'">{{ endModeLabel(record.ownershipAtEnd) }}</template>
          <template v-else-if="column.key === 'actions'">
            <a-space wrap>
              <a-button size="small" @click="openDetail(record)">月租计划</a-button>
              <a-button v-if="canManage && record.status === 'ACTIVE'" size="small" type="primary" @click="generateDue(record)">生成到期应收</a-button>
              <a-button v-if="canManage && record.status === 'ACTIVE' && record.ownershipAtEnd === 'RETURN'" size="small" danger @click="endLease(record)">到期归还</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="formOpen" title="新建电池租赁合同" :confirm-loading="saving" ok-text="确认起租"
             cancel-text="取消" width="760px" @ok="create">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="客户" required><a-select v-model:value="form.customerId" :options="customerOptions" show-search /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="在库电池" required><a-select v-model:value="form.serialId" :options="batteryOptions" show-search placeholder="选择在库电池 SN" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="起租日期" required><a-date-picker v-model:value="form.startDate" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="结束日期" required><a-date-picker v-model:value="form.endDate" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="月租" required><MoneyInput v-model:value="form.monthlyFee" :min="0.01" :precision="2" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="押金"><MoneyInput v-model:value="form.deposit" :min="0" :precision="2" style="width:100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="到期处理"><a-select v-model:value="form.ownershipAtEnd" :options="endModeOptions" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="备注"><a-input v-model:value="form.remark" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>

    <a-modal v-model:open="detailOpen" :title="`月租计划 · ${current?.contractNo || ''}`" footer="null" width="980px">
      <a-descriptions bordered :column="3" size="small" style="margin-bottom:14px">
        <a-descriptions-item label="客户">{{ current?.customerName }}</a-descriptions-item>
        <a-descriptions-item label="电池 SN">{{ current?.serialNo }}</a-descriptions-item>
        <a-descriptions-item label="月租">{{ money(current?.monthlyFee) }}</a-descriptions-item>
      </a-descriptions>
      <a-table :data-source="current?.schedules || []" row-key="id" size="small" :pagination="false">
        <a-table-column title="期次" data-index="periodNo" :width="80" align="center" />
        <a-table-column title="到期日" data-index="dueDate" :width="120" />
        <a-table-column title="金额" :width="130" align="right"><template #default="{ record }">{{ money(record.amount) }}</template></a-table-column>
        <a-table-column title="应收单号" data-index="receivableNo" :width="150" />
        <a-table-column title="状态" :width="120"><template #default="{ record }">{{ receivableStatus(record.receivableStatus) }}</template></a-table-column>
        <a-table-column title="未收" :width="130" align="right"><template #default="{ record }">{{ money(record.outstanding) }}</template></a-table-column>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs, { type Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'
import { leaseApi } from '../../api/lease'
import { customerApi, serialApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { LeaseContract, LeaseOwnershipAtEnd, LeaseStatus } from '../../types/lease'
import type { ProductSerial } from '../../types/masterdata'

const auth = useAuthStore()
const canManage = auth.can('IVS_LEASE_MANAGE')
const rows = ref<LeaseContract[]>([])
const serials = ref<ProductSerial[]>([])
const customerOptions = ref<{ label: string; value: string }[]>([])
const formOpen = ref(false)
const detailOpen = ref(false)
const saving = ref(false)
const current = ref<LeaseContract>()
const form = reactive<Record<string, any>>({})
const endModeOptions = [
  { value: 'RETURN', label: '到期归还' },
  { value: 'BUYOUT', label: '到期买断（后续版本）', disabled: true },
  { value: 'RENEW', label: '到期续租（后续版本）', disabled: true },
]
const batteryOptions = computed(() => serials.value
  .filter((item) => item.status === 'IN_STOCK' && (item.categoryCode?.toUpperCase().startsWith('BATTERY')
    || item.categoryName?.includes('电池')))
  .map((item) => ({ label: `${item.serialNo} · ${item.productName || ''}`, value: item.id })))
const columns = [
  { title: '合同编号', key: 'contractNo', dataIndex: 'contractNo', width: 140 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 180 },
  { title: '电池 SN', key: 'serialNo', dataIndex: 'serialNo', width: 180 },
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 180 },
  { title: '租期', key: 'period', width: 210 },
  { title: '月租', key: 'amount', width: 130, align: 'right' },
  { title: '押金', key: 'deposit', width: 130, align: 'right' },
  { title: '到期处理', key: 'endMode', width: 110 },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' },
]
const activeCount = computed(() => rows.value.filter((item) => item.status === 'ACTIVE').length)
const monthlyIncome = computed(() => rows.value.filter((item) => item.status === 'ACTIVE')
  .reduce((sum, item) => sum + Number(item.monthlyFee || 0), 0))
const overdueAmount = computed(() => rows.value.flatMap((item) => item.schedules || [])
  .filter((item) => dayjs(item.dueDate).isBefore(dayjs().startOf('day')) && Number(item.outstanding || 0) > 0)
  .reduce((sum, item) => sum + Number(item.outstanding || 0), 0))
function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function statusLabel(value: LeaseStatus) { return value === 'ACTIVE' ? '租赁中' : value === 'ENDED' ? '已结束' : '已取消' }
function endModeLabel(value: LeaseOwnershipAtEnd) { return value === 'RETURN' ? '归还' : value === 'BUYOUT' ? '买断' : '续租' }
function receivableStatus(value: string) { return ({ NOT_GENERATED: '未生成', OPEN: '待收款', PARTIALLY_SETTLED: '部分收款', SETTLED: '已结清', OVERDUE: '逾期' } as Record<string, string>)[value] || value }
function openCreate() {
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { monthlyFee: 0, deposit: 0, ownershipAtEnd: 'RETURN', startDate: dayjs(), endDate: dayjs().add(12, 'month') })
  formOpen.value = true
}
async function create() {
  if (!form.customerId || !form.serialId || !form.startDate || !form.endDate || !form.monthlyFee) {
    message.warning('请填写客户、电池、租期和月租'); return
  }
  saving.value = true
  try {
    await leaseApi.create({ ...form,
      startDate: (form.startDate as Dayjs).format('YYYY-MM-DD'),
      endDate: (form.endDate as Dayjs).format('YYYY-MM-DD') })
    message.success('租赁合同已生效，电池已转入租赁资产池')
    formOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
function openDetail(record: LeaseContract) { current.value = record; detailOpen.value = true }
async function generateDue(record: LeaseContract) {
  try {
    const result = await leaseApi.generateDue(record.id, dayjs().format('YYYY-MM-DD'))
    current.value = result
    message.success('到期月租应收已生成')
    await reload()
  } catch (error) { message.error((error as Error).message) }
}
async function generateDueBatch() {
  try {
    const result = await leaseApi.generateDueBatch(dayjs().format('YYYY-MM-DD'))
    message.success(`已处理 ${result.activeContracts} 份合同，生成 ${result.generatedReceivables} 笔到期应收`)
    await reload()
  } catch (error) { message.error((error as Error).message) }
}
async function runAutomation() {
  try {
    const result = await leaseApi.runAutomation(dayjs().format('YYYY-MM-DD'))
    message.success(`生成应收 ${result.generatedReceivables} 笔，即将到期提醒 ${result.dueSoonReminders} 条，逾期提醒 ${result.overdueReminders} 条`)
    await reload()
  } catch (error) { message.error((error as Error).message) }
}
function endLease(record: LeaseContract) {
  Modal.confirm({ title: `确认结束合同 ${record.contractNo}？`, content: '电池将执行租赁归还入库，重新进入可售库存。',
    okText: '确认归还', cancelText: '取消', onOk: async () => {
      try { await leaseApi.end(record.id); message.success('租赁已结束，电池已归还入库'); await reload() }
      catch (error) { message.error((error as Error).message) }
    } })
}
async function reload() {
  try { rows.value = await leaseApi.list() }
  catch (error) { message.error((error as Error).message) }
}
onMounted(async () => {
  try {
    const [customers, serialRows] = await Promise.all([customerApi.list(), serialApi.list()])
    customerOptions.value = customers.map((item) => ({ label: item.name, value: item.id }))
    serials.value = serialRows
  } catch { /* ignore */ }
  await reload()
})
</script>
