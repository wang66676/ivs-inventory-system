<template>
  <div>
    <a-card :bordered="false" title="软件授权">
      <a-alert type="info" show-icon style="margin-bottom:16px"
               message="新安装软件默认试用 30 天。试用到期前或到期后均可使用本机安装码生成注册码完成离线激活。授权页面仅管理员可见。" />
      <LicensePanel @activated="onActivated" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import LicensePanel from '../../components/LicensePanel.vue'

function onActivated() { message.success('授权状态已更新') }
</script>
