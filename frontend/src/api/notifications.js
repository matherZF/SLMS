import request from './request'

// 站内消息中心：聚合预警、待审批
// 已读只影响红点和未读计数，消息本身继续留在列表里

export function list(params) {
  return request.get('/notifications', { params })
}

// 铃铛红点：未读条数（后端 /notifications/count）
export function count(params) {
  return request.get('/notifications/count', { params })
}

// 单条已读：传整条消息，后端优先取实时数据，源数据已消失时用这里的快照兜底
export function markRead(item) {
  return request.post('/notifications/read', {
    category: item.category,
    targetId: item.targetId,
    severity: item.severity,
    title: item.title,
    subtitle: item.subtitle,
    message: item.message,
    route: item.route,
    createdAt: item.createdAt
  }, { silent: true })
}

// 全部已读
export function markAllRead(params) {
  return request.post('/notifications/read-all', null, params)
}
