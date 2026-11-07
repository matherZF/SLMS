export const PERMISSIONS = {
  APPROVE: 'outbound:approve',
  WAREHOUSE_OPERATE: 'outbound:create',
  MASTER_DATA_MANAGE: 'master:edit',
  USER_MANAGE: 'system:user',
  ROLE_MANAGE: 'system:role',
  WORKFLOW_MANAGE: 'system:workflow',
  DICTIONARY_MANAGE: 'system:dict',
  AUDIT_VIEW: 'system:audit'
}

export const ROLE_META = {
  ADMIN: { label: '系统管理员', short: '管理员', tone: 'danger' },
  APPROVER: { label: '业务审批人', short: '审批人', tone: 'warning' },
  WAREHOUSE: { label: '仓库业务员', short: '仓库', tone: 'primary' },
  VIEWER: { label: '只读用户', short: '只读', tone: 'info' }
}
