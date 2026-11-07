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
        <a-sub-menu v-if="auth.can('USER_MANAGE') || auth.can('IVS_READ')" key="system">
          <template #icon><SettingOutlined /></template>
          <template #title>系统</template>
          <a-menu-item key="/system/approval-tasks">审批待办</a-menu-item>
          <template v-if="auth.can('USER_MANAGE')">
            <a-menu-item key="/system/organizations">组织架构</a-menu-item>
            <a-menu-item key="/system/users">用户管理</a-menu-item>
            <a-menu-item key="/system/roles">角色权限</a-menu-item>
            <a-menu-item key="/system/approvals">审批配置</a-menu-item>
            <a-menu-item key="/system/settings">全局参数</a-menu-item>
            <a-menu-item key="/system/update">在线更新</a-menu-item>
            <a-menu-item key="/system/migration">数据迁移</a-menu-item>
            <a-menu-item key="/system/audit">审计日志</a-menu-item>
          </template>
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
          <a-badge :count="unreadNotificationCount" :overflow-count="99">
            <a-button type="text" title="站内通知" @click="openNotifications"><BellOutlined /></a-button>
          </a-badge>
          <a-tag color="blue">M5 完整性与上线</a-tag>
          <a-dropdown>
            <a-button type="text"><template #icon><UserOutlined /></template>{{ auth.user?.displayName || auth.user?.username }}</a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item disabled>{{ auth.user?.roles?.join(' / ') }}</a-menu-item>
                <a-menu-divider />
                <a-menu-item @click="openModuleSettings">模块显示设置</a-menu-item>
                <a-menu-item @click="router.push('/system/update')">检查软件更新</a-menu-item>
                <a-menu-item @click="openChangePassword">修改密码</a-menu-item>
                <a-menu-item @click="handleLogout">退出登录</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </a-space>
      </a-layout-header>
      <a-layout-content class="app-content"><router-view :key="$route.fullPath" /></a-layout-content>
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

  <a-modal v-model:open="moduleSettingsOpen" title="模块显示设置" ok-text="完成" cancel-text="取消"
           width="560px" @ok="moduleSettingsOpen = false">
    <a-alert type="info" show-icon style="margin-bottom:16px"
             message="重复内容模块可在此隐藏；隐藏后仅从左侧菜单移除，已有权限和业务数据不受影响。" />
    <div v-for="page in visibleHideablePages" :key="page.path" class="module-visibility-row">
      <div>
        <div class="module-title">{{ page.title }}</div>
        <div class="module-note">{{ page.description }}</div>
      </div>
      <a-switch :checked="!hiddenMenuPaths.includes(page.path)" checked-children="显示" un-checked-children="隐藏"
                @change="handleMenuVisibilityChange(page.path, $event)" />
    </div>
    <a-button @click="showAllModules">恢复显示全部模块</a-button>
  </a-modal>

  <a-drawer v-model:open="notificationOpen" title="站内通知" :width="440">
    <template #extra>
      <a-space>
        <a-button size="small" @click="router.push('/system/approval-tasks'); notificationOpen = false">审批待办</a-button>
        <a-button size="small" :disabled="!unreadNotificationCount" @click="markAllNotificationsRead">全部已读</a-button>
      </a-space>
    </template>
    <a-list :data-source="notifications" :loading="notificationLoading" locale="暂无通知">
      <template #renderItem="{ item }">
        <a-list-item class="notification-item" @click="readNotification(item)">
          <a-list-item-meta>
            <template #title>
              <a-space><a-badge v-if="!item.read" status="processing" /><span :class="{ unread: !item.read }">{{ item.title }}</span></a-space>
            </template>
            <template #description>
              <div>{{ item.content }}</div>
              <div class="notification-time">{{ formatNotificationTime(item.createdAt) }}</div>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined, BarChartOutlined, BellOutlined, CheckSquareOutlined, DashboardOutlined, DatabaseOutlined,
  FileTextOutlined, GiftOutlined, SettingOutlined, ShopOutlined, ShoppingCartOutlined, UserOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from './stores/auth'
import { authApi } from './api/auth'
import { notificationApi } from './api/approval'
import { hideablePages, menuGroups, pageTitles } from './router'
import type { NotificationItem } from './types/approval'

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
const hiddenMenuPaths = ref<string[]>([])
const moduleSettingsOpen = ref(false)
const hiddenMenuStorageKey = computed(() => `ivs.hidden-menus.${auth.user?.username || 'anonymous'}`)
const visibleHideablePages = computed(() => hideablePages.filter((page) =>
  !page.permission || auth.can(page.permission)))

const visibleGroups = computed(() => menuGroups
  .map((group) => ({
    ...group,
    items: group.items.filter((item) => (!item.permission || auth.can(item.permission))
      && !hiddenMenuPaths.value.includes(item.path)),
  }))
  .filter((group) => group.items.length > 0))

function loadHiddenMenus() {
  if (typeof localStorage === 'undefined') return
  try {
    const stored = JSON.parse(localStorage.getItem(hiddenMenuStorageKey.value) || '[]')
    const validPaths = new Set(hideablePages.map((page) => page.path))
    hiddenMenuPaths.value = Array.isArray(stored) ? stored.filter((path): path is string => validPaths.has(path)) : []
  } catch {
    hiddenMenuPaths.value = []
  }
}

function persistHiddenMenus() {
  localStorage.setItem(hiddenMenuStorageKey.value, JSON.stringify(hiddenMenuPaths.value))
}

function setMenuVisible(path: string, visible: boolean) {
  hiddenMenuPaths.value = visible
    ? hiddenMenuPaths.value.filter((item) => item !== path)
    : [...new Set([...hiddenMenuPaths.value, path])]
  persistHiddenMenus()
}

function handleMenuVisibilityChange(path: string, checked: boolean | string | number) {
  setMenuVisible(path, Boolean(checked))
}

function showAllModules() {
  hiddenMenuPaths.value = []
  persistHiddenMenus()
}

function openModuleSettings() {
  moduleSettingsOpen.value = true
}

const notificationOpen = ref(false)
const notificationLoading = ref(false)
const notifications = ref<NotificationItem[]>([])
const unreadNotificationCount = ref(0)
let notificationTimer: ReturnType<typeof setInterval> | undefined

async function loadNotifications() {
  if (!auth.token) return
  notificationLoading.value = true
  try {
    const [items, count] = await Promise.all([notificationApi.list(false), notificationApi.unreadCount()])
    notifications.value = items
    unreadNotificationCount.value = count
  } catch {
    // 页面主体不应因通知接口短暂失败而中断
  } finally {
    notificationLoading.value = false
  }
}

async function openNotifications() {
  notificationOpen.value = true
  await loadNotifications()
}

async function readNotification(item: NotificationItem) {
  if (item.read) return
  try {
    await notificationApi.markRead(item.id)
    await loadNotifications()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function markAllNotificationsRead() {
  try {
    await notificationApi.markAllRead()
    await loadNotifications()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function formatNotificationTime(value: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

watch(hiddenMenuStorageKey, loadHiddenMenus, { immediate: true })
onMounted(() => {
  loadHiddenMenus()
  loadNotifications()
  notificationTimer = setInterval(loadNotifications, 30000)
})
onBeforeUnmount(() => {
  if (notificationTimer) clearInterval(notificationTimer)
})

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
  '/system/approval-tasks': '审批待办',
  '/system/audit': '审计日志',
  '/system/migration': '数据迁移',
  '/system/settings': '全局参数',
  '/system/update': '在线更新',
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
.app-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; height: 72px; padding: 0 28px; background: #fff; border-bottom: 1px solid #eef2f7; line-height: normal; }
.app-header > :first-child { flex: 1; min-width: 0; }
.app-header > .ant-space { flex-shrink: 0; }
.header-title { color: #172033; font-size: 18px; line-height: 1.4; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.header-note { margin-top: 2px; color: #8b96a8; font-size: 12px; line-height: 1.45; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.app-content { min-height: calc(100vh - 72px); padding: 24px; background: #f5f7fb; }
.module-visibility-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 0; border-bottom: 1px solid #eef2f7; }
.module-title { color: #172033; font-weight: 600; }
.module-note { margin-top: 3px; color: #8b96a8; font-size: 12px; line-height: 1.5; }
.notification-item { cursor: pointer; padding-inline: 4px; }
.notification-item:hover { background: #f7f9fc; }
.notification-item .unread { font-weight: 600; color: #172033; }
.notification-time { margin-top: 5px; color: #98a2b3; font-size: 12px; }
</style>





