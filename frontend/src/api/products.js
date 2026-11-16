import request from './request'

// 货品管理 API
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

// 编码前缀 → 下一个可用完整编码（人工只填前两段，流水号自动生成）
export function nextSku(prefix) {
  return request.get('/products/next-sku', { params: { prefix } })
}

// 删除
export function remove(id) {
  return request.delete(`/products/${id}`)
}

// 批量关联设备：一个备件一次绑到多台设备（数据初始化场景）
export function bindEquipments(id, payload) {
  return request.post(`/products/${id}/equipments`, payload)
}
