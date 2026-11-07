import request from './request'
export const list = () => request.get('/approval-tasks')
export const review = (row, approved, opinion = '') => request.post(`/approval-tasks/${row.businessType}/${row.businessId}/${approved ? 'approve' : 'reject'}`, { opinion })
