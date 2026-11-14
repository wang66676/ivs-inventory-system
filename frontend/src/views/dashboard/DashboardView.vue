<template>
  <div class="dashboard">
    <a-card :bordered="false" class="hero">
      <div class="hero-title">进销存系统（IVS）</div>
      <div class="hero-note">
        独立进销存系统：整车 / 动力电池 / 其他产品。主链路为
        <b>政策录入 → 订单选政策（预享受）→ 实销 → 申请兑现 → 厂家红字发票 → 返利资金池 → 抵扣车款</b>。
      </div>
      <a-space wrap style="margin-top:16px">
        <a-tag color="green">M0–M4 已交付</a-tag>
        <a-tag color="blue">库存双引擎：VIN 单车 + 数量</a-tag>
        <a-tag color="orange">返利资金池：按厂家分账，余额分批抵扣</a-tag>
      </a-space>
    </a-card>

    <a-row :gutter="16" style="margin-top:16px">
      <a-col :span="12">
        <a-card title="当前登录用户" :bordered="false">
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="用户名">{{ auth.user?.username }}</a-descriptions-item>
            <a-descriptions-item label="姓名">{{ auth.user?.displayName }}</a-descriptions-item>
            <a-descriptions-item label="数据范围">{{ auth.user?.dataScope }}</a-descriptions-item>
            <a-descriptions-item label="角色">{{ auth.user?.roles?.join(' / ') }}</a-descriptions-item>
            <a-descriptions-item label="权限数">{{ auth.user?.permissions?.length ?? 0 }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="实施路线" :bordered="false">
          <a-timeline>
            <a-timeline-item color="green">M0 骨架：平台能力 + 权限 + 审计（已完成）</a-timeline-item>
            <a-timeline-item color="green">M1 主数据：产品大项 / 商品 / VIN（采购自动建档）/ 仓库 / 供应商 / 客户（已完成）</a-timeline-item>
            <a-timeline-item color="green">M2 采购与库存：采购单 / 收货 / 台账 / 余额 / 看板（已完成）</a-timeline-item>
            <a-timeline-item color="green">M3 销售与结算：订单 / 出库 / 应收 / 收款 / 开票（已完成）</a-timeline-item>
            <a-timeline-item color="green">M4 实销与政策返利：实销 / 预享受 / 权益 / 申报 / 红字 / 资金池（已完成）</a-timeline-item>
            <a-timeline-item color="blue">M5 完整性：盘点 / 调拨 / 退换 / 报表 / 迁移 / 正式部署（下一步）</a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="常用入口" :bordered="false" style="margin-top:16px">
      <a-space wrap>
        <a-button @click="go('/system/users')">用户管理</a-button>
        <a-button @click="go('/system/roles')">角色权限</a-button>
        <a-button @click="go('/system/organizations')">组织架构</a-button>
        <a-button @click="go('/system/audit')">审计日志</a-button>
        <a-button type="primary" @click="go('/policy/policies')">政策管理</a-button>
        <a-button @click="go('/policy/fund-pool')">返利资金池</a-button>
        <a-button @click="go('/inventory/dashboard')">库存看板</a-button>
      </a-space>
      <a-alert style="margin-top:16px" type="info" show-icon
               message="M0–M4 已完成并通过 96 个集成测试；业务主链路已可端到端运行。下一步进入 M5：盘点、调拨、退换、报表、历史数据迁移与正式部署。" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()

function go(path: string) {
  router.push(path)
}
</script>

<style scoped>
.hero-title { font-size: 22px; font-weight: 700; color: #172033; }
.hero-note { margin-top: 8px; color: #5c6b82; line-height: 1.7; }
</style>

