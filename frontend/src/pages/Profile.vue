<template>
  <div class="page-container profile-page" :class="{ 'm-page': isMobile }">
    <PageHeader>个人信息</PageHeader>

    <!-- 用户卡片 -->
    <div class="card-m user-card">
      <div class="avatar">{{ avatarChar }}</div>
      <div class="user-meta">
        <div class="user-name">{{ authStore.displayName }}</div>
        <div class="user-sub">
          <span class="m-tag" :class="isMobile ? 't-blue' : ''">{{ authStore.roleLabel }}</span>
          <span class="username">@{{ authStore.user?.username }}</span>
        </div>
      </div>
    </div>

    <!-- 基本信息修改 -->
    <div class="card-m section">
      <div class="section-title">基本信息</div>
      <el-form label-position="top" class="p-form">
        <el-form-item label="姓名">
          <el-input v-model="displayName" placeholder="请输入姓名" maxlength="64" clearable />
        </el-form-item>
        <el-form-item label="账号">
          <el-input :model-value="authStore.user?.username" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-input :model-value="authStore.roleLabel" disabled />
        </el-form-item>
      </el-form>
      <el-button type="primary" class="m-primary-btn save-btn" :loading="savingProfile" @click="saveProfile">
        保存基本信息
      </el-button>
    </div>

    <!-- 修改密码 -->
    <div class="card-m section">
      <div class="section-title">修改密码</div>
      <el-form label-position="top" class="p-form">
        <el-form-item label="原密码">
          <el-input v-model="pwd.old" type="password" show-password placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwd.next" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <el-button type="warning" class="save-btn" :loading="savingPwd" @click="savePassword">
        修改密码
      </el-button>
    </div>

    <!-- 构建标识：用于确认手机上安装的是哪个版本 -->
    <div class="build-info">客户端构建：{{ buildTime }}</div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'

const buildTime = __BUILD_TIME__
import authApi from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const { isMobile } = useMobile()
const authStore = useAuthStore()

const displayName = ref(authStore.user?.displayName || '')
const savingProfile = ref(false)

const pwd = reactive({ old: '', next: '', confirm: '' })
const savingPwd = ref(false)

const avatarChar = computed(() => (authStore.displayName || authStore.user?.username || '?').charAt(0).toUpperCase())

async function saveProfile() {
  if (!displayName.value.trim()) return ElMessage.warning('姓名不能为空')
  savingProfile.value = true
  try {
    await authApi.updateProfile({ displayName: displayName.value.trim() })
    await authStore.fetchMe()
    ElMessage.success('个人信息已更新')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  if (!pwd.old) return ElMessage.warning('请输入原密码')
  if (!pwd.next || pwd.next.length < 6) return ElMessage.warning('新密码长度至少 6 位')
  if (pwd.next !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')
  savingPwd.value = true
  try {
    await authApi.changePassword({ oldPassword: pwd.old, newPassword: pwd.next })
    ElMessage.success('密码修改成功，下次登录请使用新密码')
    pwd.old = pwd.next = pwd.confirm = ''
  } finally {
    savingPwd.value = false
  }
}
</script>

<style scoped lang="scss">
.profile-page { max-width: 720px; }

/* ---------- 移动端 ---------- */
.m-page {
  background: transparent;
  padding: 0 0 12px;
  box-shadow: none;
  border: 0;
  border-radius: 0;
  min-height: auto;
}
.user-card {
  margin: 12px 12px 0;
  padding: 16px 14px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #2e6ef2, #1e50d8);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.user-name {
  font-size: 17px;
  font-weight: 600;
  color: #1f2d3d;
}
.user-sub {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.username {
  font-size: 12px;
  color: #98a2b3;
}
.m-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  background: #eaf1ff;
  color: #2e6cf6;
}
.section {
  margin: 10px 12px 0;
  padding: 14px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #2e6ef2;
  line-height: 1.2;
}
.save-btn {
  width: 100%;
  margin: 0;
  min-height: 42px;
}
.build-info {
  margin: 4px 0 8px;
  text-align: center;
  font-size: 12px;
  color: #a4afbf;
}
</style>
