/**
 * 权限落点计算。
 *
 * 背景：路由守卫在"无页面权限"时曾统一 next('/workbench')，而 /workbench 自身也要求
 * page:workbench 权限，导致 vue-router 检测到无限重定向并中止导航（白屏）。
 * 这里改为按菜单优先级挑第一个真正可访问的页面，都不可访问时落到 /403。
 */
const ENTRY_CANDIDATES = [
  { path: '/workbench', permission: 'page:workbench' },
  { path: '/approvals', permission: 'page:approval' },
  { path: '/inventory', permission: 'page:inventory' },
  { path: '/outbound', permission: 'page:outbound' },
  { path: '/outbound/picking', permission: 'outbound:picking' },
  { path: '/orders', permission: 'page:inbound' },
  { path: '/putaway', permission: 'inbound:putaway' },
  { path: '/stocktakes', permission: 'page:stocktake' },
  { path: '/spare-part-usages', permission: 'page:usage' },
  { path: '/alerts', permission: 'page:alerts' },
  { path: '/products', permission: 'page:master' },
  { path: '/locations', permission: 'page:master' },
  { path: '/users', permission: 'system:user' },
  { path: '/roles', permission: 'system:role' },
  { path: '/dictionaries', permission: 'system:dict' },
  { path: '/workflows', permission: 'system:workflow' },
  { path: '/interface-logs', permission: 'system:interface' },
  { path: '/audit-logs', permission: 'system:audit' },
  { path: '/system-configs', permission: 'system:config' }
]

export const FORBIDDEN_PATH = '/403'

/**
 * @param {(permission: string) => boolean} can 权限判断函数（通常是 authStore.can）
 * @returns {string} 第一个可访问的路径；全部不可访问时返回 /403
 */
export function firstAccessiblePath(can) {
  const hit = ENTRY_CANDIDATES.find((c) => !c.permission || can(c.permission))
  return hit ? hit.path : FORBIDDEN_PATH
}
