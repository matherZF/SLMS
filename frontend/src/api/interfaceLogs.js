import request from './request'

export function page(params) {
  return request.get('/interface-logs', { params })
}

export function detail(id) {
  return request.get(`/interface-logs/${id}`)
}
