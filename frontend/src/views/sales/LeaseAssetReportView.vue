<template>
  <div>
    <a-card :bordered="false">
      <a-alert type="info" show-icon style="margin-bottom:14px"
               message="汇总纯电池租赁的应收租金、实收、未收、押金余额和最新健康状态；健康结论为需关注或异常的资产单独计数。" />
      <a-row :gutter="12" style="margin-bottom:14px">
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="租赁合同" :value="report?.summary.totalContracts || 0" suffix="份" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="在租资产" :value="report?.summary.activeContracts || 0" suffix="块" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="健康关注资产" :value="report?.summary.attentionAssets || 0" suffix="块" :value-style="attentionStyle" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="在租月租" :value="report?.summary.activeMonthlyRent || 0" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="累计实收租金" :value="report?.summary.receivedRent || 0" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="未收租金" :value="report?.summary.outstandingRent || 0" :precision="2" prefix="¥" :value-style="outstandingStyle" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="租赁资产原值" :value="report?.summary.purchaseCost || 0" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="累计折旧" :value="report?.summary.accumulatedDepreciation || 0" :precision="2" prefix="¥" /></a-card></a-col>
        <a-col :xs="12" :md="8"><a-card size="small"><a-statistic title="资产净值" :value="report?.summary.netValue || 0" :precision="2" prefix="¥" /></a-card></a-col>
      </a-row>
      <a-space style="margin-bottom:12px">
        <a-button @click="load">刷新</a-button>
        <a-button type="primary" :disabled="!report?.rows.length" @click="exportReport">导出 CSV</a-button>
        <span class="report-time">{{ report ? `生成时间：${dayjs(report.generatedAt).format('YYYY-MM-DD HH:mm:ss')}` : '' }}</span>
      </a-space>
      <a-table v-resizable-columns :data-source="report?.rows || []" :columns="columns" row-key="contractId"
               size="middle" :loading="loading" :pagination="{ pageSize: 15, showSizeChanger: true }"
               :scroll="{ x: 2100 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'"><a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag></template>
          <template v-else-if="column.key === 'period'">{{ record.startDate }} 至 {{ record.endDate }}</template>
          <template v-else-if="column.key === 'generatedRent'">{{ money(record.generatedRent) }}</template>
          <template v-else-if="column.key === 'receivedRent'">{{ money(record.receivedRent) }}</template>
          <template v-else-if="column.key === 'outstandingRent'"><span class="danger-text">{{ money(record.outstandingRent) }}</span></template>
          <template v-else-if="column.key === 'depositAvailable'">{{ money(record.depositAvailable) }}</template>
          <template v-else-if="column.key === 'purchaseCost'">{{ money(record.purchaseCost) }}</template>
          <template v-else-if="column.key === 'monthlyDepreciation'">{{ money(record.monthlyDepreciation) }}</template>
          <template v-else-if="column.key === 'accumulatedDepreciation'">{{ money(record.accumulatedDepreciation) }}</template>
          <template v-else-if="column.key === 'netValue'">{{ money(record.netValue) }}</template>
          <template v-else-if="column.key === 'latestSoh'">{{ record.latestSohPercent == null ? '-' : `${record.latestSohPercent}%` }}</template>
          <template v-else-if="column.key === 'mileage'">{{ record.latestMileageKm == null ? '-' : `${record.latestMileageKm} km` }}</template>
          <template v-else-if="column.key === 'inspection'">
            <a-tag :color="inspectionColor(record.latestInspectionResult)">{{ inspectionLabel(record.latestInspectionResult) }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { leaseApi } from '../../api/lease'
import { exportCsv, exportTimestamp, type CsvColumn } from '../../utils/csvExport'
import type { LeaseAssetReport, LeaseAssetReportRow, LeaseInspectionResult, LeaseStatus } from '../../types/lease'

const loading = ref(false)
const report = ref<LeaseAssetReport>()
const attentionStyle = { color: '#d46b08' }
const outstandingStyle = { color: '#cf1322' }
const columns = [
  { title: '合同编号', key: 'contractNo', dataIndex: 'contractNo', width: 145 },
  { title: '客户', key: 'customerName', dataIndex: 'customerName', width: 160 },
  { title: '电池 SN', key: 'serialNo', dataIndex: 'serialNo', width: 170 },
  { title: '商品', key: 'productName', dataIndex: 'productName', width: 160 },
  { title: '状态', key: 'status', width: 90, align: 'center' },
  { title: '租期', key: 'period', width: 210 },
  { title: '月租', key: 'monthlyFee', dataIndex: 'monthlyFee', width: 120, align: 'right' },
  { title: '应收租金', key: 'generatedRent', width: 130, align: 'right' },
  { title: '实收租金', key: 'receivedRent', width: 130, align: 'right' },
  { title: '未收租金', key: 'outstandingRent', width: 130, align: 'right' },
  { title: '押金余额', key: 'depositAvailable', width: 130, align: 'right' },
  { title: '资产原值', key: 'purchaseCost', width: 130, align: 'right' },
  { title: '月折旧', key: 'monthlyDepreciation', width: 120, align: 'right' },
  { title: '累计折旧', key: 'accumulatedDepreciation', width: 130, align: 'right' },
  { title: '资产净值', key: 'netValue', width: 130, align: 'right' },
  { title: '最新 SOH', key: 'latestSoh', width: 110, align: 'right' },
  { title: '循环次数', key: 'latestCycleCount', dataIndex: 'latestCycleCount', width: 100, align: 'right' },
  { title: '累计里程', key: 'mileage', width: 130, align: 'right' },
  { title: '健康结论', key: 'inspection', width: 100, align: 'center' },
  { title: '检测日期', key: 'latestInspectionDate', dataIndex: 'latestInspectionDate', width: 120 },
]

function money(value?: number) { return value == null ? '-' : `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` }
function statusLabel(value: LeaseStatus) { return value === 'ACTIVE' ? '租赁中' : value === 'ENDED' ? '已结束' : '已取消' }
function statusColor(value: LeaseStatus) { return value === 'ACTIVE' ? 'green' : value === 'ENDED' ? 'blue' : 'default' }
function inspectionLabel(value?: LeaseInspectionResult) {
  return ({ NORMAL: '正常', ATTENTION: '需关注', ABNORMAL: '异常' } as Record<string, string>)[value || ''] || '未检测'
}
function inspectionColor(value?: LeaseInspectionResult) {
  return value === 'NORMAL' ? 'green' : value === 'ATTENTION' ? 'orange' : value === 'ABNORMAL' ? 'red' : 'default'
}

async function load() {
  loading.value = true
  try { report.value = await leaseApi.assetReport() }
  catch (error) { message.error((error as Error).message) } finally { loading.value = false }
}

function exportReport() {
  if (!report.value) return
  const exportColumns: CsvColumn<LeaseAssetReportRow>[] = [
    { title: '合同编号', value: (row) => row.contractNo },
    { title: '客户', value: (row) => row.customerName },
    { title: '电池SN', value: (row) => row.serialNo },
    { title: '商品', value: (row) => row.productName },
    { title: '状态', value: (row) => statusLabel(row.status) },
    { title: '起租日期', value: (row) => row.startDate },
    { title: '结束日期', value: (row) => row.endDate },
    { title: '月租', value: (row) => row.monthlyFee },
    { title: '应收租金', value: (row) => row.generatedRent },
    { title: '实收租金', value: (row) => row.receivedRent },
    { title: '未收租金', value: (row) => row.outstandingRent },
    { title: '合同押金', value: (row) => row.depositExpected },
    { title: '已收押金', value: (row) => row.depositReceived },
    { title: '押金抵扣', value: (row) => row.depositApplied },
    { title: '押金退还', value: (row) => row.depositRefunded },
    { title: '押金余额', value: (row) => row.depositAvailable },
    { title: '资产原值', value: (row) => row.purchaseCost },
    { title: '月折旧', value: (row) => row.monthlyDepreciation },
    { title: '累计折旧', value: (row) => row.accumulatedDepreciation },
    { title: '资产净值', value: (row) => row.netValue },
    { title: '最新SOH', value: (row) => row.latestSohPercent },
    { title: '循环次数', value: (row) => row.latestCycleCount },
    { title: '累计里程', value: (row) => row.latestMileageKm },
    { title: '健康结论', value: (row) => inspectionLabel(row.latestInspectionResult) },
    { title: '检测日期', value: (row) => row.latestInspectionDate },
  ]
  exportCsv(`租赁资产报表-${exportTimestamp()}`, exportColumns, report.value.rows)
}

onMounted(load)
</script>

<style scoped>
.report-time { color: #8c8c8c; font-size: 13px; }
.danger-text { color: #cf1322; font-weight: 600; }
</style>
