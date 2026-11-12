<template>
  <a-space direction="vertical" size="large" style="width: 100%">
    <a-card :bordered="false" title="角色权限">
      <a-alert type="info" show-icon
        message="按角色勾选可用的功能权限，点该角色卡片右上角的「保存」立即生效。"
        description="权限改动对已登录用户会在其「刷新页面或重新登录」后生效。系统管理员角色必须保留「用户、角色权限与系统配置管理」，否则会被锁在系统外。" />
      <div style="margin-top:12px"><a-button @click="load" :loading="loading">刷新</a-button></div>
    </a-card>

    <a-card v-for="role in roles" :key="role.roleCode" :bordered="false" :title="role.roleName + '（' + role.roleCode + '）'">
      <template #extra>
        <a-space>
          <a-button size="small" @click="checkAll(role.roleCode)">全选</a-button>
          <a-button size="small" @click="clearAll(role.roleCode)">清空</a-button>
          <a-button type="primary" size="small" :loading="savingRole === role.roleCode" @click="save(role)">保存</a-button>
        </a-space>
      </template>
      <a-checkbox-group v-model:value="checked[role.roleCode]" style="width:100%">
        <div v-for="group in groups" :key="group.group" class="perm-group">
          <div class="perm-group-title">{{ group.group }}</div>
          <a-checkbox v-for="item in group.permissions" :key="item.code" :value="item.code" class="perm-item">
            {{ item.label }}<span class="perm-code">{{ item.code }}</span>
          </a-checkbox>
        </div>
      </a-checkbox-group>
    </a-card>
  </a-space>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { roleApi } from '../../api/role'
import type { PermissionGroup, RolePermission } from '../../types/role'

const roles = ref<RolePermission[]>([])
const groups = ref<PermissionGroup[]>([])
const checked = reactive<Record<string, string[]>>({})
const loading = ref(false)
const savingRole = ref('')

async function load() {
  loading.value = true
  try {
    const catalog = await roleApi.catalog()
    roles.value = catalog.roles
    groups.value = catalog.groups
    roles.value.forEach(role => { checked[role.roleCode] = [...role.permissions] })
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function checkAll(roleCode: string) {
  checked[roleCode] = groups.value.flatMap(group => group.permissions.map(item => item.code))
}

function clearAll(roleCode: string) {
  checked[roleCode] = []
}

async function save(role: RolePermission) {
  savingRole.value = role.roleCode
  try {
    const updated = await roleApi.update(role.roleCode, checked[role.roleCode] ?? [])
    checked[role.roleCode] = [...updated.permissions]
    const index = roles.value.findIndex(item => item.roleCode === role.roleCode)
    if (index >= 0) roles.value[index] = updated
    message.success(role.roleName + ' 的权限已保存（用户刷新页面后生效）')
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    savingRole.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.perm-group { margin-bottom: 10px; }
.perm-group-title { margin-bottom: 4px; color: #1677ff; font-size: 13px; font-weight: 600; }
.perm-item { display: inline-flex; margin: 0 24px 6px 0; }
.perm-code { margin-left: 6px; color: #b0b7c3; font-size: 11px; }
</style>
