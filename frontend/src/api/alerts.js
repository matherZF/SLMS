import request from './request'

// 预警读的是预警表（每天 09:00 定时算一次并落表），不是每次实时算
// config 透传 axios 配置：轮询调用传 { silent: true }，失败时不弹 toast
export const inbox = (status, config = {}) => request.get('/alerts', { params: { status }, ...config })
// 立即重算并落表：改完参数想马上看结果时用
export const refresh = () => request.post('/alerts/refresh')
// 实时算、不落表：核对「现在到底什么情况」
export const live = () => request.get('/alerts/live')
export const unreadCount = (config = {}) => request.get('/alerts/unread-count', config)
export const markRead = (id) => request.post(`/alerts/${id}/read`)
export const markAllRead = () => request.post('/alerts/read-all')
export const acknowledge = (id) => request.post(`/alerts/${id}/ack`)
export const ignore = (id) => request.post(`/alerts/${id}/ignore`)
