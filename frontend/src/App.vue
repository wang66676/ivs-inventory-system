<template>
  <router-view v-if="isLogin" />
  <a-layout v-else class="app-shell">
    <a-layout-sider class="app-sider" :width="248" theme="dark">
      <div class="brand">
        <div class="brand-mark">IVS</div>
        <div>
          <div class="brand-title">进销存系统</div>
          <div class="brand-subtitle">整车 · 动力电池 · 其他产品</div>
        </div>
      </div>
      <a-menu :open-keys="openKeys" theme="dark" mode="inline" :selected-keys="selectedKeys"
              @click="handleMenuClick" @openChange="handleMenuOpenChange">
        <a-menu-item key="/dashboard"><template #icon><DashboardOutlined /></template>工作台</a-menu-item>
        <a-sub-menu v-for="group in visibleGroups" :key="group.key">
          <template #icon><component :is="groupIcons[group.key]" /></template>
          <template #title>{{ group.title }}</template>
          <a-menu-item v-for="item in group.items" :key="item.path">{{ item.title }}</a-menu-item>
        </a-sub-menu>
        <a-sub-menu v-if="auth.can('USER_MANAGE')" key="system">
          <template #icon><SettingOutlined /></template>
          <template #title>系统</template>
          <a-menu-item key="/system/organizations">组织架构</a-menu-item>
          <a-menu-item key="/system/users">用户管理</a-menu-item>
          <a-menu-item key="/system/roles">角色权限</a-menu-item>
          <a-menu-item key="/system/approvals">审批配置</a-menu-item>
          <a-menu-item key="/system/settings">全局参数</a-menu-item>
          <a-menu-item key="/system/migration">数据迁移</a-menu-item>
          <a-menu-item key="/system/audit">审计日志</a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="app-header">
        <div>
          <div class="header-title">{{ pageTitle }}</div>
          <div class="header-note">商品、采购、库存、销售、实销、返利与资金池的统一入口</div>
        </div>
        <a-space>
          <a-tag color="blue">M5 完整性与上线</a-tag>
          <a-dropdown>
            <a-button type="text"><template #icon><UserOutlined /></template>{{ auth.user?.displayName || auth.user?.username }}</a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item disabled>{{ auth.user?.roles?.join(' / ') }}</a-menu-item>
                <a-menu-divider />
                <a-menu-item @click="openChangePassword">修改密码</a-menu-item>
                <a-menu-item @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </a-space>
      </a-layout-header>
      <a-layout-content class="app-content"><router-view /></a-layout-content>
    </a-layout>
  </a-layout>

  <a-modal v-model:open="passwordModalOpen" title="修改密码" :confirm-loading="passwordSaving"
           ok-text="确认修改" cancel-text="取消" width="460px" @ok="submitChangePassword">
    <a-alert type="warning" show-icon style="margin-bottom:12px"
             message="修改成功后，您在其它电脑 / 手机上的登录会被强制下线，需要用新密码重新登录（当前窗口保持登录）。" />
    <a-form layout="vertical">
      <a-form-item label="原密码"><a-input-password v-model:value="passwordForm.currentPassword" /></a-form-item>
      <a-form-item label="新密码（8-80 位）"><a-input-password v-model:value="passwordForm.newPassword" /></a-form-item>
      <a-form-item label="确认新密码"><a-input-password v-model:value="passwordForm.confirmPassword" /></a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined, BarChartOutlined, CheckSquareOutlined, DashboardOutlined, DatabaseOutlined,
  FileTextOutlined, GiftOutlined, SettingOutlined, ShopOutlined, ShoppingCartOutlined, UserOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from './stores/auth'
import { authApi } from './api/auth'
import { menuGroups, pageTitles } from './router'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const groupIcons: Record<string, unknown> = {
  masterdata: AppstoreOutlined,
  procurement: ShoppingCartOutlined,
  inventory: DatabaseOutlined,
  sales: ShopOutlined,
  retail: CheckSquareOutlined,
  policy: GiftOutlined,
  billing: FileTextOutlined,
  reports: BarChartOutlined,
}

const isLogin = computed(() => route.path === '/login')

const visibleGroups = computed(() => menuGroups
  .map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.permission || auth.can(item.permission)),
  }))
  .filter((group) => group.items.length > 0))

const openKeys = ref<string[]>(['masterdata'])
function handleMenuOpenChange(keys: (string | number)[]) {
  const list = keys.map(String)
  const newlyOpened = list.filter((key) => !openKeys.value.includes(key))
  openKeys.value = newlyOpened.length ? [newlyOpened[newlyOpened.length - 1]] : []
}

const selectedKeys = computed(() => {
  const path = route.path
  if (path.startsWith('/system/')) return [path]
  const allPaths = menuGroups.flatMap((group) => group.items.map((item) => item.path))
  const matched = allPaths
    .filter((item) => path === item || path.startsWith(item + '/'))
    .sort((a, b) => b.length - a.length)[0]
  return [matched ?? path]
})

const pageTitle = computed(() => pageTitles[selectedKeys.value[0]] ?? (route.path.startsWith('/system/') ? systemTitles[route.path] ?? '系统管理' : '工作台'))

const systemTitles: Record<string, string> = {
  '/system/organizations': '组织架构',
  '/system/users': '用户管理',
  '/system/roles': '角色权限',
  '/system/approvals': '审批配置',
  '/system/audit': '审计日志',
  '/system/migration': '数据迁移',
  '/system/settings': '全局参数',
}

function handleMenuClick(info: { key: string }) {
  router.push(info.key)
}

async function handleLogout() {
  await auth.logout()
  await router.push('/login')
}

const passwordModalOpen = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

function openChangePassword() {
  Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
  passwordModalOpen.value = true
}

async function submitChangePassword() {
  const form = passwordForm
  if (!form.currentPassword) { message.warning('请输入原密码'); return }
  if (!form.newPassword || form.newPassword.length < 8) { message.warning('新密码长度必须为 8-80 位'); return }
  if (form.newPassword !== form.confirmPassword) { message.warning('两次输入的新密码不一致'); return }
  if (form.newPassword === form.currentPassword) { message.warning('新密码不能与原密码相同'); return }
  passwordSaving.value = true
  try {
    await authApi.changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    message.success('密码已修改成功，其它设备的登录已下线')
    passwordModalOpen.value = false
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    passwordSaving.value = false
  }
}
</script>

<style scoped>
.app-shell { min-height: 100vh; }
.app-sider { position: sticky; top: 0; height: 100vh; overflow-y: auto; }
.brand { display: flex; gap: 12px; align-items: center; padding: 22px 20px; color: #fff; }
.brand-mark { display: grid; width: 44px; height: 40px; place-items: center; border-radius: 12px; background: #1677ff; font-weight: 700; font-size: 13px; }
.brand-title { font-size: 16px; font-weight: 700; }
.brand-subtitle { margin-top: 3px; color: rgba(255, 255, 255, 0.55); font-size: 12px; }
.app-header { display: flex; align-items: center; justify-content: space-between; height: 72px; padding: 0 28px; background: #fff; border-bottom: 1px solid #eef2f7; }
.header-title { color: #172033; font-size: 18px; font-weight: 700; }
.header-note { margin-top: 2px; color: #8b96a8; font-size: 12px; }
.app-content { min-height: calc(100vh - 72px); padding: 24px; background: #f5f7fb; }
</style>




