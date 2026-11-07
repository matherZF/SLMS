import axios from 'axios'
import { ElMessage } from 'element-plus'
import { apiBaseURL } from './baseURL'

// axios 实例：统一 baseURL 与超时
const request = axios.create({
  baseURL: apiBaseURL,
  timeout: 10000
})

// 请求拦截器：自动携带 JWT token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('wms_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理 Result 结构 { code, msg, data }
let lastUnauthorizedAt = 0

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        // 成功：直接返回 data 字段，调用侧无需再拆 Result
        return body.data
      } else {
        // 登录态失效：后端 SecurityConfig 返回 code=401；旧版后端仅靠文案，保留 msg 匹配兜底
        const unauthorized = body.code === 401 || body.msg === '未登录或登录已过期'
        if (unauthorized) {
          localStorage.removeItem('wms_token')
          localStorage.removeItem('wms_user')
          // 交给 App.vue 统一处理：先 authStore.logout() 再 router.replace('/login')。
          // 这里不能直接改 hash —— 只清 localStorage 而 Pinia 里仍有 token，
          // 会让路由守卫把用户从 /login 又踢回工作台，形成 401 死循环。
          const now = Date.now()
          if (now - lastUnauthorizedAt > 2000) {
            lastUnauthorizedAt = now
            ElMessage.error('登录已过期，请重新登录')
            window.dispatchEvent(new CustomEvent('wms:unauthorized'))
          }
          return Promise.reject(body)
        }
        // 业务失败：统一 toast 并 reject（轮询类请求用 silent 关掉，避免无网络时每几分钟弹一次）
        if (!res.config?.silent) ElMessage.error(body.msg || '请求失败')
        return Promise.reject(body)
      }
    }
    // 非标准 Result 直接返回
    return body
  },
  (error) => {
    // HTTP 层错误：把 axios 的英文技术文案翻译成可读提示
    let msg = error.response?.data?.msg
    if (!msg) {
      if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
        msg = '请求超时，请检查网络后重试'
      } else if (!error.response) {
        msg = '网络不可用，请检查网络连接'
      } else {
        msg = '服务异常，请稍后重试'
      }
    }
    if (!error.config?.silent) ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
