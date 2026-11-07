import request from './request'

// 上架作业确认 API
// payload: { orderId, locationId, qty }
// 返回: { orderId, putQty, remaining, done(boolean), msg }
export function confirm(payload) {
  return request.post('/putaway/confirm', payload)
}
