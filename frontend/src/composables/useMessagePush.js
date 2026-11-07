import { LocalNotifications } from '@capacitor/local-notifications'
import router from '@/router'
import * as alertApi from '@/api/alerts'

/**
 * 站内消息（备件预警）→ 手机系统通知。
 *
 * 只做「APP 端本地通知」：服务端部署在客户内网，接不了 FCM/厂商推送通道，
 * 所以由前端轮询「未读预警」并在有新增时用 Capacitor LocalNotifications 弹出系统通知。
 *
 * 关键取舍：
 * 1. 首次进入只做基线，不推送历史未读 —— 否则一装包就被几十条旧预警刷屏。
 * 2. 按 dedupKey + severity 去重存本地，同一条预警只响一次；级别从中升到高会再响一次。
 * 3. 通知 id 由 dedupKey 哈希得到，重复调度是覆盖而不是叠加。
 * 4. Android 13+ 首次进入请求一次 POST_NOTIFICATIONS，被拒后不再骚扰，角标照常工作。
 */

const CHANNEL_ID = 'wms_alert_v1'
const CHANNEL_NAME = '备件预警'
const SEEN_KEY = 'wms_push_seen_alerts'
const ASKED_KEY = 'wms_push_permission_asked'
const ALERT_ROUTE = '/alerts'
const SMALL_ICON = 'ic_stat_alert'
const ICON_COLOR = '#1E54D8'
const POLL_INTERVAL = 3 * 60 * 1000
const MAX_NOTIFICATIONS_PER_SYNC = 3
const MAX_SEEN = 400

const TYPE_TEXT = { STOCK: '库存不足', EXPIRY: '批次临期', LIFECYCLE: '更换预警', FORECAST: '需求预测' }

let started = false
let permissionGranted = false
let timer = null
let syncing = false
let actionListener = null
let visibilityHandler = null
let unreadHandler = null

function isNative() {
  return !!(typeof window !== 'undefined' && window.Capacitor?.isNativePlatform?.())
}

function readSeen() {
  try {
    const raw = localStorage.getItem(SEEN_KEY)
    if (!raw) return null
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? new Set(arr) : null
  } catch {
    return null
  }
}

function writeSeen(set) {
  try {
    // 只保留最近的若干条，防止长期使用把 localStorage 撑大
    const arr = Array.from(set).slice(-MAX_SEEN)
    localStorage.setItem(SEEN_KEY, JSON.stringify(arr))
  } catch {
    /* 存储不可用时忽略：最多重复提醒，不影响主流程 */
  }
}

/** 预警对象 + 级别：级别变化视为新消息，需要再次提醒 */
function signature(row) {
  const key = row.dedupKey
    || [row.alertType, row.productId, row.equipmentId, row.usageId, row.inventoryId].join('|')
  return `${key}#${row.severity || 'MEDIUM'}`
}

/** 32 位正整数，同一个预警对象稳定命中同一条通知 */
function notificationId(row) {
  const s = signature(row)
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return (h & 0x7fffffff) || 1
}

function titleOf(row) {
  const type = TYPE_TEXT[row.alertType] || '备件预警'
  return row.severity === 'HIGH' ? `${type} · 高优先级` : `${type} · 提醒`
}

function bodyOf(row) {
  const text = row.message || [row.productName, row.sku].filter(Boolean).join(' ')
  if (!text) return '有新的备件预警，点击查看'
  return text.length > 100 ? `${text.slice(0, 100)}…` : text
}

async function ensureChannel() {
  try {
    await LocalNotifications.createChannel({
      id: CHANNEL_ID,
      name: CHANNEL_NAME,
      description: '库存不足、批次临期、更换预警等站内提醒',
      importance: 4, // HIGH：横幅 + 声音
      visibility: 1, // 锁屏上也显示内容
      vibration: true
    })
  } catch {
    /* 渠道已存在或平台不支持：忽略 */
  }
}

async function ensurePermission() {
  try {
    const status = await LocalNotifications.checkPermissions()
    if (status.display === 'granted') return true
    // Android 13+ 只在首次进入时弹系统授权，用户拒绝后不再反复打扰
    if (localStorage.getItem(ASKED_KEY) === '1') return false
    const asked = await LocalNotifications.requestPermissions()
    localStorage.setItem(ASKED_KEY, '1')
    return asked.display === 'granted'
  } catch {
    return false
  }
}

async function scheduleNotifications(rows) {
  if (!permissionGranted || !rows.length) return false
  const picked = rows.slice(0, MAX_NOTIFICATIONS_PER_SYNC)
  const notifications = picked.map(row => ({
    id: notificationId(row),
    title: titleOf(row),
    body: bodyOf(row),
    largeBody: row.message || bodyOf(row),
    channelId: CHANNEL_ID,
    smallIcon: SMALL_ICON,
    iconColor: ICON_COLOR,
    group: 'wms_alerts',
    autoCancel: true,
    extra: { route: ALERT_ROUTE, alertId: row.id, dedupKey: row.dedupKey }
  }))

  // 一次冒出太多条会刷屏：只详细推前几条，其余合并成一条汇总
  if (rows.length > MAX_NOTIFICATIONS_PER_SYNC) {
    const rest = rows.length - MAX_NOTIFICATIONS_PER_SYNC
    notifications.push({
      id: 20261031,
      title: `另有 ${rest} 条新预警`,
      body: '点击进入预警管理查看全部',
      channelId: CHANNEL_ID,
      smallIcon: SMALL_ICON,
      iconColor: ICON_COLOR,
      group: 'wms_alerts',
      autoCancel: true,
      extra: { route: ALERT_ROUTE }
    })
  }

  try {
    await LocalNotifications.schedule({ notifications })
    return true
  } catch {
    /* 权限被系统回收或渠道异常：静默，下一次轮询再试 */
    return false
  }
}

/** 拉一次未读预警：更新角标 + 对新增项发系统通知 */
async function sync() {
  if (syncing) return
  syncing = true
  try {
    const rows = await alertApi.inbox('OPEN', { silent: true })
    if (!Array.isArray(rows)) return
    const unread = rows.filter(r => !r.readFlag)
    if (unreadHandler) unreadHandler(unread.length)

    const seen = readSeen()
    if (!seen) {
      // 首次（或换账号后重新登录）：只记录基线，不把历史未读一次性推出去
      writeSeen(new Set(unread.map(signature)))
      return
    }

    const fresh = unread.filter(r => !seen.has(signature(r)))
    if (!fresh.length) {
      // 没有新消息：集合跟着未读列表收敛，不会无限增长
      writeSeen(new Set(unread.map(signature)))
      return
    }

    // 高优先级排前面，让通知栏第一条就是要紧的那条
    fresh.sort((a, b) => (b.severity === 'HIGH' ? 1 : 0) - (a.severity === 'HIGH' ? 1 : 0))
    const pushed = await scheduleNotifications(fresh)

    // 只有真正排上通知的才算「提醒过」；推送失败（如权限未开）下次轮询继续尝试
    const freshSigs = new Set(fresh.map(signature))
    const next = new Set()
    for (const row of unread) {
      const sig = signature(row)
      if (seen.has(sig) || (pushed && freshSigs.has(sig))) next.add(sig)
    }
    writeSeen(next)
  } catch {
    /* 未登录、无 page:alerts 权限、接口异常都静默，不影响主流程 */
  } finally {
    syncing = false
  }
}

/** 浏览器端没有系统通知，只维护未读角标 */
async function refreshUnreadCount() {
  try {
    const count = await alertApi.unreadCount({ silent: true })
    if (unreadHandler) unreadHandler(count || 0)
  } catch {
    /* 未授权或接口异常：静默 */
  }
}

function handleVisibility() {
  if (document.visibilityState === 'visible') sync()
}

async function handleAction(event) {
  const route = event?.notification?.extra?.route || ALERT_ROUTE
  if (router.currentRoute.value.path === route) return
  // 冷启动点通知时，事件可能在路由初始导航完成前回调，稍等一下再跳
  setTimeout(() => { router.push(route).catch(() => {}) }, 300)
}

/**
 * 启动站内消息推送。
 * @param {(count:number)=>void} options.onUnread 未读数回调，布局用它更新红点
 */
export async function startMessagePush({ onUnread } = {}) {
  unreadHandler = onUnread || null

  if (!isNative()) {
    await refreshUnreadCount()
    if (timer) clearInterval(timer)
    timer = setInterval(refreshUnreadCount, 5 * 60 * 1000)
    return
  }

  if (started) return
  started = true

  permissionGranted = await ensurePermission()
  if (permissionGranted) await ensureChannel()

  await sync()

  if (timer) clearInterval(timer)
  timer = setInterval(() => sync(), POLL_INTERVAL)

  // WebView 切到后台后定时器会被系统挂起，回到前台立刻补一次
  visibilityHandler = handleVisibility
  document.addEventListener('visibilitychange', visibilityHandler)

  actionListener = await LocalNotifications.addListener('localNotificationActionPerformed', handleAction)
}

/** 供页面主动刷新（如刚在预警页点了「立即重算」） */
export function refreshMessagePush() {
  return isNative() ? sync() : refreshUnreadCount()
}

/** 预警被读掉/处理后，把通知栏里对应的那几条一起收掉 */
export async function dismissDeliveredAlerts(rows = []) {
  if (!isNative() || !rows.length) return
  try {
    await LocalNotifications.removeDeliveredNotifications({
      notifications: rows.map(row => ({ id: notificationId(row), title: '', body: '' }))
    })
  } catch {
    /* 通知已不存在：忽略 */
  }
}

/** 全部已读：通知栏一起清空 */
export async function clearDeliveredAlerts() {
  if (!isNative()) return
  try {
    await LocalNotifications.removeAllDeliveredNotifications()
  } catch {
    /* ignore */
  }
}

/** 退出登录 / 布局卸载时调用；reset 会清掉推送基线，换账号后不会漏推 */
export async function stopMessagePush({ reset = false } = {}) {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (visibilityHandler) {
    document.removeEventListener('visibilitychange', visibilityHandler)
    visibilityHandler = null
  }
  if (actionListener) {
    try { await actionListener.remove() } catch { /* ignore */ }
    actionListener = null
  }
  started = false
  permissionGranted = false
  unreadHandler = null
  if (reset) {
    try { localStorage.removeItem(SEEN_KEY) } catch { /* ignore */ }
  }
}
