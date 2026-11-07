import request from './request'
export const options = type => request.get(`/dictionaries/options/${type}`)
export const list = params => request.get('/dictionaries', { params })
export const create = data => request.post('/dictionaries', data)
export const update = (id, data) => request.put(`/dictionaries/${id}`, data)
