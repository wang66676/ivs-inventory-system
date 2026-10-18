<template>
  <a-space direction="vertical" size="large" style="width: 100%">
    <a-card :bordered="false" title="新建用户">
      <a-form layout="inline" :model="form" @finish="createUser">
        <a-form-item label="用户名"><a-input v-model:value="form.username" /></a-form-item>
        <a-form-item label="姓名"><a-input v-model:value="form.displayName" /></a-form-item>
        <a-form-item label="密码"><a-input-password v-model:value="form.password" /></a-form-item>
        <a-form-item label="公司"><a-select v-model:value="form.companyId" style="width: 220px" allow-clear @change="companyChanged"><a-select-option v-for="item in companies" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select></a-form-item>
        <a-form-item label="门店"><a-select v-model:value="form.storeId" style="width: 220px" allow-clear @change="storeChanged"><a-select-option v-for="item in filteredStores" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select></a-form-item>
        <a-form-item label="团队"><a-select v-model:value="form.teamId" style="width: 220px" allow-clear><a-select-option v-for="item in filteredTeams" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select></a-form-item>        <a-form-item label="角色">
          <a-select v-model:value="form.roles" mode="multiple" style="width: 260px">
            <a-select-option v-for="(label, value) in roleLabels" :key="value" :value="value">{{ label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="数据范围">
          <a-select v-model:value="form.dataScope" style="width: 150px">
            <a-select-option v-for="(label, value) in dataScopeLabels" :key="value" :value="value">{{ label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item><a-button type="primary" html-type="submit" :loading="submitting">创建用户</a-button></a-form-item>
      </a-form>
    </a-card>

    <a-card :bordered="false" title="用户列表">
      <a-table v-resizable-columns row-key="id" :data-source="users" :columns="columns" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'roles'">
            <a-space wrap>
              <a-tag v-for="role in record.roles" :key="role" color="blue">{{ roleLabel(role) }}</a-tag>
            </a-space>
          </template>
          <template v-else-if="column.key === 'dataScope'">{{ dataScopeLabel(record.dataScope) }}</template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'red'">{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="openRoleEditor(record)">角色</a-button>
              <a-button type="link" @click="openPasswordReset(record)">重置密码</a-button>
              <a-button v-if="record.status === 'ACTIVE' && record.username !== 'admin'" type="link" danger @click="toggleStatus(record, false)">停用</a-button>
              <a-button v-if="record.status === 'DISABLED'" type="link" @click="toggleStatus(record, true)">启用</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="passwordModalOpen" :title="'重置密码：' + resetTargetName" :confirm-loading="resetSaving"
             ok-text="确认重置" cancel-text="取消" width="440px" @ok="submitPasswordReset">
      <a-alert type="warning" show-icon style="margin-bottom:12px"
        message="重置后该用户的所有登录会立即失效，需要用新密码重新登录。" />
      <a-form layout="vertical">
        <a-form-item label="新密码（8-80 位）" required>
          <a-input-password v-model:value="resetForm.password" placeholder="至少 8 位，建议字母 + 数字" @press-enter="submitPasswordReset" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="roleModalOpen" title="分配角色" @ok="saveRoles">
      <a-select v-model:value="editingRoles" mode="multiple" style="width: 100%">
        <a-select-option v-for="(label, value) in roleLabels" :key="value" :value="value">{{ label }}</a-select-option>
      </a-select>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { userApi } from '../../api/user'
import { organizationApi } from '../../api/organization'
import type { OrganizationItem } from '../../types/organization'
import {
  dataScopeLabels,
  roleLabels,
  type CreatePlatformUserRequest,
  type DataScope,
  type PlatformUser,
} from '../../types/user'

const companies = ref<OrganizationItem[]>([])
const filteredStores = ref<OrganizationItem[]>([])
const filteredTeams = ref<OrganizationItem[]>([])
const users = ref<PlatformUser[]>([])
const loading = ref(false)
const submitting = ref(false)
const roleModalOpen = ref(false)
const editingUserId = ref('')
const editingRoles = ref<string[]>([])
const form = reactive<CreatePlatformUserRequest>({
  username: '',
  displayName: '',
  password: '',
  dataScope: 'SELF',
  companyId: '',
  storeId: '',
  teamId: '',
  roles: ['SALES'],
})
const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username', width: 140 },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 140 },
  { title: '角色', key: 'roles', width: 300 },
  { title: '数据范围', key: 'dataScope', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 240 },
]

async function load() {
  loading.value = true
  try {
    users.value = await userApi.list()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}


async function loadOrganizations() {
  companies.value = await organizationApi.companies()
}
async function companyChanged(value?: string) {
  form.storeId = ''
  form.teamId = ''
  filteredTeams.value = []
  filteredStores.value = value ? await organizationApi.stores(value) : []
}
async function storeChanged(value?: string) {
  form.teamId = ''
  filteredTeams.value = value ? await organizationApi.teams(value) : []
}

async function createUser() {
  submitting.value = true
  try {
    await userApi.create(form)
    message.success('用户创建成功')
    Object.assign(form, { username: '', displayName: '', password: '', dataScope: 'SELF', companyId: '', storeId: '', teamId: '', roles: ['SALES'] })
    await load()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    submitting.value = false
  }
}

/* ===== 管理员重置用户密码 ===== */
const passwordModalOpen = ref(false)
const resetSaving = ref(false)
const resetTargetId = ref('')
const resetTargetName = ref('')
const resetForm = reactive({ password: '' })

function openPasswordReset(user: PlatformUser) {
  resetTargetId.value = user.id
  resetTargetName.value = user.displayName || user.username
  resetForm.password = ''
  passwordModalOpen.value = true
}

async function submitPasswordReset() {
  if (!resetForm.password || resetForm.password.length < 8) { message.warning('新密码长度必须为 8-80 位'); return }
  resetSaving.value = true
  try {
    await userApi.resetPassword(resetTargetId.value, resetForm.password)
    message.success('密码已重置，该用户需要用新密码重新登录')
    passwordModalOpen.value = false
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    resetSaving.value = false
  }
}

function openRoleEditor(user: PlatformUser) {
  editingUserId.value = user.id
  editingRoles.value = [...user.roles]
  roleModalOpen.value = true
}

async function saveRoles() {
  try {
    await userApi.assignRoles(editingUserId.value, editingRoles.value)
    message.success('角色已更新')
    roleModalOpen.value = false
    await load()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function toggleStatus(user: PlatformUser, enable: boolean) {
  try {
    if (enable) await userApi.enable(user.id)
    else await userApi.disable(user.id)
    message.success(enable ? '用户已启用' : '用户已停用')
    await load()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function roleLabel(value: string) {
  return roleLabels[value] ?? value
}

function dataScopeLabel(value: DataScope) {
  return dataScopeLabels[value] ?? value
}

onMounted(async () => { await Promise.all([load(), loadOrganizations()]) })
</script>



