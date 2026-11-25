<template>
  <div>
    <a-card :bordered="false" title="软件授权">
      <a-alert type="info" show-icon style="margin-bottom:16px"
               message="新安装软件默认试用 30 天。试用到期后，请把软件码、授权用户名和购买期限发送给作者，收到 ivs-license.lic 后导入并重启软件。授权页面仅管理员可见。" />
      <LicensePanel @activated="onActivated" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import LicensePanel from '../../components/LicensePanel.vue'

function onActivated() { message.success('授权状态已更新') }
</script>
