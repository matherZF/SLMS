import request from './request'

// 商品管理 API
// 分页列表：返回 Spring Page 对象 { content, totalElements, totalPages, ... }
export function list(params) {
  return request.get('/products', { params })
}

// 详情
export function detail(id) {
  return request.get(`/products/${id}`)
}

// 新增
export function create(payload) {
  return request.post('/products', payload)
}

// 更新
export function update(id, payload) {
  return request.put(`/products/${id}`, payload)
}

// 删除
export function remove(id) {
  return request.delete(`/products/${id}`)
}
