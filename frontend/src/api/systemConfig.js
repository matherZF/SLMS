import request from './request'

export const list = () => request.get('/system-configs')
export const update = (id, configValue) => request.put(`/system-configs/${id}`, { configValue })
export const reset = id => request.post(`/system-configs/${id}/reset`)
