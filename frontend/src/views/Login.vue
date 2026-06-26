<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTopbar from '../components/AppTopbar.vue'
import { login, register } from '../api'
import { authUser, isAuthenticated, roleLabel } from '../auth/session'

const route = useRoute()
const router = useRouter()
const mode = ref('login')
const username = ref('')
const displayName = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

const backFallback = computed(() => typeof route.query.redirect === 'string' ? route.query.redirect : { name: 'home' })

const permissionText = computed(() => ({
  SUPER_ADMIN: '完整数据下载与权限管理',
  ADMIN: '完整数据集与单条记录下载',
  USER: '单条记录下载',
  GUEST: '浏览数据与结构'
}[authUser.value.role]))

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    if (mode.value === 'login') {
      await login({ username: username.value, password: password.value })
    } else {
      await register({ username: username.value, displayName: displayName.value, password: password.value })
    }
    password.value = ''
    goBack()
  } catch (err) {
    error.value = err.message || '认证失败'
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push(route.query.redirect || { name: 'home' })
}
</script>

<template>
  <div class="page">
    <AppTopbar show-back :back-fallback="backFallback" @brand-click="goBack" />

    <main class="shell">
      <section v-if="!isAuthenticated" class="auth-panel">
        <div class="panel-head">
          <div class="shield">VS</div>
          <div>
            <p>Account</p>
            <h1>{{ mode === 'login' ? '登录' : '创建账号' }}</h1>
          </div>
        </div>

        <div class="mode-switch">
          <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
          <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
        </div>

        <form @submit.prevent="submit">
          <label>
            <span>用户名</span>
            <input v-model="username" autocomplete="username" required placeholder="username">
          </label>
          <label v-if="mode === 'register'">
            <span>显示名称</span>
            <input v-model="displayName" autocomplete="name" placeholder="研究人员姓名">
          </label>
          <label>
            <span>密码</span>
            <input v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required placeholder="至少 8 位">
          </label>
          <p v-if="error" class="error">{{ error }}</p>
          <button class="primary" :disabled="submitting" type="submit">
            {{ submitting ? '处理中...' : (mode === 'login' ? '登录' : '注册并登录') }}
          </button>
        </form>
      </section>

      <section v-else class="account-panel">
        <div class="account-head">
          <div>
            <p>Account</p>
            <h1>{{ authUser.displayName }}</h1>
            <span>{{ roleLabel(authUser.role) }} · {{ permissionText }}</span>
          </div>
          <button class="back" @click="goBack">进入数据中心</button>
        </div>

      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

button,
input {
  font: inherit;
}

.back {
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
  cursor: pointer;
  font-weight: 800;
}

.shell {
  width: min(1040px, calc(100% - 40px));
  margin: 0 auto;
  padding: 52px 0;
}

.auth-panel,
.account-panel {
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-lg);
  background: var(--vs-card);
  box-shadow: 0 18px 44px rgba(22, 49, 102, 0.07);
}

.auth-panel {
  width: min(430px, 100%);
  margin: 0 auto;
  padding: 28px;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.shield {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: var(--vs-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 900;
}

.panel-head p,
.account-head p {
  margin: 0 0 5px;
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

h1,
h2 {
  margin: 0;
  color: var(--vs-text);
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin: 26px 0 20px;
  padding: 5px;
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-soft);
}

.mode-switch button {
  height: 39px;
  border: 0;
  border-radius: var(--vs-radius-sm);
  background: transparent;
  color: var(--vs-text-secondary);
  cursor: pointer;
  font-weight: 800;
}

.mode-switch button.active {
  background: var(--vs-card);
  color: var(--vs-primary);
  box-shadow: 0 2px 8px rgba(22, 49, 102, 0.08);
}

form {
  display: grid;
  gap: 15px;
}

label {
  display: grid;
  gap: 8px;
  color: var(--vs-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

input {
  height: 46px;
  padding: 0 13px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card-elevated);
  color: var(--vs-text);
}

.primary {
  height: 46px;
  margin-top: 5px;
  border: 1px solid var(--vs-primary);
  border-radius: var(--vs-radius-md);
  background: var(--vs-primary);
  color: #fff;
  cursor: pointer;
  font-weight: 850;
}

.primary:disabled {
  opacity: 0.55;
  cursor: wait;
}

.error {
  margin: 0;
  color: #cc5656;
  font-size: 13px;
}

.account-panel {
  padding: 28px;
}

.account-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.account-head span {
  display: block;
  margin-top: 9px;
  color: var(--vs-text-secondary);
  font-size: 14px;
}

@media (max-width: 680px) {
  .shell {
    padding: 28px 0;
  }

  .account-head {
    align-items: stretch;
    flex-direction: column;
  }

}
</style>
