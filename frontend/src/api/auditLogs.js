import request from './request'

export function list(params) {
  return request.get('/audit-logs', { params })
}
