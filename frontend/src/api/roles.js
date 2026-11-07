import request from './request'
export default {
  list: () => request.get('/roles'),
  permissions: () => request.get('/roles/permissions'),
  create: data => request.post('/roles', data),
  update: (id, data) => request.put(`/roles/${id}`, data),
  remove: id => request.delete(`/roles/${id}`)
}
