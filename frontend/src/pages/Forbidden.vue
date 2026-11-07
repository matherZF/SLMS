<template>
  <div class="forbidden-wrap">
    <div class="forbidden-card">
      <div class="code">403</div>
      <h2>当前账号没有访问该页面的权限</h2>
      <p class="hint">
        你的角色是「{{ authStore.roleLabel || '未知' }}」。如需访问，请联系管理员在
        <strong>角色管理</strong>中为该角色勾选对应页面权限。
      </p>
      <div class="actions">
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">回到可访问的首页</el-button>
        <el-button link type="danger" @click="signOut">退出登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { firstAccessiblePath } from '@/router/access'

const router = useRouter()
const authStore = useAuthStore()

function goHome() {
  router.replace(firstAccessiblePath(authStore.can))
}

function goBack() {
  if (window.history.length > 1) router.back()
  else goHome()
}

function signOut() {
  authStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.forbidden-wrap {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.forbidden-card {
  max-width: 460px;
  width: 100%;
  text-align: center;
  padding: 36px 28px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.code {
  font-size: 44px;
  font-weight: 500;
  color: #f09595;
  letter-spacing: 2px;
  line-height: 1.1;
}
h2 {
  margin: 12px 0 8px;
  font-size: 16px;
  font-weight: 500;
  color: #2c2c2a;
}
.hint {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.7;
  color: #888780;
}
.hint strong {
  font-weight: 500;
  color: #5f5e5a;
}
.actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
}
</style>
