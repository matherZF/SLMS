import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { firstAccessiblePath, FORBIDDEN_PATH } from './access'

const SYS_NAME = '备品备件管理系统'

// 使用 hash 模式，便于 jar 部署时不需要 history fallback 配置
const routes = [
  { path: '/', redirect: '/workbench' },
  {
    path: FORBIDDEN_PATH,
    name: 'Forbidden',
    component: () => import('@/pages/Forbidden.vue'),
    // 不设 permission：这里是"无权限"的落点，本身必须始终可达
    meta: { title: '无访问权限', hidden: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/products',
    name: 'Products',
    component: () => import('@/pages/Products.vue'),
    meta: { title: '货品管理', icon: 'Goods', permission: 'page:master' }
  },
  {
    path: '/locations',
    name: 'Locations',
    component: () => import('@/pages/Locations.vue'),
    meta: { title: '库位管理', icon: 'Location', permission: 'page:master' }
  },
  {
    path: '/workbench',
    name: 'Workbench',
    component: () => import('@/pages/Workbench.vue'),
    meta: { title: '工作台', icon: 'HomeFilled', permission: 'page:workbench' }
  },
  {
    path: '/approvals',
    name: 'Approvals',
    component: () => import('@/pages/Approvals.vue'),
    meta: { title: '审批中心', icon: 'Stamp', permission: 'page:approval' }
  },
  { path: '/equipments', name: 'Equipment', component: () => import('@/pages/Equipment.vue'), meta: { title: '设备管理', icon: 'Cpu', permission: 'page:master' } },
  { path: '/es-tags', name: 'EsTags', component: () => import('@/pages/EsTags.vue'), meta: { title: '电子标签', icon: 'MagicStick', permission: 'page:master' } },
  { path: '/alerts', name: 'Alerts', component: () => import('@/pages/Alerts.vue'), meta: { title: '预警管理', icon: 'Bell', permission: 'page:alerts' } },
  { path: '/forecast', name: 'Forecast', component: () => import('@/pages/Forecast.vue'), meta: { title: '需求预测', icon: 'DataLine', permission: 'page:alerts' } },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('@/pages/Orders.vue'),
    meta: { title: '入库管理', icon: 'Tickets', permission: 'page:inbound' }
  },
  {
    path: '/orders/:id',
    name: 'OrderDetail',
    component: () => import('@/pages/OrderDetail.vue'),
    meta: { title: '入库单详情', hidden: true }
  },
  {
    path: '/putaway',
    name: 'Putaway',
    component: () => import('@/pages/Putaway.vue'),
    meta: { title: '上架作业', icon: 'Check', primary: true, permission: 'inbound:putaway' }
  },
  {
    path: '/outbound',
    name: 'Outbound',
    component: () => import('@/pages/Outbound.vue'),
    meta: { title: '出库管理', icon: 'Box', permission: 'page:outbound' }
  },
  {
    path: '/outbound/:id',
    name: 'OutboundDetail',
    component: () => import('@/pages/OutboundDetail.vue'),
    meta: { title: '领用单详情', hidden: true, permission: 'page:outbound' }
  },
  {
    path: '/outbound/picking',
    redirect: '/outbound/picking/list'
  },
  {
    path: '/outbound/picking/:id',
    name: 'Picking',
    component: () => import('@/pages/Picking.vue'),
    meta: { title: '拣货作业', hidden: true, primary: true, permission: 'outbound:picking' }
  },
  {
    path: '/inventory',
    name: 'Inventory',
    component: () => import('@/pages/Inventory.vue'),
    meta: { title: '库存查询', icon: 'DataAnalysis', permission: 'page:inventory' }
  },
  {
    path: '/inventory/logs',
    name: 'InventoryLog',
    component: () => import('@/pages/InventoryLog.vue'),
    meta: { title: '库存日志', icon: 'Document', permission: 'page:inventory' }
  },
  {
    path: '/spare-part-usages',
    name: 'SparePartUsages',
    component: () => import('@/pages/SparePartUsages.vue'),
    meta: { title: '备件使用台账', icon: 'TrendCharts', permission: 'page:usage' }
  },
  {
    path: '/stocktakes',
    name: 'Stocktake',
    component: () => import('@/pages/Stocktake.vue'),
    meta: { title: '库存盘点', icon: 'Finished', permission: 'page:stocktake' }
  },
  {
    path: '/users',
    name: 'Users',
    component: () => import('@/pages/Users.vue'),
    meta: { title: '用户管理', icon: 'UserFilled', permission: 'system:user' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/pages/Profile.vue'),
    meta: { title: '个人信息', hidden: true }
  },
  ,{
    path: '/audit-logs',
    name: 'AuditLogs',
    component: () => import('@/pages/AuditLogs.vue'),
    meta: { title: '系统日志', icon: 'List', permission: 'system:audit' }
  }
  ,{
    path: '/dictionaries', name: 'Dictionaries', component: () => import('@/pages/Dictionaries.vue'),
    meta: { title: '数据字典', icon: 'Collection', permission: 'system:dict' }
  },{
    path: '/roles', name: 'Roles', component: () => import('@/pages/Roles.vue'),
    meta: { title: '角色管理', icon: 'Key', permission: 'system:role' }
  },{
    path: '/workflows', name: 'Workflows', component: () => import('@/pages/Workflows.vue'),
    meta: { title: '流程配置', icon: 'Share', permission: 'system:workflow' }
  },{
    path: '/interface-logs', name: 'InterfaceLogs', component: () => import('@/pages/InterfaceLogs.vue'),
    meta: { title: '接口管理', icon: 'Connection', permission: 'system:interface' }
  },{
    path: '/system-configs', name: 'SystemConfig', component: () => import('@/pages/SystemConfig.vue'),
    meta: { title: '系统配置', icon: 'Setting', permission: 'system:config' }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 全局前置守卫：未登录跳转登录页，页面与作业入口按后端返回的动态权限控制
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    if (authStore.isLoggedIn && to.path === '/login') {
      return next(firstAccessiblePath(authStore.can))
    }
    return next()
  }

  if (!authStore.isLoggedIn) {
    return next({ path: '/login', query: { redirect: to.fullPath } })
  }

  // 无权限时落到 /403（该路由本身不要求权限，因此不会二次触发重定向）。
  // 这里加一道防御：若目标已是 /403 仍不通过，直接中止导航，避免 vue-router 无限重定向告警。
  if (to.meta.permission && !authStore.can(to.meta.permission)) {
    if (to.path === FORBIDDEN_PATH) return next(false)
    return next(FORBIDDEN_PATH)
  }

  next()
})

// 页面标题动态设置
router.afterEach((to) => {
  const title = to.meta?.title
  if (title) {
    document.title = `${title} - ${SYS_NAME}`
  }
})

export default router
