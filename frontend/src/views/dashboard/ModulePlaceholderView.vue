<template>
  <a-card :bordered="false">
    <a-result status="info" :title="title" :sub-title="description">
      <template #icon><BuildOutlined style="color: #1677ff" /></template>
      <template #extra>
        <a-space>
          <a-tag color="blue">页面框架已建立</a-tag>
          <a-tag color="orange">业务功能待实现</a-tag>
          <a-button type="primary" disabled>进入后续开发阶段</a-button>
        </a-space>
      </template>
    </a-result>
    <a-divider>页面规划基线</a-divider>
    <a-row :gutter="16">
      <a-col :span="8"><a-card size="small" title="数据边界">主责模块、统一业务主键和跨模块引用。</a-card></a-col>
      <a-col :span="8"><a-card size="small" title="状态与权限">状态机、审批门禁、数据范围和操作审计。</a-card></a-col>
      <a-col :span="8"><a-card size="small" title="链路与对账">领域事件、Outbox、幂等、补偿和日终对账。</a-card></a-col>
    </a-row>
  </a-card>
</template>

<script setup lang="ts">
import { BuildOutlined } from '@ant-design/icons-vue'
defineProps<{ title: string; description: string }>()
</script>
