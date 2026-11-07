import request from './request'

// 用户管理 API（仅 ADMIN 角色可访问）
export default {
  /**
   * 用户下拉选项（任意已登录用户可访问）
   * 只返回启用用户的 username / displayName，供领用人等选择控件使用
   */
  options() {
    return request.get('/user-options')
  },
  /** 分页查询用户列表 */
  list(params = {}) {
    return request.get('/users', { params })
  },
  /** 获取用户详情 */
  detail(id) {
    return request.get(`/users/${id}`)
  },
  /** 创建新用户 */
  create(payload) {
    return request.post('/users', payload)
  },
  /** 修改用户信息（password 为可选字段） */
  update(id, payload) {
    return request.put(`/users/${id}`, payload)
  },
  /** 删除用户 */
  remove(id) {
    return request.delete(`/users/${id}`)
  }
}
