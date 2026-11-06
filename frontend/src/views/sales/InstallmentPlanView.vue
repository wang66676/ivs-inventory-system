<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="分期收款与以租代售：首付及每期分期均生成应收，登记收款后按所选期次核销；全部结清后自动完成所有权转移。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-button @click="exportPlans">导出</a-button>
        <span class="hint">共 {{ rows.length }} 个分期计划</span>
      </a-space>
      <a-table v-resizable-columns :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 15, showSizeChanger: true }" :scroll="{ x: 1750 }">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.schedules" row-key="id" size="small" :pagination="false">
            <a-table-column title="期次" :width="90">
              <template #default="{ record: schedule }">{{ schedule.periodNo === 0 ? '首付' : `第 ${schedule.periodNo} 期` }}</template>
            </a-table-column>
            <a-table-column title="应收单号" data-index="receivableNo" :width="150" />
            <a-table-column title="到期日" data-index="dueDate" :width="120" />
            <a-table-column title="应还" :width="120"><template #default="{ record: schedule }">{{ money(schedule.amount) }}</template></a-table-column>
            <a-table-column title="已收" :width="120"><template #default="{ record: schedule }">{{ money(schedule.receivedAmount) }}</template></a-table-column>
            <a-table-column title="未收" :width="120"><template #default="{ record: schedule }">{{ money(schedule.outstanding) }}</template></a-table-column>
            <a-table-column title="状态" :width="120">
              <template #default="{ record: schedule }">
                <a-tag :color="schedule.outstanding <= 0 ? 'green' : schedule.overdueDays > 0 ? 'red' : 'blue'">
                  {{ schedule.outstanding <= 0 ? '已结清' : schedule.overdueDays > 0 ? `逾期 ${schedule.overdueDays} 天` : '待收款' }}
                </a-tag>
              </template>
            </a-table-column>
            <a-table-column title="操作" :width="100">
              <template #default="{ record: schedule }">
                <a v-if="canPay && schedule.outstanding > 0" @click="openPay(record, schedule)">收款</a>
                <span v-else>-</span>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orderNo'"><a @click="openOrder(record)">{{ record.orderNo || '-' }}</a></template>
          <template v-else-if="column.key === 'totalAmount'">{{ money(record.totalAmount) }}</template>
          <template v-else-if="column.key === 'downPayment'">{{ money(record.downPayment) }}</template>
          <template v-else-if="column.key === 'financedAmount'">{{ money(record.financedAmount) }}</template>
          <template v-else-if="column.key === 'receivedAmount'">{{ money(record.receivedAmount) }}</template>
          <template v-else-if="column.key === 'outstanding'"><b>{{ money(record.outstanding) }}</b></template>
          <template v-else-if="column.key === 'overdueAmount'">
            <a-tag :color="record.overdueAmount > 0 ? 'red' : 'default'">{{ money(record.overdueAmount) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'SETTLED' ? 'green' : record.overdueAmount > 0 ? 'red' : 'blue'">
              {{ record.status === 'SETTLED' ? '已结清' : record.overdueAmount > 0 ? '逾期' : '履约中' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="payOpen" title="分期收款" :confirm-loading="saving" ok-text="确认收款"
             cancel-text="取消" @ok="submitPay">
      <a-form layout="vertical">
        <a-form-item label="客户">{{ payPlan?.customerName }}</a-form-item>
        <a-form-item label="期次">{{ paySchedule?.periodNo === 0 ? '首付' : `第 ${paySchedule?.periodNo} 期` }}</a-form-item>
        <a-form-item label="本次收款" required><MoneyInput v-model:value="payAmount" :min="0.01" :precision="2" style="width:100%" /></a-form-item>
        <a-form-item label="收款方式"><a-select v-model:value="payMethod" :options="paymentMethods" style="width:100%" /></a-form-item>
        <a-form-item label="备注"><a-input v-model:value="payRemark" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { salesApi } from '../../api/sales'
import { useAuthStore } from '../../stores/auth'
import { exportCsv, exportTimestamp } from '../../utils/csvExport'
import type { InstallmentPlan, InstallmentSchedule, PaymentMethod } from '../../types/sales'

const router = useRouter()
const auth = useAuthStore()
const canPay = auth.can('IVS_WRITE')
const rows = ref<InstallmentPlan[]>([])
const payOpen = ref(false)
const saving = ref(false)
const payPlan = ref<InstallmentPlan>()
const paySchedule = ref<InstallmentSchedule>()
const payAmount = ref<number>()
const payMethod = ref<PaymentMethod>('BANK_TRANSFER')
const payRemark = ref('')
const paymentMethods = [
  { value: 'BANK_TRANSFER', label: '银行转账' }, { value: 'CASH', label: '现金' },
  { value: 'ACCEPTANCE_BILL', label: '承兑汇票' }, { value: 'OTHER', label: '其他' },
]
const columns = [
  { title: '分期计划号', key: 'planNo', dataIndex: 'planNo', width: 150 },
  { title: '订单编号', key: 'orderNo', width: 150 }, { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 200 },
  { title: '订单总额', key: 'totalAmount', width: 130, align: 'right' }, { title: '首付', key: 'downPayment', width: 120, align: 'right' },
  { title: '分期金额', key: 'financedAmount', width: 130, align: 'right' }, { title: '期数', dataIndex: 'installmentCount', key: 'installmentCount', width: 80, align: 'right' },
  { title: '已收', key: 'receivedAmount', width: 130, align: 'right' }, { title: '未收', key: 'outstanding', width: 130, align: 'right' },
  { title: '逾期金额', key: 'overdueAmount', width: 130, align: 'right' }, { title: '下期到期', dataIndex: 'nextDueDate', key: 'nextDueDate', width: 120 },
  { title: '状态', key: 'status', width: 100 },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` }
function openOrder(_plan: InstallmentPlan) { router.push('/sales/orders') }
function reload() { salesApi.installmentPlans().then((items) => { rows.value = items }).catch((error) => message.error((error as Error).message)) }
function openPay(plan: InstallmentPlan, schedule: InstallmentSchedule) {
  payPlan.value = plan; paySchedule.value = schedule; payAmount.value = schedule.outstanding
  payMethod.value = 'BANK_TRANSFER'; payRemark.value = `${plan.planNo} ${schedule.periodNo === 0 ? '首付' : `第 ${schedule.periodNo} 期`}`
  payOpen.value = true
}
async function submitPay() {
  if (!payPlan.value || !paySchedule.value || !payAmount.value) { message.warning('请填写收款金额'); return }
  saving.value = true
  try {
    await salesApi.createReceipt({ customerId: payPlan.value.customerId, amount: payAmount.value,
      method: payMethod.value, remark: payRemark.value || null, receivableIds: [paySchedule.value.receivableId] })
    message.success('分期收款已登记'); payOpen.value = false; reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}
function exportPlans() {
  if (!rows.value.length) { message.warning('当前没有分期计划'); return }
  exportCsv<InstallmentPlan>(`分期收款-${exportTimestamp()}`, [
    { title: '分期计划号', value: (row) => row.planNo }, { title: '订单编号', value: (row) => row.orderNo },
    { title: '客户', value: (row) => row.customerName }, { title: '订单总额', value: (row) => row.totalAmount },
    { title: '首付', value: (row) => row.downPayment }, { title: '分期金额', value: (row) => row.financedAmount },
    { title: '期数', value: (row) => row.installmentCount }, { title: '已收', value: (row) => row.receivedAmount },
    { title: '未收', value: (row) => row.outstanding }, { title: '逾期金额', value: (row) => row.overdueAmount },
    { title: '状态', value: (row) => row.status },
  ], rows.value)
  message.success('分期计划已导出')
}
onMounted(reload)
</script>
