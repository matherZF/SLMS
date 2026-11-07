import request from './request'

export const list = (params) => request.get('/stocktakes', { params })
export const detail = (id) => request.get(`/stocktakes/${id}`)
export const create = (remark) => request.post('/stocktakes', { remark })
export const count = (id, itemId, actualQty) => request.put(`/stocktakes/${id}/count`, { itemId, actualQty })
export const complete = (id) => request.post(`/stocktakes/${id}/complete`)
