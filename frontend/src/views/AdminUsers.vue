<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTopbar from '../components/AppTopbar.vue'
import { fetchMe, fetchUsers, updateUserRole } from '../api'
import { isAuthenticated, isSuperAdmin, roleLabel } from '../auth/session'

const router = useRouter()
const users = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  await fetchMe()
  if (!isAuthenticated.value) {
    router.replace({ name: 'login', query: { redirect: '/admin/users' } })
    return
  }
  if (!isSuperAdmin.value) {
    router.replace({ name: 'home' })
    return
  }
  await loadUsers()
})

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    users.value = await fetchUsers()
  } catch (err) {
    error.value = err.message || '读取用户列表失败'
  } finally {
    loading.value = false
  }
}

async function setRole(user, role) {
  error.value = ''
  try {
    await updateUserRole(user.username, role)
    await loadUsers()
  } catch (err) {
    error.value = err.message || '权限修改失败'
  }
}

function backHome() {
  router.push({ name: 'home' })
}
</script>

<template>
  <div class="page">
    <AppTopbar show-back @brand-click="backHome" />

    <main class="shell">
      <header class="heading">
        <div>
          <p class="eyebrow">Administration</p>
          <h1>用户与下载权限</h1>
          <p class="intro">管理注册用户角色和数据导出权限。超级管理员账号为系统固定角色。</p>
        </div>
        <button class="refresh" @click="loadUsers">刷新列表</button>
      </header>

      <p v-if="error" class="state error">{{ error }}</p>
      <p v-else-if="loading" class="state">正在读取用户信息...</p>

      <section v-else class="users-panel">
        <table>
          <thead>
            <tr>
              <th>用户</th>
              <th>角色</th>
              <th>可用权限</th>
              <th>角色设置</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.username" :class="{ elevated: user.role === 'SUPER_ADMIN' }">
              <td>
                <strong>{{ user.displayName }}</strong>
                <span>{{ user.username }}</span>
              </td>
              <td><em :class="`role-${user.role.toLowerCase()}`">{{ roleLabel(user.role) }}</em></td>
              <td class="capability">
                {{ user.role === 'SUPER_ADMIN' ? '批量下载 / 权限管理' : user.role === 'ADMIN' ? '批量下载 / 单条下载' : '单条下载' }}
              </td>
              <td>
                <template v-if="user.username !== 'superadmin'">
                  <button class="role" :class="{ active: user.role === 'USER' }" @click="setRole(user, 'USER')">注册用户</button>
                  <button class="role" :class="{ active: user.role === 'ADMIN' }" @click="setRole(user, 'ADMIN')">管理员</button>
                </template>
                <span v-else class="locked">固定权限</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
}

button {
  font: inherit;
}

.shell {
  width: min(1160px, calc(100% - 40px));
  margin: 0 auto;
  padding: 36px 0;
}

.back,
.refresh {
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
  cursor: pointer;
  font-weight: 800;
}

.heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 25px;
}

.heading h1 {
  margin: 4px 0 9px;
  font-size: 30px;
}

.heading p {
  margin: 0;
}

.eyebrow {
  color: var(--vs-primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.intro {
  color: var(--vs-text-secondary);
  font-size: 14px;
}

.state {
  padding: 20px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
}

.state.error {
  color: #c84d58;
}

.users-panel {
  overflow: hidden;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 15px 18px;
  border-bottom: 1px solid var(--vs-border);
  text-align: left;
  font-size: 13px;
}

th {
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  font-weight: 800;
}

tbody tr:last-child td {
  border-bottom: 0;
}

tr.elevated {
  background: color-mix(in srgb, var(--vs-primary) 4%, var(--vs-card));
}

td strong,
td span {
  display: block;
}

td span,
.locked {
  margin-top: 4px;
  color: var(--vs-text-tertiary);
  font-size: 12px;
}

td em {
  display: inline-flex;
  padding: 5px 9px;
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card-soft);
  color: var(--vs-text-secondary);
  font-style: normal;
  font-weight: 800;
}

.role-super_admin {
  background: color-mix(in srgb, var(--vs-primary) 12%, var(--vs-card)) !important;
  color: var(--vs-primary) !important;
}

.role-admin {
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card)) !important;
  color: var(--vs-primary) !important;
}

.capability {
  color: var(--vs-text-secondary);
}

.role {
  margin-right: 7px;
  padding: 7px 11px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-sm);
  background: var(--vs-card);
  color: var(--vs-primary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
}

.role.active {
  border-color: var(--vs-primary);
  background: var(--vs-primary);
  color: #fff;
}

@media (max-width: 760px) {
  .heading {
    align-items: stretch;
    flex-direction: column;
  }

  .users-panel {
    overflow-x: auto;
  }

  table {
    min-width: 700px;
  }
}
</style>
