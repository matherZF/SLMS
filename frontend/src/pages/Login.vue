<template>
  <div class="login-container">
    <div class="login-scene">
      <div class="scene-kicker">SPARES LIFECYCLE MANAGEMENT SYSTEM</div>
      <h2>让每一件备件<br><span>全程可见、全程可溯</span></h2>
      <p>标准化数字台账 · 全生命周期管理 · 智能库存预警</p>
      <div class="scene-grid"><span>01 主数据标准</span><span>02 流程数字化</span><span>03 寿命跟踪</span><span>04 决策预测</span></div>
    </div>
    <div class="login-card">
      <div class="login-header">
        <div class="login-logo"><el-icon class="logo-icon"><Box /></el-icon></div>
        <h1 class="login-title">备品备件管理系统</h1>
        <p class="login-subtitle">SPARES LIFECYCLE MANAGEMENT SYSTEM</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="handleLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { firstAccessiblePath } from '@/router/access'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** 只接受站内相对路径，避免 redirect 参数被用来做开放重定向 */
function safeRedirect(raw) {
  if (typeof raw !== 'string') return ''
  if (!raw.startsWith('/') || raw.startsWith('//')) return ''
  if (raw === '/login') return ''
  return raw
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.login(form.username, form.password)
      ElMessage.success('登录成功')
      // 优先回到被拦截前的页面；没有则按当前账号权限挑第一个可访问页面
      router.replace(safeRedirect(route.query.redirect) || firstAccessiblePath(authStore.can))
    } catch {
      // 错误已由 axios 拦截器统一 toast
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(40px,8vw,120px);
  background:radial-gradient(circle at 18% 20%,rgba(14,165,233,.18),transparent 28%),linear-gradient(135deg,#061426 0%,#0a2947 55%,#0c4163 100%);
  padding: 20px;
}
.login-scene{max-width:590px;color:#dbeafe}.scene-kicker{font-size:12px;letter-spacing:3px;color:#67e8f9;font-weight:700}.login-scene h2{font-size:44px;line-height:1.25;margin:18px 0;font-weight:700}.login-scene h2 span{color:#7dd3fc}.login-scene>p{color:#94a3b8;font-size:16px}.scene-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-top:38px}.scene-grid span{padding:13px 16px;border:1px solid rgba(125,211,252,.18);background:rgba(15,58,91,.42);border-radius:8px;color:#bfdbfe;font-size:13px}
.login-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 18px;
  padding: 40px 32px;
  box-shadow: 0 30px 80px rgba(0,0,0,.32);
  border:1px solid rgba(255,255,255,.5);
}
.login-logo{width:64px;height:64px;margin:0 auto 14px;display:grid;place-items:center;border-radius:18px;background:linear-gradient(135deg,#e0f2fe,#ecfeff);box-shadow:0 8px 22px rgba(14,165,233,.18)}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-icon {
  font-size: 48px;
  color: $primary;
  margin-bottom: 0;
}
.login-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px;
}
.login-subtitle {
  font-size: 14px;
  color: #0891b2;
  letter-spacing:1px;
  font-size:10px;
  margin: 0;
}
@media(max-width:900px){.login-scene{display:none}.login-container{padding:16px}.login-card{padding:32px 24px}}
.login-btn {
  width: 100%;
  min-height: 44px;
  font-size: 16px;
}
</style>
