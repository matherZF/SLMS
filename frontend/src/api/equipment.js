import request from './request'
export const list = (params) => request.get('/equipments', { params })
export const create = (data) => request.post('/equipments', data)
export const update = (id, data) => request.put(`/equipments/${id}`, data)
export const parts = (id) => request.get(`/equipments/${id}/parts`)
// 设备编辑弹框里的关联备件分页列表
export const partsPage = (id, params) => request.get(`/equipments/${id}/parts/page`, { params })
// 设备运行数据（外部系统每日同步落表），days 默认 30
export const stats = (id, days) => request.get(`/equipments/${id}/stats`, { params: { days } })
export const devicesOfProduct = (productId) => request.get('/equipments/by-product', { params: { productId } })
export const bindPart = (id, data) => request.post(`/equipments/${id}/parts`, data)
export const updatePart = (id, partId, data) => request.put(`/equipments/${id}/parts/${partId}`, data)
export const unbindPart = (id, partId) => request.delete(`/equipments/${id}/parts/${partId}`)
export const sparePartForecasts = () => request.get('/spare-part-forecasts')
