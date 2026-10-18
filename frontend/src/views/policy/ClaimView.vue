<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:12px"
               message="申报单按厂家 + 政策 + 周期生成，也可同厂家同周期合并。厂家确认差异必须逐行填写原因与处置；红字金额必须等于厂家确认额。" />
      <a-space wrap style="margin-bottom:12px">
        <a-button v-if="canSubmit" type="primary" @click="openGenerate">按期间生成申报单</a-button>
        <a-button @click="reload">刷新</a-button>
        <a-input v-model:value="periodCode" placeholder="期间，如 2026-10" allow-clear style="width:180px" @press-enter="reload" />
        <a-select v-model:value="status" :options="statusOptions" allow-clear placeholder="状态" style="width:170px" @change="reload" />
      </a-space>
      <a-table :data-source="rows" :columns="columns" row-key="id" size="middle"
               :pagination="{ pageSize: 12 }" :scroll="{ x: 1500 }">
        <template #expandedRowRender="{ record }">
          <a-table :data-source="record.lines" row-key="id" size="small" :pagination="false">
            <a-table-column title="权益编号" data-index="entitlementNo" :width="150" />
            <a-table-column title="政策" data-index="policyName" :width="200" />
            <a-table-column title="实销单" :width="220">
              <template #default="{ record: line }">{{ line.retailNo || line.retailId || '-' }}</template>
            </a-table-column>
            <a-table-column title="VIN / SN" :width="180">
              <template #default="{ record: line }">{{ line.serialNo || line.serialId || '-' }}</template>
            </a-table-column>
            <a-table-column title="申报额" :width="130">
              <template #default="{ record: line }">{{ money(line.amount) }}</template>
            </a-table-column>
            <a-table-column title="确认额" :width="130">
              <template #default="{ record: line }">{{ money(line.confirmedAmount) }}</template>
            </a-table-column>
            <a-table-column title="差异" :width="240">
              <template #default="{ record: line }">
                <span v-if="Number(line.differenceAmount) === 0">-</span>
                <span v-else>{{ money(line.differenceAmount) }} · {{ line.differenceReason }}</span>
              </template>
            </a-table-column>
          </a-table>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'policy'">
            <a-tag v-if="record.mergedClaim" color="purple">合并申报</a-tag>
            <span v-else>{{ record.policyName }}</span>
          </template>
          <template v-else-if="column.key === 'amounts'">
            <div>申报 {{ money(record.claimAmount) }}</div>
            <div class="sub">确认 {{ money(record.confirmedAmount) }} · 差异 {{ money(record.differenceAmount) }}</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ CLAIM_STATUS_LABELS[record.status] }}</a-tag>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space wrap>
              <a-button v-if="canSubmit && record.status === 'DRAFT'" size="small" type="primary"
                        @click="submitClaim(record)">提交</a-button>
              <a-button v-if="canConfirm && record.status === 'SUBMITTED'" size="small" type="primary"
                        @click="openConfirm(record)">厂家确认</a-button>
              <a-button v-if="canConfirm && ['SUBMITTED','PARTIALLY_CONFIRMED','DISPUTED'].includes(record.status)"
                        size="small" danger @click="openDispute(record)">争议</a-button>
              <a-button v-if="record.differences?.length" size="small" @click="openDifferences(record)">差异</a-button>
              <a-button v-if="canConfirm && ['CONFIRMED','PARTIALLY_CONFIRMED','SETTLED'].includes(record.status)"
                        size="small" @click="closeClaim(record)">关闭</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="generateOpen" title="按期间生成申报单" :confirm-loading="saving" ok-text="生成草稿"
             cancel-text="取消" @ok="generate">
      <a-form layout="vertical">
        <a-form-item label="申报期间" required><a-input v-model:value="genPeriod" placeholder="如 2026-10 / 2026Q4 / 2026" /></a-form-item>
        <a-form-item label="同厂家同周期合并政策"><a-switch v-model:checked="genMerge" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="genRemark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="confirmOpen" title="登记厂家确认" :confirm-loading="saving" ok-text="确认并生成差异"
             cancel-text="取消" width="980px" @ok="confirm">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
               message="确认额小于申报额时，原因与处置必填；成本分摊将按确认额校正。" />
      <a-table :data-source="confirmLines" row-key="lineId" size="small" :pagination="false"
               :scroll="{ x: 920 }">
        <a-table-column title="权益" data-index="entitlementNo" :width="140" />
        <a-table-column title="政策" data-index="policyName" :width="160" />
        <a-table-column title="申报额" :width="120">
          <template #default="{ record }">{{ money(record.amount) }}</template>
        </a-table-column>
        <a-table-column title="确认额" :width="150">
          <template #default="{ record }"><a-input-number v-model:value="record.confirmedAmount" :min="0" :max="record.amount" :precision="2" style="width:130px" /></template>
        </a-table-column>
        <a-table-column title="差异原因" :width="190">
          <template #default="{ record }"><a-input v-model:value="record.reason" placeholder="有差异时必填" /></template>
        </a-table-column>
        <a-table-column title="处置" :width="160">
          <template #default="{ record }">
            <a-select v-model:value="record.disposition" :options="dispositionOptions" allow-clear style="width:140px" />
          </template>
        </a-table-column>
      </a-table>
      <a-checkbox v-model:checked="confirmDisputed" style="margin-top:12px">标记为厂家争议</a-checkbox>
    </a-modal>

    <a-modal v-model:open="disputeOpen" title="登记厂家争议" :confirm-loading="saving" ok-text="确认争议"
             cancel-text="取消" @ok="dispute">
      <a-form layout="vertical">
        <a-form-item label="争议原因" required><a-textarea v-model:value="disputeReason" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="differencesOpen" title="申报差异" footer="" width="900px">
      <a-table :data-source="current?.differences || []" row-key="id" size="small" :pagination="false">
        <a-table-column title="差异额" :width="120">
          <template #default="{ record }">{{ money(record.amount) }}</template>
        </a-table-column>
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="处置" :width="130">
          <template #default="{ record }">{{ DISPOSITION_LABELS[record.disposition] || record.disposition }}</template>
        </a-table-column>
        <a-table-column title="状态" :width="100">
          <template #default="{ record }"><a-tag :color="record.status === 'RESOLVED' ? 'green' : 'orange'">{{ record.status }}</a-tag></template>
        </a-table-column>
        <a-table-column title="操作" :width="110">
          <template #default="{ record }">
            <a-button v-if="canConfirm && record.status !== 'RESOLVED'" size="small" @click="openResolve(record)">标记解决</a-button>
          </template>
        </a-table-column>
      </a-table>
    </a-modal>

    <a-modal v-model:open="resolveOpen" title="差异处置结果" :confirm-loading="saving" ok-text="标记已解决"
             cancel-text="取消" @ok="resolve">
      <a-form layout="vertical">
        <a-form-item label="处置说明"><a-textarea v-model:value="resolveRemark" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { policyApi } from '../../api/policy'
import { useAuthStore } from '../../stores/auth'
import { CLAIM_STATUS_LABELS, DISPOSITION_LABELS } from '../../types/policy'
import type { ClaimDifference, OemClaim } from '../../types/policy'

const auth = useAuthStore()
const canSubmit = auth.can('IVS_CLAIM_SUBMIT')
const canConfirm = auth.can('IVS_CLAIM_CONFIRM')
const rows = ref<OemClaim[]>([])
const periodCode = ref('')
const status = ref<string | undefined>()
const saving = ref(false)

const generateOpen = ref(false)
const genPeriod = ref('')
const genMerge = ref(false)
const genRemark = ref('')

const confirmOpen = ref(false)
const confirmDisputed = ref(false)
const confirmLines = ref<any[]>([])
const current = ref<OemClaim>()

const disputeOpen = ref(false)
const disputeReason = ref('')

const differencesOpen = ref(false)
const resolveOpen = ref(false)
const resolveRemark = ref('')
const resolveClaimId = ref('')
const resolveDifferenceId = ref('')

const statusOptions = Object.entries(CLAIM_STATUS_LABELS).map(([value, label]) => ({ value, label }))
const dispositionOptions = Object.entries(DISPOSITION_LABELS).map(([value, label]) => ({ value, label }))
const columns = [
  { title: '申报单号', key: 'claimNo', dataIndex: 'claimNo', width: 140 },
  { title: '厂家', key: 'supplierName', dataIndex: 'supplierName', width: 180 },
  { title: '政策 / 粒度', key: 'policy', width: 180 },
  { title: '期间', key: 'periodCode', dataIndex: 'periodCode', width: 110 },
  { title: '金额', key: 'amounts', width: 220 },
  { title: '状态', key: 'status', width: 110 },
  { title: '红字发票', key: 'invoiceNo', dataIndex: 'invoiceNo', width: 150 },
  { title: '创建时间', key: 'createdAt', width: 170, customRender: ({ record }: any) => formatTime(record.createdAt) },
  { title: '操作', key: 'actions', width: 320, fixed: 'right' },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function formatTime(value: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }
function statusColor(value: string) {
  if (value === 'SETTLED' || value === 'CONFIRMED') return 'green'
  if (value === 'DISPUTED' || value === 'PARTIALLY_CONFIRMED') return 'orange'
  if (value === 'CLOSED') return 'default'
  return 'blue'
}

function openGenerate() { genPeriod.value = periodCode.value || ''; genMerge.value = false; genRemark.value = ''; generateOpen.value = true }

async function generate() {
  if (!genPeriod.value) { message.warning('请填写申报期间'); return }
  saving.value = true
  try {
    const claims = await policyApi.generateClaims({ period: genPeriod.value, merge: genMerge.value, remark: genRemark.value || undefined })
    message.success(`已生成 ${claims.length} 张申报草稿`)
    generateOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function submitClaim(record: OemClaim) {
  try { await policyApi.submitClaim(record.id); message.success('申报单已提交'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

function openConfirm(record: OemClaim) {
  current.value = record
  confirmDisputed.value = false
  confirmLines.value = record.lines.map((line) => ({
    lineId: line.id, entitlementNo: line.entitlementNo, policyName: line.policyName,
    amount: line.amount, confirmedAmount: Number(line.confirmedAmount) || Number(line.amount),
    reason: line.differenceReason || '', disposition: line.differenceDisposition || undefined,
  }))
  confirmOpen.value = true
}

async function confirm() {
  if (!current.value) return
  for (const line of confirmLines.value) {
    if (Number(line.confirmedAmount) < Number(line.amount)
      && (!line.reason?.trim() || !line.disposition)) {
      message.warning(`权益 ${line.entitlementNo} 存在差异，请填写原因并选择处置`); return
    }
  }
  saving.value = true
  try {
    await policyApi.confirmClaim(current.value.id, {
      lines: confirmLines.value.map((line) => ({
        lineId: line.lineId, confirmedAmount: line.confirmedAmount,
        reason: line.reason || null, disposition: line.disposition || null,
      })),
      disputed: confirmDisputed.value,
    })
    message.success('厂家确认已登记')
    confirmOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function openDispute(record: OemClaim) { current.value = record; disputeReason.value = ''; disputeOpen.value = true }

async function dispute() {
  if (!current.value || !disputeReason.value.trim()) { message.warning('请填写争议原因'); return }
  saving.value = true
  try {
    await policyApi.disputeClaim(current.value.id, { reason: disputeReason.value.trim() })
    message.success('争议已登记')
    disputeOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

function openDifferences(record: OemClaim) { current.value = record; differencesOpen.value = true }

function openResolve(difference: ClaimDifference) {
  resolveClaimId.value = difference.id ? current.value?.id || '' : ''
  resolveDifferenceId.value = difference.id
  resolveRemark.value = ''
  resolveOpen.value = true
}

async function resolve() {
  if (!resolveClaimId.value || !resolveDifferenceId.value) return
  saving.value = true
  try {
    await policyApi.resolveDifference(resolveClaimId.value, resolveDifferenceId.value, { remark: resolveRemark.value || undefined })
    message.success('差异已标记解决')
    resolveOpen.value = false
    differencesOpen.value = false
    await reload()
  } catch (error) { message.error((error as Error).message) } finally { saving.value = false }
}

async function closeClaim(record: OemClaim) {
  try { await policyApi.closeClaim(record.id); message.success('申报单已关闭'); await reload() }
  catch (error) { message.error((error as Error).message) }
}

async function reload() {
  try { rows.value = await policyApi.claims({ periodCode: periodCode.value || undefined, status: status.value }) }
  catch (error) { message.error((error as Error).message) }
}

onMounted(reload)
</script>

<style scoped>
.sub { margin-top: 2px; color: #8b96a8; font-size: 12px; }
</style>
