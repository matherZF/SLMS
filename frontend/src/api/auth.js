import request from './request'

// 认证相关 API
export default {
  /** 登录：POST /api/auth/login { username, password } → { token, userId, username, displayName, role } */
  login(username, password) {
    return request.post('/auth/login', { username, password })
  },
  /** 获取当前登录用户信息：GET /api/auth/me → UserDTO */
  me() {
    return request.get('/auth/me')
  },
  /** 修改个人信息（姓名）：PUT /api/auth/profile { displayName } → UserDTO */
  updateProfile(payload) {
    return request.put('/auth/profile', payload)
  },
  /** 修改密码：PUT /api/auth/password { oldPassword, newPassword } */
  changePassword(payload) {
    return request.put('/auth/password', payload)
  }
}
