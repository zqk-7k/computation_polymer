<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchMe, logout } from '../api'
import { authUser, isAuthenticated, isSuperAdmin, roleLabel } from '../auth/session'

const props = defineProps({
  showBack: {
    type: Boolean,
    default: false
  },
  backLabel: {
    type: String,
    default: '返回上一级'
  },
  backFallback: {
    type: [Object, String],
    default: () => ({ name: 'home' })
  }
})

const emit = defineEmits(['brand-click'])
const router = useRouter()
const route = useRoute()

const THEME_KEY = 'vasp-show-theme'
const theme = ref('light')

function normalizeTheme(value) {
  return value === 'dark' ? 'dark' : 'light'
}

function storedTheme() {
  try {
    return localStorage.getItem(THEME_KEY)
  } catch {
    return null
  }
}

function saveTheme(value) {
  try {
    localStorage.setItem(THEME_KEY, value)
  } catch {
    // Ignore unavailable storage.
  }
}

function initialTheme() {
  const stored = storedTheme()
  if (stored === 'dark' || stored === 'light') return stored
  if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) return 'dark'
  return 'light'
}

function applyTheme(value) {
  const next = normalizeTheme(value)
  theme.value = next
  document.documentElement.setAttribute('data-theme', next)
  saveTheme(next)
}

function toggleTheme() {
  applyTheme(theme.value === 'dark' ? 'light' : 'dark')
}

function openLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

function openAdmin() {
  router.push({ name: 'admin-users' })
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push(props.backFallback)
}

async function signOut() {
  await logout()
  if (route.name === 'login' || route.name === 'admin-users') {
    router.push({ name: 'home' })
  }
}

onMounted(async () => {
  applyTheme(initialTheme())
  if (isAuthenticated.value) {
    try {
      await fetchMe()
    } catch {
      // The normal UI remains available if a transient session check fails.
    }
  }
})
</script>

<template>
  <header class="topbar">
    <button class="brand" @click="emit('brand-click')">
      <span class="brand-mark">VS</span>
      <span class="brand-text">VASP Show</span>
    </button>

    <slot />

    <div class="app-topbar-actions">
      <button
        v-if="showBack"
        class="back-action"
        type="button"
        @click="goBack"
      >{{ backLabel }}</button>

      <slot name="actions" />

      <button
        v-if="isSuperAdmin"
        class="admin-action"
        :class="{ active: route.name === 'admin-users' }"
        type="button"
        @click="openAdmin"
      >权限管理</button>

      <button class="app-user-card" type="button" :title="roleLabel(authUser.role)" @click="openLogin">
        <span class="app-user-avatar">{{ isAuthenticated ? authUser.displayName.slice(0, 1) : '访' }}</span>
        <span class="app-user-meta">
          <strong>{{ authUser.displayName }}</strong>
          <small>{{ roleLabel(authUser.role) }}</small>
        </span>
      </button>

      <button v-if="!isAuthenticated" class="session-action" type="button" @click="openLogin">登录</button>
      <button v-else class="session-action" type="button" @click="signOut">退出</button>

      <button
        class="theme-toggle"
        type="button"
        :aria-pressed="theme === 'dark'"
        :aria-label="theme === 'dark' ? '切换到日间模式' : '切换到夜间模式'"
        :title="theme === 'dark' ? '切换到日间模式' : '切换到夜间模式'"
        @click="toggleTheme"
      >
        <span class="theme-track">
          <span class="theme-sun" aria-hidden="true"></span>
          <span class="theme-moon" aria-hidden="true"></span>
          <span class="theme-thumb" aria-hidden="true"></span>
        </span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  height: 62px;
  background: color-mix(in srgb, var(--vs-card) 90%, transparent);
  border-bottom: 1px solid var(--vs-border);
  box-shadow: 0 1px 0 rgba(29, 33, 41, 0.02);
  backdrop-filter: blur(14px);
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 36px;
  position: sticky;
  top: 0;
  z-index: 20;
}

.brand {
  min-width: 166px;
  border: 0;
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--vs-text);
  font: inherit;
  font-weight: 800;
  flex: 0 0 auto;
  white-space: nowrap;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--vs-primary), color-mix(in srgb, var(--vs-primary) 62%, #9fb7c3));
  box-shadow: 0 8px 18px rgba(22, 93, 255, 0.22);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: var(--vs-text);
  font-size: 16px;
  line-height: 1.05;
}

.brand-text::after {
  content: "计算材料数据平台";
  color: var(--vs-text-tertiary);
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

:deep(.main-nav) {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 42px;
  min-width: 0;
}

:deep(.main-nav button) {
  height: 36px;
  padding: 0 15px;
  border: 1px solid transparent;
  border-radius: var(--vs-radius-md);
  background: transparent;
  color: var(--vs-text-secondary);
  cursor: pointer;
  font: inherit;
  font-family: inherit;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
  white-space: nowrap;
  transition: color 0.16s ease, background 0.16s ease, border-color 0.16s ease;
}

:deep(.main-nav button.active),
:deep(.main-nav button:hover) {
  border-color: var(--vs-border-strong);
  background: color-mix(in srgb, var(--vs-primary) 13%, var(--vs-card));
  color: var(--vs-primary);
}

.app-topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex: 0 0 auto;
}

.app-user-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 142px;
  height: 40px;
  padding: 0 12px 0 8px;
  border: 1px solid var(--vs-border);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-card) 86%, transparent);
  color: var(--vs-text);
  cursor: pointer;
  font: inherit;
}

.app-user-avatar {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--vs-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 900;
  line-height: 1;
}

.app-user-meta {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.app-user-meta strong {
  color: var(--vs-text);
  font-size: 13px;
  line-height: 1;
  white-space: nowrap;
}

.app-user-meta small {
  color: var(--vs-text-tertiary);
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
}

.session-action {
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: color-mix(in srgb, var(--vs-primary) 8%, var(--vs-card));
  color: var(--vs-primary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
}

.admin-action {
  height: 36px;
  padding: 0 13px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
}

.back-action {
  height: 36px;
  padding: 0 13px;
  border: 1px solid var(--vs-border-strong);
  border-radius: var(--vs-radius-md);
  background: var(--vs-card);
  color: var(--vs-text-secondary);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 800;
}

.back-action:hover {
  border-color: var(--vs-primary);
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
}

.admin-action.active,
.admin-action:hover {
  border-color: var(--vs-primary);
  background: color-mix(in srgb, var(--vs-primary) 10%, var(--vs-card));
  color: var(--vs-primary);
}

.theme-toggle {
  width: 54px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--vs-border);
  border-radius: 999px;
  background: var(--vs-card);
  cursor: pointer;
}

.theme-track {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
}

.theme-thumb,
.theme-sun,
.theme-moon {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
}

.theme-thumb {
  left: 4px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--vs-primary);
  box-shadow: 0 3px 9px rgba(22, 93, 255, 0.22);
  transition: left 0.18s ease, background 0.18s ease;
}

.theme-sun {
  left: 11px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fff7cc;
  box-shadow: 0 0 0 2px rgba(255, 247, 204, 0.48);
  z-index: 1;
}

.theme-moon {
  right: 11px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #9aa4b2;
}

.theme-moon::after {
  content: "";
  position: absolute;
  left: 4px;
  top: -1px;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: var(--vs-card);
}

:global(:root[data-theme="dark"]) .theme-thumb {
  left: 24px;
  box-shadow: 0 3px 10px rgba(116, 211, 194, 0.24);
}

:global(:root[data-theme="dark"]) .theme-moon {
  background: #f2d48f;
}

@media (max-width: 980px) {
  .topbar {
    padding: 0 24px;
  }

  .brand {
    min-width: auto;
  }

  .brand-text::after {
    display: none;
  }

  :deep(.main-nav) {
    margin-left: 24px;
  }
}

@media (max-width: 760px) {
  .topbar {
    height: 64px;
    padding: 0 16px;
    overflow-x: auto;
  }

  .app-user-card {
    min-width: 0;
    padding-right: 8px;
  }

  .app-user-meta {
    display: none;
  }

  :deep(.main-nav) {
    margin-left: 18px;
    gap: 6px;
    overflow-x: auto;
  }

  :deep(.main-nav button) {
    height: 40px;
    padding: 0 13px;
  }
}
</style>
