/**
 * API base URL
 * —— Web 开发（Vite dev server, .env.development）  → '/api'（靠 Vite proxy）
 * —— APP 打包（build, .env.production） → 'http://后端IP:8080/api'（绝对 URL）
 *
 * 两种模式都由 Vite 在 build/dev 时静态注入，运行时零判断。
 * 换后端地址只改 .env.production 一行，重新 build + cap sync。
 */
const url = import.meta.env.VITE_API_BASE_URL
export const apiBaseURL = url || '/api'
