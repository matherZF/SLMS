<template>
  <div class="app-layout">
    <!-- 顶部 Header（蓝色渐变） -->
    <header class="app-header">
      <div class="app-brand">
        <el-icon v-if="route.meta.hidden" class="back-icon" @click="router.back()"><ArrowLeft /></el-icon>
        <span v-else class="brand-mark">SP</span>
        <span class="app-title">{{ $route.meta.title || '备品备件管理' }}</span>
      </div>
      <!-- 右侧：消息中心 + 用户 + 退出 -->
      <div class="header-actions">
        <!-- 移动端铃铛：点开底部滑出的消息弹层（AlertNoticeSheet），不再用 PC 式 popover -->
        <el-badge :value="notifyCount" :hidden="notifyCount === 0" :max="99" :offset="[-2, 2]">
          <el-icon class="action-icon" @click="openNoticeSheet"><Bell /></el-icon>
        </el-badge>
        <el-icon class="action-icon" @click="goProfile"><UserFilled /></el-icon>
        <el-icon class="action-icon" @click="handleLogout"><SwitchButton /></el-icon>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="app-main">
      <slot />
    </main>

    <!-- 底部导航：全部等权 -->
    <nav class="app-nav">
      <div v-for="tab in bottomTabs" :key="tab.path" class="tab-item" :class="{ active: isActive(tab) }" @click="goTab(tab.path)">
        <span class="icon-wrap">
          <el-icon class="icon"><component :is="tab.icon" /></el-icon>
          <span v-if="tab.badge && alertUnread > 0" class="tab-badge">{{ alertUnread > 99 ? '99+' : alertUnread }}</span>
        </span>
        <span class="tab-label">{{ tab.label }}</span>
      </div>
    </nav>

    <!-- 消息通知弹层（teleport 到 body，盖住底部导航） -->
    <AlertNoticeSheet
      v-model:visible="noticeVisible"
      :items="allMessages"
      :loading="npLoading"
      :unread="notifyCount"
      @item-click="openItem"
      @read-all="readAll"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { startMessagePush, stopMessagePush, refreshMessagePush } from '@/composables/useMessagePush'
import * as notifyApi from '@/api/notifications'
import AlertNoticeSheet from '@/components/AlertNoticeSheet.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// App 端角色高频流程（底部导航）
const bottomTabs = computed(() => {
  const common = [{ path: '/workbench', label: '首页', icon: 'HomeFilled' }]
  if (authStore.canOperateWarehouse) return [...common,
    { path: '/orders', label: '入库', icon: 'Download' },
    { path: '/outbound', label: '出库', icon: 'Upload', primary: true },
    { path: '/inventory', label: '库存', icon: 'Search' },
    { path: '/alerts', label: '预警', icon: 'Bell', badge: true }]
  if (authStore.can('page:approval')) return [...common,
    { path: '/inventory', label: '库存', icon: 'Search' },
    { path: '/approvals', label: '审批', icon: 'Stamp', primary: true },
    { path: '/spare-part-usages', label: '台账', icon: 'TrendCharts' },
    { path: '/alerts', label: '预警', icon: 'Bell', badge: true }]
  return [...common,
    { path: '/inventory', label: '库存', icon: 'Search' },
    { path: '/spare-part-usages', label: '台账', icon: 'TrendCharts' },
    { path: '/alerts', label: '预警', icon: 'Bell', badge: true }]
})

// 消息中心数据（铃铛弹层）
const noticeVisible = ref(false)
const allMessages = ref([])
const notifyCount = ref(0)
const npLoading = ref(false)
const alertUnread = ref(0)

// 底部 tab 红点只在 APP 原生环境维护（useMessagePush 提供）
const showAlertEntry = computed(() => authStore.can('page:alerts'))

watch(showAlertEntry, (visible) => {
  if (visible) {
    startMessagePush({ onUnread: (n) => { alertUnread.value = n } })
  } else {
    alertUnread.value = 0
    stopMessagePush()
  }
}, { immediate: true })

watch(() => route.path, () => { refreshMessagePush(); refreshNotifyCount() })

// 打开弹层：拉全量消息列表，顺带校正角标（角标 = 未读条数）
async function openNoticeSheet() {
  noticeVisible.value = true
  npLoading.value = true
  try {
    const items = await notifyApi.list()
    allMessages.value = Array.isArray(items) ? items : []
    notifyCount.value = allMessages.value.filter(m => !m.read).length
  } catch {
    allMessages.value = []
    notifyCount.value = 0
  } finally {
    npLoading.value = false
  }
}

// 轻量刷新角标（路由切换时调用），不拉全量列表
async function refreshNotifyCount() {
  try {
    const n = await notifyApi.count({ silent: true })
    notifyCount.value = Number(n) || 0
  } catch { /* 静默：保留旧值 */ }
}

// 点某条：先标已读（红点消失、计数 -1），消息仍留在列表里，再关弹层并跳转
async function openItem(m) {
  if (!m.read) {
    m.read = true                       // 先本地落状态，点下去立刻没红点
    notifyCount.value = Math.max(0, notifyCount.value - 1)
    try { await notifyApi.markRead(m) } catch { /* 失败也不阻塞跳转，下次拉列表会纠正 */ }
    refreshMessagePush()                // 预警已读 → 顺手清掉通知栏里对应那条
  }
  noticeVisible.value = false
  router.push(m.route).catch(() => {})
}

// 全部已读：红点清零，消息全部转为已读态继续挂在弹框里
async function readAll() {
  allMessages.value.forEach(m => { m.read = true })
  notifyCount.value = 0
  try {
    await notifyApi.markAllRead()
    refreshMessagePush()
  } catch {
    await refreshNotifyCount()
  }
}

onMounted(() => { refreshNotifyCount() })

onUnmounted(() => stopMessagePush({ reset: true }))

function isActive(tab) {
  if (tab.path === '/orders' && route.path.startsWith('/orders/')) return true
  if (tab.path === '/outbound' && route.path.startsWith('/outbound/')) return true
  return route.path === tab.path
}
function goTab(path) { if (route.path !== path) router.push(path) }
function goProfile() { router.push('/profile') }
function handleLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '退出确认', { type: 'warning' })
    .then(() => { authStore.logout(); ElMessage.success('已退出登录'); router.push('/login') })
    .catch(() => {})
}
</script>

<style scoped lang="scss">
.app-layout { min-height: 100vh; background: #f4f7fb; display: flex; flex-direction: column; }
.app-header {
  min-height: 52px;
  padding: var(--safe-area-inset-top, env(safe-area-inset-top, 0px)) 16px 0;
  background: linear-gradient(135deg, #1e50d8, #2e6ef2);
  color: #fff; display: flex; align-items: center; justify-content: space-between;
  font-size: 16px; font-weight: 600; position: sticky; top: 0; z-index: 10; box-sizing: border-box;
}
.app-brand { display: flex; align-items: center; gap: 9px; height: 52px; }
.brand-mark {
  display: grid; place-items: center; width: 30px; height: 30px;
  border: 1px solid rgba(255,255,255,.45); border-radius: 8px; color: #fff; font-size: 11px; letter-spacing: 1px;
}
.back-icon { font-size: 22px; cursor: pointer; &:active { opacity: .7 } }
.app-title { font-size: 15px; }
.header-actions { display: flex; gap: 16px; min-width: 48px; justify-content: flex-end; }
.action-icon { font-size: 22px; cursor: pointer; opacity: .9; &:active { opacity: 1 } }
.app-main {
  flex: 1;
  padding-bottom: calc(60px + var(--safe-area-inset-bottom, env(safe-area-inset-bottom, 0px)));
  min-height: calc(100vh - 52px - 60px); overflow-y: auto; -webkit-overflow-scrolling: touch;
}
.app-nav {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding-bottom: var(--safe-area-inset-bottom, env(safe-area-inset-bottom, 0px));
  background: #fff; border-top: 1px solid #eef2f7;
  display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; text-align: center; z-index: 100;
}
.tab-item {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  height: 60px; font-size: 12px; color: #8a97a8; cursor: pointer; transition: color .2s;
  .icon { font-size: 23px; line-height: 1; margin-bottom: 3px }
  .icon-wrap { position: relative; display: flex; align-items: center }
  .tab-badge {
    position: absolute; top: -4px; left: 100%; transform: translateX(-40%);
    min-width: 16px; height: 16px; padding: 0 4px; box-sizing: border-box;
    border-radius: 8px; background: #e34d59; color: #fff; font-size: 10px; line-height: 16px;
    text-align: center; font-weight: 600; border: 1px solid #fff;
  }
  &.active { color: #1e54d8 }
}
</style>
