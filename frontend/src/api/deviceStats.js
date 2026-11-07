import request from './request'

// 设备每日运行数据（外部系统 DT / WMS → SP）

// 查询已落库的设备日报
export function list(params) {
  return request.get('/device-stats', { params })
}

// 手动补拉单日（不传 date 时后端按"昨天"处理）
// 拉取要连外部库，比普通请求慢，单独放宽超时
export function sync(date) {
  return request.post('/device-stats/sync', null, {
    params: date ? { date } : {},
    timeout: 180000
  })
}

// 按区间补拉：传 from/to，或只传 days 表示"到昨天为止的最近 N 天"
export function syncRange(params) {
  return request.post('/device-stats/sync-range', null, {
    params,
    timeout: 300000
  })
}
