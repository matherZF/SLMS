<template>
  <div class="m-home" v-loading="loading">
    <!-- 蓝色渐变 Hero：问候 + 搜索 + 快捷入口 -->
    <section class="hero">
      <div class="hero-top">
        <div class="hero-greet">
          <span class="hello">你好，{{ auth.displayName }}</span>
          <span class="role-chip">{{ auth.roleLabel }}</span>
        </div>
        <span class="hero-sub">备品备件数字化管理</span>
      </div>
      <div class="search-pill" @click="router.push('/inventory')">
        <el-icon><Search /></el-icon>
        <span>搜索备件 / SKU / 库位</span>
      </div>
      <div class="hero-actions">
        <button v-for="a in heroActions" :key="a.path" @click="router.push(a.path)">
          <el-icon :size="26"><component :is="a.icon" /></el-icon>
          <span>{{ a.label }}</span>
        </button>
      </div>
    </section>

    <!-- 通知条 -->
    <div v-if="notice" class="notice" @click="router.push(notice.path)">
      <el-icon class="notice-icon"><BellFilled /></el-icon>
      <span class="notice-text">{{ notice.text }}</span>
      <span class="notice-more">查看<i class="arrow" /></span>
    </div>

    <!-- 服务宫格 -->
    <section class="card grid-card">
      <button v-for="s in services" :key="s.path + s.label" class="svc" @click="router.push(s.path)">
        <span class="svc-icon" :style="{ background: s.bg, color: s.color }">
          <el-icon :size="22"><component :is="s.icon" /></el-icon>
        </span>
        <span class="svc-label">{{ s.label }}</span>
      </button>
    </section>

    <!-- 数据概览 -->
    <section class="card stats-card">
      <button v-for="st in stats" :key="st.label" @click="router.push(st.path)">
        <strong>{{ st.value }}</strong>
        <span>{{ st.label }}</span>
      </button>
    </section>

    <!-- 业务待办 -->
    <section v-if="todos.length" class="card todo-card">
      <div class="card-title">业务待办</div>
      <div v-for="t in todos" :key="t.label" class="todo-row" @click="router.push(t.path)">
        <span>{{ t.label }}</span>
        <span class="badge" :class="t.tone">{{ t.count }}</span>
      </div>
      <div class="export-row">
        <el-button size="small" plain :loading="exportingInventory" @click="exportInventory">导出库存明细</el-button>
        <el-button size="small" plain :loading="exportingLogs" @click="exportLogs">导出库存流水</el-button>
      </div>
    </section>

    <!-- 近期库存变动 -->
    <section class="card log-card">
      <div class="card-title">近期库存变动<span class="more" @click="router.push('/inventory/logs')">全部<i class="arrow" /></span></div>
      <div v-for="(row, i) in (data.recentLogs || []).slice(0, 6)" :key="i" class="log-row">
        <div class="log-main">
          <span class="log-name">{{ row.productName }}</span>
          <span class="log-sub">{{ row.sku }} · {{ row.locationCode }} · {{ row.operator }}</span>
        </div>
        <div class="log-right">
          <b :class="row.afterQty >= row.beforeQty ? 'up' : 'down'">{{ row.afterQty >= row.beforeQty ? '+' : '-' }}{{ row.changeQty }}</b>
          <span class="log-time">{{ shortTime(row.createdAt) }}</span>
        </div>
      </div>
      <div v-if="!(data.recentLogs || []).length" class="log-empty">暂无库存变动</div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as dashboardApi from '@/api/dashboard'
import * as inventoryApi from '@/api/inventory'
import * as logApi from '@/api/inventoryLog'

const router = useRouter(), auth = useAuthStore(), loading = ref(false)
const data = reactive({ recentLogs: [] })
const exportingInventory = ref(false), exportingLogs = ref(false)

// Hero 快捷入口（最多 4 个，按权限）
const heroActions = computed(() => {
  const list = []
  if (auth.canOperateWarehouse) {
    list.push({ path: '/outbound', label: '领用出库', icon: 'Upload' },
      { path: '/orders', label: '入库上架', icon: 'Download' },
      { path: '/stocktakes', label: '库存盘点', icon: 'Finished' })
  }
  if (auth.can('page:approval')) list.push({ path: '/approvals', label: '审批中心', icon: 'Stamp' })
  list.push({ path: '/inventory', label: '库存查询', icon: 'Search' })
  return list.slice(0, 4)
})

// 服务宫格（鄂汇办式彩色图标，按权限过滤）
// m: false 表示该服务不在移动端宫格展示（系统管理/低频配置类，桌面端操作更合适）
const services = computed(() => {
  const list = [
    { path: '/orders', label: '入库管理', icon: 'Download', color: '#2e6cf6', bg: '#eaf1ff', perm: 'page:inbound' },
    { path: '/outbound', label: '领用出库', icon: 'Upload', color: '#e34d59', bg: '#fdeaea', perm: 'page:outbound' },
    { path: '/outbound/picking/list', label: '拣货作业', icon: 'Van', color: '#f5722c', bg: '#fef0e6', perm: 'outbound:picking' },
    { path: '/inventory', label: '库存台账', icon: 'Search', color: '#12b76a', bg: '#e6f7ef' },
    { path: '/inventory/logs', label: '库存流水', icon: 'List', color: '#0aa5a8', bg: '#e3f7f7' },
    { path: '/stocktakes', label: '库存盘点', icon: 'Finished', color: '#7c5cf0', bg: '#efeaff', perm: 'page:stocktake' },
    { path: '/approvals', label: '审批中心', icon: 'Stamp', color: '#d9a400', bg: '#fbf3da', perm: 'page:approval' },
    { path: '/alerts', label: '预警中心', icon: 'Bell', color: '#e34d59', bg: '#fdeaea', perm: 'page:alerts' },
    { path: '/products', label: '备件主数据', icon: 'Goods', color: '#2e6cf6', bg: '#eaf1ff', perm: 'page:master' },
    { path: '/locations', label: '库位管理', icon: 'Location', color: '#0aa5a8', bg: '#e3f7f7', perm: 'page:master' },
    { path: '/spare-part-usages', label: '使用台账', icon: 'TrendCharts', color: '#12b76a', bg: '#e6f7ef' },
    { path: '/es-tags', label: '电子标签', icon: 'MagicStick', color: '#7c5cf0', bg: '#efeaff', perm: 'page:master' },
    { path: '/equipments', label: '设备数据', icon: 'Cpu', color: '#f5722c', bg: '#fef0e6', perm: 'page:master', m: false },
    { path: '/users', label: '用户管理', icon: 'User', color: '#2e6cf6', bg: '#eaf1ff', perm: 'system:user', m: false },
    { path: '/roles', label: '角色权限', icon: 'Key', color: '#d9a400', bg: '#fbf3da', perm: 'system:role', m: false },
    { path: '/workflows', label: '流程配置', icon: 'SetUp', color: '#7c5cf0', bg: '#efeaff', perm: 'system:workflow', m: false },
    { path: '/dictionaries', label: '数据字典', icon: 'Collection', color: '#0aa5a8', bg: '#e3f7f7', perm: 'system:dict', m: false },
    { path: '/audit-logs', label: '系统日志', icon: 'Document', color: '#e34d59', bg: '#fdeaea', perm: 'system:audit', m: false }
  ]
  return list.filter(s => s.m !== false && (!s.perm || auth.can(s.perm)))
})

const stats = computed(() => [
  { label: '库存总量', value: data.totalQty || 0, path: '/inventory' },
  { label: '有货备件', value: data.skuCount || 0, path: '/inventory' },
  { label: '在用库位', value: data.locationCount || 0, path: '/inventory' },
  { label: '临期批次', value: data.expiryCount || 0, path: '/alerts' }
])

// 待办列表
const todos = computed(() => {
  const list = []
  if (auth.can('page:approval') && data.pendingApproval) list.push({ label: '待办审批', count: data.pendingApproval, path: '/approvals', tone: 'warn' })
  if (auth.can('page:approval') && data.overdueApproval) list.push({ label: '超时审批', count: data.overdueApproval, path: '/approvals', tone: 'danger' })
  if (auth.can('outbound:picking') && data.pendingPicking) list.push({ label: '待拣货领用单', count: data.pendingPicking, path: '/outbound/picking/list', tone: 'danger' })
  if (auth.can('page:stocktake') && data.stocktakeCounting) list.push({ label: '进行中盘点', count: data.stocktakeCounting, path: '/stocktakes', tone: 'info' })
  // 站内提醒优先显示未读条数；没有未读再显示待处理总数
  if (auth.can('page:alerts') && data.alertUnread) list.push({ label: '未读预警提醒', count: data.alertUnread, path: '/alerts', tone: 'danger' })
  else if (auth.can('page:alerts') && data.alertCount) list.push({ label: '库存与更换预警', count: data.alertCount, path: '/alerts', tone: 'info' })
  return list
})

// 顶部通知条：取最高优先级待办
const notice = computed(() => {
  if (auth.can('outbound:picking') && data.pendingPicking) return { text: `您有 ${data.pendingPicking} 张待拣货领用单，请及时处理`, path: '/outbound/picking/list' }
  if (auth.can('page:approval') && data.overdueApproval) return { text: `有 ${data.overdueApproval} 笔审批已超时，请尽快处理`, path: '/approvals' }
  if (auth.can('page:approval') && data.pendingApproval) return { text: `您有 ${data.pendingApproval} 笔业务待审批`, path: '/approvals' }
  if (auth.can('page:alerts') && data.alertUnread) return { text: `您有 ${data.alertUnread} 条未读的备件预警提醒，请查看`, path: '/alerts' }
  if (auth.can('page:alerts') && data.alertCount) return { text: `备件预警共 ${data.alertCount} 条待处理`, path: '/alerts' }
  return null
})

async function load() {
  loading.value = true
  try { Object.assign(data, await dashboardApi.summary()) } finally { loading.value = false }
}

function csvCell(value) { let text = value == null ? '' : String(value); if (/^[=+\-@]/.test(text)) text = "'" + text; return `"${text.replaceAll('"', '""')}"` }
function downloadCsv(name, headers, rows) { const content = '\ufeff' + [headers, ...rows].map(row => row.map(csvCell).join(',')).join('\r\n'); const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url) }
async function exportInventory() { exportingInventory.value = true; try { const rows = await inventoryApi.details({}); downloadCsv(`库存明细_${dateText()}.csv`, ['SKU', '备件名称', '库位', '区域', '数量', '批次', '生产日期', '到期日', '生产厂商'], rows.map(r => [r.sku, r.productName, r.locationCode, r.area, r.qty, r.itemKey, r.productionDate, r.expiryDate, r.manufacturer])); ElMessage.success('库存明细已导出') } finally { exportingInventory.value = false } }
async function exportLogs() { exportingLogs.value = true; try { const result = await logApi.page({ page: 0, size: 10000 }); const rows = result.content || []; downloadCsv(`库存流水_${dateText()}.csv`, ['时间', '类型', 'SKU', '备件名称', '库位', '变动数量', '变动前', '变动后', '关联单号', '操作人', '备注'], rows.map(r => [formatTime(r.createdAt), r.changeType, r.sku, r.productName, r.locationCode, r.changeQty, r.beforeQty, r.afterQty, r.refNo, r.operator, r.remark])); ElMessage.success(`已导出 ${rows.length} 条流水`) } finally { exportingLogs.value = false } }
function dateText() { const d = new Date(); return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}` }
function formatTime(v) { return v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '' }
function shortTime(v) { if (!v) return ''; const d = new Date(v); return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }

onMounted(load)
</script>

<style scoped lang="scss">
.m-home { padding-bottom: 12px; }

// ---------- Hero ----------
.hero {
  background: linear-gradient(178deg, #2e6ef2 0%, #3f83f8 100%);
  padding: 16px 16px 52px;
  color: #fff;
}
.hero-top { display: flex; justify-content: space-between; align-items: flex-end; }
.hero-greet { display: flex; align-items: center; gap: 8px; }
.hello { font-size: 19px; font-weight: 700; }
.role-chip { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: rgba(255,255,255,.22); }
.hero-sub { font-size: 11px; opacity: .75; }
.search-pill {
  margin-top: 14px; min-height: 38px; display: flex; align-items: center; gap: 8px;
  background: #fff; border-radius: 19px; padding: 0 14px;
  color: #9aa7b8; font-size: 13px; cursor: pointer;
}
.hero-actions { margin-top: 18px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.hero-actions button {
  border: 0; background: transparent; color: #fff; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; gap: 6px; font-size: 12px;
}

// ---------- 通知条 ----------
.notice {
  margin: -36px 12px 0; position: relative; z-index: 1;
  min-height: 40px; display: flex; align-items: center; gap: 8px;
  background: #fff; border-radius: 10px; padding: 8px 12px;
  box-shadow: 0 4px 14px rgba(30, 80, 200, .10); cursor: pointer;
}
.notice-icon { color: #f5722c; flex-shrink: 0; }
.notice-text { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-more { font-size: 12px; color: #2e6cf6; display: flex; align-items: center; }

// ---------- 卡片通用 ----------
.card {
  margin: 12px; background: #fff; border-radius: 14px; padding: 14px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
}
.card-title { font-size: 15px; font-weight: 700; color: #1e293b; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
.more { font-size: 12px; font-weight: 400; color: #9aa7b8; display: flex; align-items: center; }

// ---------- 服务宫格 ----------
.grid-card { display: grid; grid-template-columns: repeat(4, 1fr); gap: 18px 4px; margin-top: -30px; padding-top: 20px; }
.grid-card:first-of-type { margin-top: -30px; }
.notice + .grid-card { margin-top: 12px; }
.svc { border: 0; background: transparent; cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 7px; }
.svc-icon { width: 44px; height: 44px; border-radius: 13px; display: grid; place-items: center; }
.svc-label { font-size: 12px; color: #475569; }

// ---------- 数据概览 ----------
.stats-card { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding: 12px; }
.stats-card button { border: 0; background: #f6f9ff; border-radius: 10px; padding: 10px 4px; display: flex; flex-direction: column; align-items: center; gap: 3px; cursor: pointer; }
.stats-card strong { font-size: 19px; color: #1e54d8; }
.stats-card span { font-size: 11px; color: #8a97a8; }

// ---------- 待办 ----------
.todo-row { min-height: 44px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #f1f5f9; cursor: pointer; font-size: 14px; color: #334155; }
.todo-row:last-of-type { border-bottom: 0; }
.badge { min-width: 22px; height: 22px; border-radius: 11px; padding: 0 7px; display: grid; place-items: center; font-size: 12px; font-weight: 700; color: #fff; }
.badge.warn { background: #f59e0b; }
.badge.danger { background: #e34d59; }
.badge.info { background: #2e6cf6; }
.export-row { display: flex; gap: 10px; margin-top: 12px; .el-button { flex: 1; margin: 0; } }

// ---------- 近期变动 ----------
.log-row { display: flex; justify-content: space-between; align-items: center; padding: 9px 0; border-bottom: 1px solid #f1f5f9; }
.log-row:last-child { border-bottom: 0; }
.log-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.log-name { font-size: 14px; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.log-sub { font-size: 11px; color: #94a3b8; }
.log-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; flex-shrink: 0; margin-left: 10px; }
.log-right b { font-size: 15px; }
.up { color: #12b76a; } .down { color: #e34d59; }
.log-time { font-size: 11px; color: #b6c0cd; }
.log-empty { text-align: center; color: #b6c0cd; font-size: 13px; padding: 18px 0; }
</style>
