import { computed, reactive } from 'vue'

const STORAGE_KEY = 'vasp-show-session'

function loadStoredSession() {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    return null
  }
}

export const authState = reactive({
  session: loadStoredSession()
})

export const authUser = computed(() => authState.session?.user || {
  username: '',
  displayName: '游客',
  role: 'GUEST',
  authenticated: false
})

export const isAuthenticated = computed(() => Boolean(authUser.value.authenticated))
export const canDownloadSingle = computed(() => isAuthenticated.value)
export const canDownloadDataset = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(authUser.value.role))
export const isSuperAdmin = computed(() => authUser.value.role === 'SUPER_ADMIN')
export const isAdminOrSuperAdmin = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(authUser.value.role))

export function setSession(session) {
  authState.session = session
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Storage can be unavailable in private browser contexts.
  }
}

export function clearSession() {
  authState.session = null
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Ignore unavailable storage.
  }
}

export function authHeaders() {
  const token = authState.session?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export function roleLabel(role) {
  return {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    USER: '注册用户',
    GUEST: '游客'
  }[role] || role
}
