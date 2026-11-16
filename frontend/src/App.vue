<template>
  <!-- 登录页不使用布局外壳 -->
  <router-view v-if="isLoginPage" />

  <!-- 桌面端布局（WEB 端，保持不变）：KeepAlive 按页签缓存页面，切回页签时保留上次状态 -->
  <DesktopLayout v-else-if="!isAppShell">
    <router-view v-slot="{ Component, route: rRoute }">
      <keep-alive :include="tagsStore.cachedNames" :max="20">
        <component :is="Component" :key="rRoute.path" />
      </keep-alive>
    </router-view>
  </DesktopLayout>

  <!-- App 外壳（APP 端 + 手机浏览器端共用，视觉一致） -->
  <AppLayout v-else>
    <router-view />
  </AppLayout>
</template>

<script setup>
import { computed, watchEffect, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DesktopLayout from '@/layouts/DesktopLayout.vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { useTagsViewStore } from '@/stores/tagsView'
import { usePlatform } from '@/composables/usePlatform'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const tagsStore = useTagsViewStore()
const { isAppShell } = usePlatform()

// 登出/token 失效后清空页签与页面缓存，避免下次登录残留上个会话的页签
watchEffect(() => {
  if (!authStore.isLoggedIn && tagsStore.views.length) tagsStore.clear()
})

// 登录页不显示布局
const isLoginPage = computed(() => route.path === '/login')

// App 外壳样式挂在 body 上：覆盖 teleport 到 body 的弹窗/提示（app.scss 以 body.app-shell 为根选择器）
watchEffect(() => {
  document.body.classList.toggle('app-shell', isAppShell.value && !isLoginPage.value)
})

// token 失效统一在这里处理：必须同时清 Pinia 状态，否则路由守卫仍认为已登录，
// 会把用户从登录页踢回工作台，形成"401 → 跳登录 → 被踢回 → 再 401"的死循环。
let redirecting = false
function handleUnauthorized() {
  if (redirecting) return
  const current = router.currentRoute.value
  authStore.logout()
  if (current.path === '/login') return
  redirecting = true
  router
    .replace({ path: '/login', query: current.fullPath ? { redirect: current.fullPath } : {} })
    .catch(() => {})
    .finally(() => { redirecting = false })
}

onMounted(() => {
  window.addEventListener('wms:unauthorized', handleUnauthorized)
  if (authStore.isLoggedIn) authStore.fetchMe()
})

onBeforeUnmount(() => window.removeEventListener('wms:unauthorized', handleUnauthorized))
</script>
