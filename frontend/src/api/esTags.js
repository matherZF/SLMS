import request from './request'

export const list = (params = {}) => request.get('/es-tags', { params })
export const get = (id) => request.get(`/es-tags/${id}`)
export const create = (data) => request.post('/es-tags', data)
export const update = (id, data) => request.put(`/es-tags/${id}`, data)
export const remove = (id) => request.delete(`/es-tags/${id}`)
export const bindLocation = (id, locationId) => request.post(`/es-tags/${id}/bind-location`, { locationId })
export const syncRuntime = (data) => request.post('/es-tags/runtime-sync', data)

// 从墨水屏服务器批量同步标签运行时信息（电量/在线/固件）
export const syncFromDevice = () => request.post('/es-tags/sync-from-device')

// 点灯 / 熄灭 —— 调 EslController 的快捷端点
export const lightOn = (mac, opts = {}) => request.post(`/esl/light/${encodeURIComponent(mac)}`, null, {
  params: { rgb: opts.rgb || 'ff0000', mode: opts.mode ?? 0, timeout: opts.timeout ?? 30 }
})
export const lightOff = (mac) => request.post(`/esl/light/${encodeURIComponent(mac)}/off`)
