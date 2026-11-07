import request from './request'

export function list(params) {
  return request.get('/spare-part-usages', { params })
}

export function detail(id) {
  return request.get(`/spare-part-usages/${id}`)
}

export function refresh(id) {
  return request.post(`/spare-part-usages/${id}/refresh`)
}

export function refreshAll() {
  return request.post('/spare-part-usages/refresh-all')
}

export function snapshots(id) {
  return request.get(`/spare-part-usages/${id}/snapshots`)
}

export function end(id, reason) {
  return request.post(`/spare-part-usages/${id}/end`, { reason })
}
