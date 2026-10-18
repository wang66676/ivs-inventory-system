<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <div class="login-brand">
        <div class="brand-mark">TC</div>
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
      </a-form>
      <a-alert class="login-hint" type="info" show-icon message="首次使用请用随软件附带的《使用说明》中的默认账号登录，并尽快在「用户管理」中新建自己的账号。" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
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
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
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
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border-radius: 16px;
  background: #1677ff;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 4px 0 0;
  color: #8b96a8;
}

.login-hint {
  margin-top: 18px;
}
</style>
