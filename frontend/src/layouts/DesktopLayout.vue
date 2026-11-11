<template>
  <el-container class="desktop-layout">
    <!-- 左侧菜单 -->
    <el-aside width="220px" class="aside">
      <div class="logo">
        <el-icon class="logo-icon"><Box /></el-icon>
        <span class="logo-text">备品备件管理</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :default-openeds="defaultOpeneds"
        router
        background-color="transparent"
        text-color="#a9bdd2"
        active-text-color="#67e8f9"
        unique-opened
      >
        <el-menu-item v-if="authStore.can('page:workbench')" index="/workbench">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu v-if="authStore.can('page:inbound') || authStore.can('page:outbound') || authStore.can('page:approval') || authStore.can('page:stocktake')" index="operations">
          <template #title>
            <el-icon><Operation /></el-icon><span>业务作业</span>
          </template>
          <el-menu-item v-if="authStore.can('page:inbound')" index="/orders"><el-icon><Download /></el-icon><span>入库与上架</span></el-menu-item>
          <el-menu-item v-if="authStore.can('page:outbound')" index="/outbound"><el-icon><Upload /></el-icon><span>领用与出库</span></el-menu-item>
          <el-menu-item v-if="authStore.can('page:approval')" index="/approvals"><el-icon><Stamp /></el-icon><span>审批中心</span></el-menu-item>
          <el-menu-item v-if="authStore.can('page:stocktake')" index="/stocktakes"><el-icon><Finished /></el-icon><span>库存盘点</span></el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="authStore.can('page:inventory') || authStore.can('page:usage') || authStore.can('page:alerts')" index="ledger">
          <template #title><el-icon><DataAnalysis /></el-icon><span>数字台账</span></template>
          <el-menu-item v-if="authStore.can('page:inventory')" index="/inventory"><el-icon><Search /></el-icon><span>库存台账</span></el-menu-item>
          <el-menu-item v-if="authStore.can('page:usage')" index="/spare-part-usages"><el-icon><TrendCharts /></el-icon><span>使用台账</span></el-menu-item>
          <el-menu-item v-if="authStore.can('page:inventory')" index="/inventory/logs"><el-icon><Document /></el-icon><span>库存流水</span></el-menu-item>
          <el-menu-item v-if="authStore.can('page:alerts')" index="/alerts"><el-icon><Bell /></el-icon><span>预警中心</span><el-badge v-if="unread > 0" :value="unread" :max="99" class="menu-badge" /></el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="authStore.can('page:master')" index="basic">
          <template #title><el-icon><Files /></el-icon><span>基础数据</span></template>
          <el-menu-item index="/products">
            <el-icon><Goods /></el-icon><span>备件主数据</span>
          </el-menu-item>
          <el-menu-item index="/locations"><el-icon><Location /></el-icon><span>仓库与库位</span></el-menu-item>
          <el-menu-item index="/equipments"><el-icon><Cpu /></el-icon><span>设备管理</span></el-menu-item>
          <el-menu-item index="/es-tags"><el-icon><MagicStick /></el-icon><span>电子标签</span></el-menu-item>
        </el-sub-menu>

        <!-- 5. 系统管理（仅管理员） -->
        <el-sub-menu v-if="authStore.can('system:user') || authStore.can('system:role') || authStore.can('system:workflow') || authStore.can('system:audit') || authStore.can('system:dict') || authStore.can('system:interface')" index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item v-if="authStore.can('system:user')" index="/users">
            <el-icon><UserFilled /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item v-if="authStore.can('system:role')" index="/roles"><el-icon><Key /></el-icon><span>角色管理</span></el-menu-item>
          <el-menu-item v-if="authStore.can('system:workflow')" index="/workflows"><el-icon><Share /></el-icon><span>流程配置</span></el-menu-item>
          <el-menu-item v-if="authStore.can('system:audit')" index="/audit-logs">
            <el-icon><List /></el-icon>
            <span>系统日志</span>
          </el-menu-item>
          <el-menu-item v-if="authStore.can('system:dict')" index="/dictionaries"><el-icon><Collection /></el-icon><span>数据字典</span></el-menu-item>
          <el-menu-item v-if="authStore.can('system:interface')" index="/interface-logs"><el-icon><Connection /></el-icon><span>接口管理</span></el-menu-item>
          <el-menu-item v-if="authStore.can('system:config')" index="/system-configs"><el-icon><Setting /></el-icon><span>系统配置</span></el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部 Header -->
      <el-header height="60px" class="header">
        <div class="header-left">
          <div><span class="sys-title">备品备件全生命周期管理</span><span class="environment-dot">内网运行</span></div>
        </div>
        <div class="header-right">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/workbench' }">工作台</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">
              {{ $route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <!-- 用户下拉菜单 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              <span class="user-name">{{ authStore.displayName }}</span>
              <el-tag size="small" :type="authStore.roleMeta.tone" effect="plain">
                {{ authStore.roleMeta.short }}
              </el-tag>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 个人信息弹框（编辑自己的姓名 / 修改自己的密码） -->
      <el-dialog v-model="profileVisible" title="个人信息" width="480px" :close-on-click-modal="false">
        <el-form label-width="90px">
          <el-form-item label="用户名">
            <el-input :model-value="authStore.user?.username" disabled />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="profileForm.displayName" placeholder="请输入姓名" maxlength="64" />
          </el-form-item>
          <el-form-item label="角色">
            <el-input :model-value="authStore.roleLabel" disabled />
          </el-form-item>
          <el-divider content-position="left" style="width:100%;margin:8px 0 16px">修改密码（留空则不修改）</el-divider>
          <el-form-item label="原密码">
            <el-input v-model="profileForm.oldPwd" type="password" show-password placeholder="修改密码时必填" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="profileForm.newPwd" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="profileForm.confirmPwd" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="profileVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">确定</el-button>
        </template>
      </el-dialog>

      <!-- 主内容 -->
      <el-main class="main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import authApi from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import * as alertApi from '@/api/alerts'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// ---------- 站内消息：预警未读数 ----------
const unread = ref(0)
let unreadTimer = null
async function loadUnread() {
  try { unread.value = await alertApi.unreadCount({ silent: true }) } catch { /* 未授权时静默，不影响主流程 */ }
}
onMounted(() => {
  if (!authStore.can('page:alerts')) return
  loadUnread()
  // 预警是每天定时算的，轮询只是为了让处理后角标尽快消失
  unreadTimer = setInterval(loadUnread, 5 * 60 * 1000)
})
onUnmounted(() => { if (unreadTimer) clearInterval(unreadTimer) })

// ---------- 个人信息弹框 ----------
const profileVisible = ref(false)
const savingProfile = ref(false)
const profileForm = reactive({ displayName: '', oldPwd: '', newPwd: '', confirmPwd: '' })

function openProfile() {
  profileForm.displayName = authStore.user?.displayName || ''
  profileForm.oldPwd = profileForm.newPwd = profileForm.confirmPwd = ''
  profileVisible.value = true
}

async function saveProfile() {
  const name = profileForm.displayName.trim()
  if (!name) return ElMessage.warning('姓名不能为空')

  const wantPwd = profileForm.oldPwd || profileForm.newPwd || profileForm.confirmPwd
  if (wantPwd) {
    if (!profileForm.oldPwd) return ElMessage.warning('修改密码请先输入原密码')
    if (!profileForm.newPwd || profileForm.newPwd.length < 6) return ElMessage.warning('新密码长度至少 6 位')
    if (profileForm.newPwd !== profileForm.confirmPwd) return ElMessage.warning('两次输入的新密码不一致')
  }

  savingProfile.value = true
  try {
    if (name !== (authStore.user?.displayName || '')) {
      await authApi.updateProfile({ displayName: name })
    }
    if (wantPwd) {
      await authApi.changePassword({ oldPassword: profileForm.oldPwd, newPassword: profileForm.newPwd })
      ElMessage.success('密码修改成功，下次登录请使用新密码')
    }
    await authStore.fetchMe()
    profileVisible.value = false
    ElMessage.success('个人信息已保存')
  } finally {
    savingProfile.value = false
  }
}

// 根据当前路由自动展开对应的父级子菜单
const defaultOpeneds = computed(() => {
  const p = route.path
  if (p.startsWith('/orders') || p.startsWith('/outbound') || p.startsWith('/approvals') || p.startsWith('/stocktakes')) return ['operations']
  if (p.startsWith('/inventory') || p.startsWith('/spare-part-usages') || p.startsWith('/alerts')) return ['ledger']
  if (p.startsWith('/products') || p.startsWith('/locations') || p.startsWith('/equipments') || p.startsWith('/es-tags')) return ['basic']
  if (p.startsWith('/users') || p.startsWith('/audit-logs') || p.startsWith('/dictionaries') || p.startsWith('/roles') || p.startsWith('/workflows') || p.startsWith('/interface-logs')) return ['system']
  return []
})

function handleCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '退出确认', { type: 'warning' })
      .then(() => {
        authStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
  } else if (cmd === 'profile') {
    openProfile()
  }
}
</script>

<style scoped lang="scss">
.menu-badge { margin-left: 8px; }
:deep(.menu-badge .el-badge__content) { border: 0; }
.desktop-layout {
  height: 100vh;
  min-height: 100vh;
}
.aside {
  background: linear-gradient(180deg,#071426 0%,#0b2442 58%,#0c3152 100%);
  color: #fff;
  overflow-x: hidden;
  :deep(.el-menu) {
    border-right: none;
  }
  :deep(.el-menu-item),:deep(.el-sub-menu__title){margin:3px 10px;width:calc(100% - 20px);border-radius:8px;height:46px;line-height:46px}
  :deep(.el-menu-item:hover),:deep(.el-sub-menu__title:hover){background:rgba(56,189,248,.09)!important;color:#e0f2fe}
  :deep(.el-menu-item.is-active){background:linear-gradient(90deg,rgba(14,165,233,.22),rgba(6,182,212,.08))!important;box-shadow:inset 2px 0 #38bdf8}
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: #fff;
  border-bottom: 1px solid rgba(103,232,249,.12);
  background:rgba(4,15,29,.25);
}
.logo-icon {
  font-size: 24px;
  color: #38bdf8;
}
.logo-text {
  font-size: 16px;
  font-weight: 600;
}
.header {
  background: rgba(255,255,255,.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(148,163,184,.18);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.sys-title {
  font-size: 16px;
  font-weight: 600;
  color: #10233f;
}
.environment-dot{margin-left:12px;padding:3px 9px;border-radius:20px;background:#ecfeff;color:#0891b2;font-size:11px;font-weight:600}
.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
  color: #606266;
  font-size: 13px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
  .user-name {
    font-size: 14px;
    font-weight: 500;
  }
}
.main {
  background: radial-gradient(circle at 85% 0,rgba(14,165,233,.08),transparent 28%),#f4f7fb;
  min-height: calc(100vh - 60px);
  padding: 20px;
  overflow-y: auto;
}
</style>
