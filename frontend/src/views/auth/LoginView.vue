<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <div class="login-brand">
        <img class="brand-mark" :src="brandLogoUrl" alt="容大进销存" />
        <div>
          <h1>进销存系统</h1>
          <p>整车 · 动力电池 · 其他产品</p>
        </div>
      </div>
      <a-form :model="form" :rules="rules" layout="vertical" @finish="submit">
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="form.username" size="large" autocomplete="username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="form.password" size="large" autocomplete="current-password" placeholder="请输入密码" @press-enter="submit" />
        </a-form-item>
        <a-button type="primary" html-type="submit" size="large" block :loading="loading">登录</a-button>
        <a-button size="large" block style="margin-top:10px" @click="licenseOpen = true">软件授权 / 购买延期</a-button>
      </a-form>
      <div class="server-address">当前登录服务：{{ serverAddress }}</div>
      <a-alert class="login-hint" type="info" show-icon message="首次使用请用随软件附带的《使用说明》中的默认账号登录，并尽快在「用户管理」中新建自己的账号。局域网客户端必须填写主机服务器地址，并使用主机端创建的账号。" />
    </a-card>
    <a-modal v-model:open="licenseOpen" title="软件授权与授权文件" footer="" width="680px">
      <LicensePanel />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '../../stores/auth'
import { licenseApi } from '../../api/license'
import LicensePanel from '../../components/LicensePanel.vue'

const brandLogoUrl = import.meta.env.BASE_URL + 'logo.png'
const serverAddress = window.location.origin

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const licenseOpen = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  loading.value = true
  try {
    await auth.login(form)
    message.success('登录成功')
    await router.push('/dashboard')
  } catch (error) {
    const errorMessage = (error as Error).message
    if (errorMessage.includes('试用期已结束')) licenseOpen.value = true
    message.error(errorMessage === '用户名或密码错误'
      ? '用户名或密码错误。局域网登录请确认客户端地址指向主机，并使用主机端“用户管理”中创建的账号。'
      : errorMessage)
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  try {
    const status = await licenseApi.status()
    if (status.state === 'EXPIRED') licenseOpen.value = true
  } catch { /* 登录服务不可达时由登录按钮给出提示 */ }
})
</script>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  background: linear-gradient(135deg, #e8f1ff 0%, #f5f7fb 55%, #edf8f3 100%);
}

.login-card {
  width: 420px;
  border-radius: 18px;
  box-shadow: 0 18px 60px rgba(30, 64, 120, 0.14);
}

.login-brand {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 28px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  padding: 5px;
  box-sizing: border-box;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
  object-fit: contain;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 4px 0 0;
  color: #8b96a8;
}

.server-address {
  margin: 14px 0 10px;
  color: #6b7280;
  font-size: 12px;
  text-align: center;
}

.login-hint {
  margin-top: 18px;
}
</style>
