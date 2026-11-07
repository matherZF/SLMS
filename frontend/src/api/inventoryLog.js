import request from './request'

// 库存变动日志 API

// 分页查询库存变动日志
// 参数：sku、locationCode、changeType（INBOUND / OUTBOUND）、page、size
export function page(params) {
  return request.get('/inventory-logs', { params })
}
