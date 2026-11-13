import request from './request'
export const list = () => request.get('/approval-tasks')
export const review = (row, approved, opinion = '') => request.post(`/approval-tasks/${row.businessType}/${row.businessId}/${approved ? 'approve' : 'reject'}`, { opinion })
// 已办结审批历史：管理员看全部，普通用户看自己审批过的 + 自己申请的
export const history = (params) => request.get('/approval-tasks/history', { params })
