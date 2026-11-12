<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="资金池余额" :value="pool?.balanceAmount || 0" :precision="2" prefix="¥" /><div class="hint">一个池，按厂家分账</div></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="累计入池" :value="pool?.totalInAmount || 0" :precision="2" prefix="¥" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="累计抵扣" :value="pool?.totalOffsetAmount || 0" :precision="2" prefix="¥" /></a-card></a-col>
      <a-col :span="6"><a-card :bordered="false"><a-statistic title="预抵扣" :value="pool?.allowPreOffset ? '允许' : '禁止'" /></a-card></a-col>
    </a-row>

    <a-card :bordered="false" style="margin-top:16px">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="资金池只接受已到账返利：厂家红字发票登记后自动入池；只能抵扣同一厂家车款，不允许预抵扣。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button @click="reload">刷新</a-button>
        <a-select v-model:value="supplierId" :options="supplierOptions" allow-clear show-search
                  placeholder="按厂家筛选" style="width:240px" @change="reload" />
        <a-button v-if="canAdjust" @click="openCredit">人工调整入池</a-button>
      </a-space>

      <a-table :data-source="pool?.suppliers || []" :columns="supplierColumns" row-key="supplierId"
               size="middle" :pagination="false" style="margin-bottom:18px">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'supplier'">{{ supplierName(record.supplierId) }}</template>
          <template v-else-if="column.key === 'balance'">{{ money(record.balanceAmount) }}</template>
          <template v-else-if="column.key === 'totalIn'">{{ money(record.totalInAmount) }}</template>
          <template v-else-if="column.key === 'totalOffset'">{{ money(record.totalOffsetAmount) }}</template>
        </template>
      </a-table>

      <a-tabs>
        <a-tab-pane key="txns" tab="资金流水">
          <a-table :data-source="txns" :columns="txnColumns" row-key="id" size="small"
                   :pagination="{ pageSize: 10 }" :scroll="{ x: 1200 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'direction'">
                <a-tag :color="record.direction === 'IN' ? 'green' : 'orange'">{{ record.direction === 'IN' ? '入池' : '抵扣' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>
              <template v-else-if="column.key === 'balanceAfter'">{{ money(record.balanceAfter) }}</template>
              <template v-else-if="column.key === 'supplier'">{{ supplierName(record.supplierId) }}</template>
              <template v-else-if="column.key === 'occurredAt'">{{ formatTime(record.occurredAt) }}</template>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="offsets" tab="抵扣单">
          <a-table :data-source="offsets" :columns="offsetColumns" row-key="id" size="small"
                   :pagination="{ pageSize: 10 }" :scroll="{ x: 1100 }">
            <template #expandedRowRender="{ record }">
              <a-table :data-source="record.lines" row-key="fundTxnId" size="small" :pagination="false">
                <a-table-column title="返利来源" data-index="sourceNo" />
                <a-table-column title="本次抵扣" :width="160">
                  <template #default="{ record: line }">{{ money(line.amount) }}</template>
                </a-table-column>
              </a-table>
            </template>
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'supplier'">{{ supplierName(record.supplierId) }}</template>
              <template v-else-if="column.key === 'appliedAmount'">{{ money(record.appliedAmount) }}</template>
              <template v-else-if="column.key === 'balanceAfter'">{{ money(record.balanceAfter) }}</template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">
                  {{ record.status === 'ACTIVE' ? '已抵扣' : record.status === 'CANCELLED' ? '已取消' : record.status }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-button v-if="canOffset && record.status === 'ACTIVE'" danger size="small" @click="cancelOffset(record)">
                  取消抵扣
                </a-button>
                <span v-else>-</span>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <a-modal v-model:open="creditOpen" title="人工调整入池" :confirm-loading="saving" ok-text="确认入池"
             cancel-text="取消" @ok="credit">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="人工调整仅用于期初余额或经批准的账务调整，必须填写依据单号与备注。" />
      <a-form layout="vertical">
        <a-form-item label="厂家" required><a-select v-model:value="creditForm.supplierId" :options="supplierOptions" show-search /></a-form-item>
        <a-form-item label="金额" required><MoneyInput v-model:value="creditForm.amount" :min="0.01" :precision="2" style="width:100%" /></a-form-item>
        <a-form-item label="依据单号"><a-input v-model:value="creditForm.sourceNo" /></a-form-item>
        <a-form-item label="备注" required><a-textarea v-model:value="creditForm.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { supplierApi } from '../../api/masterdata'
import { useAuthStore } from '../../stores/auth'
import type { FundOffset, FundPool, FundTxn } from '../../types/policy'

const auth = useAuthStore()
const canAdjust = auth.can('IVS_FUND_POOL_ADJUST')
const canOffset = auth.can('IVS_FUND_OFFSET')
const pool = ref<FundPool>()
const txns = ref<FundTxn[]>([])
const offsets = ref<FundOffset[]>([])
const supplierId = ref<string | undefined>()
const supplierNames = ref<Record<string, string>>({})
const supplierOptions = ref<{ label: string; value: string }[]>([])
const creditOpen = ref(false)
const saving = ref(false)
const creditForm = reactive<Record<string, any>>({})

const supplierColumns = [
  { title: '厂家', key: 'supplier', width: 260 },
  { title: '累计入池', key: 'totalIn', width: 180 },
  { title: '累计抵扣', key: 'totalOffset', width: 180 },
  { title: '子余额', key: 'balance', width: 180 },
]
const txnColumns = [
  { title: '时间', key: 'occurredAt', width: 170 },
  { title: '厂家', key: 'supplier', width: 200 },
  { title: '方向', key: 'direction', width: 90 },
  { title: '类型', key: 'txnType', dataIndex: 'txnType', width: 160 },
  { title: '金额', key: 'amount', width: 130 },
  { title: '余额', key: 'balanceAfter', width: 130 },
  { title: '来源类型', key: 'sourceType', dataIndex: 'sourceType', width: 130 },
  { title: '来源单号', key: 'sourceNo', dataIndex: 'sourceNo', width: 180 },
  { title: '抵扣单号', key: 'offsetNo', dataIndex: 'offsetNo', width: 160 },
]
const offsetColumns = [
  { title: '抵扣单号', key: 'offsetNo', dataIndex: 'offsetNo', width: 150 },
  { title: '厂家', key: 'supplier', width: 200 },
  { title: '入库单', key: 'receiptNo', dataIndex: 'receiptNo', width: 150 },
  { title: '抵扣额', key: 'appliedAmount', width: 130 },
  { title: '抵扣后余额', key: 'balanceAfter', width: 140 },
  { title: '状态', key: 'status', width: 100 },
  { title: '时间', key: 'createdAt', dataIndex: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 110, fixed: 'right' },
]

function supplierName(id: string) { return supplierNames.value[id] || id }
function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }

function openCredit() {
  Object.keys(creditForm).forEach((key) => delete creditForm[key])
  creditForm.amount = 0
  creditOpen.value = true
}

async function credit() {
  if (!creditForm.supplierId || !creditForm.amount || !creditForm.remark?.trim()) {
    message.warning('请填写厂家、金额与调整依据'); return
  }
  saving.value = true
  try {
    await policyApi.requestCreditFund({
      supplierId: creditForm.supplierId, amount: creditForm.amount, sourceType: 'ADJUST_IN',
      sourceNo: creditForm.sourceNo || null, sourceId: null, remark: creditForm.remark.trim(),
    })
    message.success('资金池调整已处理；达到阈值时将进入审批，通过后入池')
    creditOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function cancelOffset(record: FundOffset) {
  Modal.confirm({
    title: `取消抵扣 ${record.offsetNo}？`,
    content: `取消后将回补厂家返利余额 ${money(record.appliedAmount)}，并新增回补流水。`,
    okText: '确认取消', okType: 'danger', cancelText: '返回',
    onOk: async () => {
      try {
        await policyApi.cancelFundOffset(record.id)
        message.success('抵扣已取消，返利余额已回补')
        await reload()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}

async function reload() {
  try {
    const [poolData, txnData, offsetData] = await Promise.all([
      policyApi.fundPool(), policyApi.fundTxns({ supplierId: supplierId.value }),
      policyApi.fundOffsets({ supplierId: supplierId.value }),
    ])
    pool.value = poolData
    txns.value = txnData
    offsets.value = offsetData
  } catch (error) { message.error((error as Error).message) }
}

onMounted(async () => {
  try {
    const suppliers = await supplierApi.list()
    supplierNames.value = Object.fromEntries(suppliers.map((item) => [item.id, item.name]))
    supplierOptions.value = suppliers.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }))
  } catch { /* ignore */ }
  await reload()
})
</script>

<style scoped>
.hint { margin-top: 4px; color: #8b96a8; font-size: 12px; }
</style>
