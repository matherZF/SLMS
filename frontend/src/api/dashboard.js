import request from './request'
export const summary = () => request.get('/dashboard/summary')
