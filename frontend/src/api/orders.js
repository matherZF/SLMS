import request from './request'

// 入库单管理 API
export function list(params) {
  return request.get('/orders', { params })
}

// 待入库单列表（不分页）
export function pending() {
  return request.get('/orders/pending')
}

// 入库单详情
export function detail(id) {
  return request.get(`/orders/${id}`)
}

// 创建入库单
export function create(payload) {
  return request.post('/orders', payload)
}
