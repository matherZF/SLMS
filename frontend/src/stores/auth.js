import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import authApi from '@/api/auth'
import { PERMISSIONS, ROLE_META } from '@/config/permissions'

/**
 * 读取本地缓存的用户信息。
 * 缓存可能被外部改坏（手工编辑、历史版本写入过 "undefined" 等），
 * 直接 JSON.parse 抛错会让 store 初始化失败，进而整站白屏，因此必须兜住。
 */
function readCachedUser() {
  const raw = localStorage.getItem('wms_user')
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    localStorage.removeItem('wms_user')
    return null
  }
}

// 认证状态管理：token 持久化到 localStorage，用户信息缓存在内存
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('wms_token') || '')
  const user = ref(readCachedUser())

  const isLoggedIn = computed(() => !!token.value)
  const can = (permission) => (user.value?.permissions || []).includes(permission)
  const canApprove = computed(() => can(PERMISSIONS.APPROVE))
  const canApply = computed(() => can(PERMISSIONS.WAREHOUSE_OPERATE))
  const canOperateWarehouse = computed(() => ['inbound:create','inbound:putaway','outbound:create','outbound:picking','inventory:transfer','inventory:scrap','stocktake:create','stocktake:complete'].some(can))
  const roleMeta = computed(() => ROLE_META[user.value?.role] || { label: user.value?.roleName || user.value?.role || '', short: user.value?.roleName || user.value?.role || '-', tone: 'info' })
  const roleLabel = computed(() => roleMeta.value.label)
  const displayName = computed(() => user.value?.displayName || user.value?.username || '')

  /** 登录：调用后端 /api/auth/login，成功后保存 token 和用户信息 */
  async function login(username, password) {
    const res = await authApi.login(username, password)
    token.value = res.token
    user.value = {
      userId: res.userId,
      username: res.username,
      displayName: res.displayName,
      role: res.role,
      roleName: res.roleName,
      permissions: res.permissions || []
    }
    localStorage.setItem('wms_token', token.value)
    localStorage.setItem('wms_user', JSON.stringify(user.value))
    return res
  }

  /** 刷新当前用户信息：调用 /api/auth/me */
  async function fetchMe() {
    try {
      const res = await authApi.me()
      user.value = {
        userId: res.id,
        username: res.username,
        displayName: res.displayName,
        role: res.role,
        roleName: res.roleName,
        permissions: res.permissions || []
      }
      localStorage.setItem('wms_user', JSON.stringify(user.value))
    } catch {
      // token 无效则清除
      logout()
    }
  }

  /** 退出登录：清除 token 和用户信息 */
  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('wms_token')
    localStorage.removeItem('wms_user')
  }

  return { token, user, isLoggedIn, can, canApprove, canApply, canOperateWarehouse, roleMeta, roleLabel, displayName, login, fetchMe, logout }
})
