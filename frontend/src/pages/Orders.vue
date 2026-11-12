<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">
      入库管理
      <template #desc>到货登记与上架跟踪；双击单据行可直接打开详情</template>
    </PageHeader>

    <!-- 快捷入口：移动端宫格 / 桌面端大卡 -->
    <section v-if="isMobile" class="m-grid card-m">
      <button v-for="item in quickActions" :key="item.title" class="m-svc" @click="item.action()">
        <span class="m-svc-icon" :style="{ background: item.bg, color: item.color }">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </span>
        <span class="m-svc-label">{{ item.title }}</span>
      </button>
    </section>
    <section v-else class="quick-section">
      <div class="quick-section-head"><span>快捷入口</span><small>根据当前角色展示可用功能</small></div>
      <div class="quick-grid">
        <el-card v-for="item in quickActions" :key="item.title" class="quick-card" shadow="never" @click="item.action()">
          <div class="quick-card-body">
            <div class="quick-icon" :class="item.tone"><el-icon :size="25"><component :is="item.icon" /></el-icon></div>
            <div class="quick-info"><div class="quick-title">{{ item.title }}</div><div class="quick-sub">{{ item.desc }}</div></div>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </div>
        </el-card>
      </div>
    </section>

    <!-- 过滤栏：移动端胶囊 + 全宽搜索 / 桌面端原版 -->
    <div v-if="isMobile" class="m-filter card-m">
      <div class="m-chips">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          class="m-chip"
          :class="{ on: filters.status === opt.value }"
          @click="setStatus(opt.value)"
        >{{ opt.label }}</button>
        <span class="m-total">共 {{ total }} 单</span>
      </div>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索货品 SKU / 名称"
        clearable
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button v-if="auth.can('inbound:create')" class="m-create-btn" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>&nbsp;新建入库单
      </el-button>
    </div>
    <div v-else class="filter-bar">
      <el-select v-model="filters.status" placeholder="状态筛选" style="width: 160px" clearable @change="onStatusChange">
        <el-option label="全部" value="" />
        <el-option label="待上架" value="PENDING" />
        <el-option label="已完成" value="DONE" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="货品 SKU / 名称"
        clearable
        style="width: 240px"
        @keyup.enter="loadData"
      />
      <el-button type="primary" @click="loadData">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="resetFilters">
        <el-icon><RefreshLeft /></el-icon>&nbsp;重置
      </el-button>
      <el-button v-if="auth.can('inbound:create')" type="success" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>&nbsp;新建入库单
      </el-button>
    </div>

    <!-- 数据表格：桌面表格 / App 壳卡片列表 -->
    <div v-if="isMobile" v-loading="loading" class="m-card-list">
      <el-empty v-if="!loading && list.length === 0" description="暂无入库单" :image-size="70" />
      <article v-for="row in list" :key="row.id" class="m-card" @click="goDetail(row)">
        <div class="m-card-head">
          <b class="m-no">{{ row.orderNo }}</b>
          <span class="m-tag" :class="row.status === 'DONE' ? 'done' : 'pending'">
            {{ row.status === 'DONE' ? '已完成' : '待上架' }}
          </span>
        </div>
        <div class="m-line"><span class="m-label">货品</span><span class="m-val">{{ row.sku }} - {{ row.productName }}</span></div>
        <div class="m-line"><span class="m-label">上架进度</span><span class="m-val"><b>{{ row.putQty }}</b> / {{ row.planQty }}（{{ row.progress || 0 }}%）</span></div>
        <div class="m-line"><span class="m-label">创建时间</span><span class="m-val">{{ formatTime(row.createdAt) }}</span></div>
        <el-progress
          :percentage="row.progress || 0"
          :color="row.status === 'DONE' ? '#12b76a' : '#2e6ef2'"
          :show-text="false"
          :stroke-width="6"
          style="margin-top: 10px"
        />
      </article>
    </div>

    <el-table
      v-else
      v-loading="loading"
      :data="list"
      stripe
      border
      class="order-table"
      style="width: 100%"
      @row-dblclick="row => goDetail(row)"
    >
      <el-table-column prop="orderNo" label="单号" min-width="170" />
      <el-table-column label="货品" min-width="220">
        <template #default="{ row }">
          <b>{{ row.sku }}</b>
          <span style="color:#909399; margin-left:6px">-</span>
          <span style="margin-left:6px">{{ row.productName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划/已上架" width="130" align="center">
        <template #default="{ row }">
          {{ row.planQty }} / <b>{{ row.putQty }}</b>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="row.progress || 0"
            :status="row.status === 'DONE' ? 'success' : undefined"
          />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'DONE' ? 'success' : 'info'" effect="light">
            {{ row.status === 'DONE' ? '已完成' : '待上架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="goDetail(row)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        :layout="isMobile ? 'total, prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
        :small="isMobile"
        :pager-count="isMobile ? 5 : 7"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        v-model:current-page="page.page"
        v-model:page-size="page.size"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 详情抽屉：桌面端原地弹出，移动端仍走详情页 -->
    <el-drawer v-model="detailVisible" direction="rtl" size="820px" :title="detailTitle" destroy-on-close>
      <OrderDetail v-if="detailId" :key="detailId" :order-id="detailId" embedded @close="detailVisible = false" />
    </el-drawer>

    <!-- 新建单 Dialog -->
    <CreateOrderDialog
      v-model="createDialogVisible"
      @success="onCreateSuccess"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import CreateOrderDialog from '@/components/CreateOrderDialog.vue'
import OrderDetail from '@/pages/OrderDetail.vue'
import * as ordersApi from '@/api/orders'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const router = useRouter()
const auth = useAuthStore()
const { isMobile } = useMobile()

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待上架', value: 'PENDING' },
  { label: '已完成', value: 'DONE' }
]

// 快捷入口：移动端用首页同款彩色宫格配色，桌面端沿用渐变 tone
const quickActions = computed(() => [
  ...(auth.can('inbound:create') ? [{ title: '新建入库单', desc: '登记到货备件与批次信息', icon: 'Plus', tone: 'tone-blue', color: '#2e6cf6', bg: '#eaf1ff', action: openCreateDialog }] : []),
  ...(auth.can('inbound:putaway') ? [{ title: '进入上架作业', desc: '按货位完成备件上架确认', icon: 'Check', tone: 'tone-green', color: '#12b76a', bg: '#e6f7ef', action: goPutaway }] : []),
  { title: '库存台账', desc: '查询当前库存与批次分布', icon: 'DataAnalysis', tone: 'tone-amber', color: '#0aa5a8', bg: '#e3f7f7', action: goInventory },
  { title: '库存流水', desc: '查看全部库存变更记录', icon: 'Document', tone: 'tone-cyan', color: '#7c5cf0', bg: '#efeaff', action: () => router.push('/inventory/logs') }
])

const filters = reactive({ status: '', keyword: '' })
const page = reactive({ page: 1, size: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const createDialogVisible = ref(false)
// 详情抽屉（桌面端）；移动端沿用详情页，避免窄屏塞两个列表
const detailVisible = ref(false)
const detailId = ref(null)
const detailTitle = computed(() => {
  const row = list.value.find(r => r.id === detailId.value)
  return row?.orderNo ? `入库单 ${row.orderNo}` : '入库单详情'
})

async function loadData() {
  loading.value = true
  try {
    const r = await ordersApi.list({
      status: filters.status || undefined,
      keyword: filters.keyword?.trim() || undefined,
      page: page.page - 1,
      size: page.size
    })
    list.value = r.content || []
    total.value = r.totalElements || 0
  } finally {
    loading.value = false
  }
}

function setStatus(value) {
  filters.status = value
  page.page = 1
  loadData()
}

function onSearch() {
  page.page = 1
  loadData()
}

function resetFilters() {
  filters.status = ''
  filters.keyword = ''
  page.page = 1
  loadData()
}

function onStatusChange() {
  page.page = 1
  loadData()
}

function openCreateDialog() {
  createDialogVisible.value = true
}

function onCreateSuccess() {
  loadData()
}

function goDetail(row) {
  if (isMobile.value) {
    router.push(`/orders/${row.id}`)
    return
  }
  detailId.value = row.id
  detailVisible.value = true
}

function goPutaway() {
  router.push('/putaway')
}

function goInventory() {
  router.push('/inventory')
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}
function formatTime(isoStr) {
  if (!isoStr) return ''
  const d = new Date(isoStr)
  if (isNaN(d.getTime())) return ''
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    ' ' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes())
  )
}

onMounted(loadData)
</script>

<style scoped lang="scss">
// ---------- 移动端（App 外壳） ----------
.m-page {
  background: transparent;
  padding: 0 0 12px;
  box-shadow: none;
  border: 0;
  border-radius: 0;
  min-height: auto;
}
.card-m {
  margin: 12px 12px 0;
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
}
// 快捷宫格（与首页服务宫格同款）
.m-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px 4px;
  padding: 16px 8px;
}
.m-svc {
  border: 0;
  background: transparent;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
}
.m-svc-icon {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  display: grid;
  place-items: center;
}
.m-svc-label {
  font-size: 12px;
  color: #475569;
}
// 筛选卡
.m-filter {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-chips {
  display: flex;
  align-items: center;
  gap: 8px;
}
.m-chip {
  border: 1px solid #e5eaf1;
  background: #f6f9ff;
  color: #64748b;
  border-radius: 15px;
  padding: 5px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all .15s;
  &.on {
    background: linear-gradient(135deg, #2e6ef2, #1e50d8);
    border-color: transparent;
    color: #fff;
    font-weight: 600;
  }
}
.m-total {
  margin-left: auto;
  font-size: 11px;
  color: #b6c0cd;
}
.m-create-btn {
  width: 100%;
  min-height: 44px;
  border: 0;
  color: #fff;
  font-size: 15px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2e6ef2, #1e50d8);
  &:active { opacity: .9; }
}
// 单据卡片列表
.m-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 12px;
  min-height: 120px;
}
.m-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
  cursor: pointer;
}
.m-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.m-no {
  font-size: 15px;
  color: #1e293b;
}
.m-tag {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  &.pending { background: #eaf1ff; color: #2e6cf6; }
  &.done { background: #e6f7ef; color: #12b76a; }
}
.m-line {
  display: flex;
  font-size: 13px;
  padding: 3px 0;
}
.m-label {
  width: 64px;
  flex-shrink: 0;
  color: #94a3b8;
}
.m-val {
  flex: 1;
  min-width: 0;
  color: #334155;
  word-break: break-all;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: center;
  padding-bottom: 4px;
}

// ---------- 桌面端（保持原样） ----------
.quick-card {
  cursor: pointer;
  transition: transform .2s, box-shadow .2s, border-color .2s;
  min-width:0;
  &:hover {
    transform: translateY(-2px);
    border-color:rgba(14,165,233,.35)!important;
    box-shadow:0 10px 24px rgba(15,35,64,.08)!important;
  }
}
.quick-section{margin-bottom:20px;padding:14px;background:linear-gradient(135deg,#f8fbff,#f7fcfd);border:1px solid #e6eef7;border-radius:12px}.quick-section-head{display:flex;align-items:center;gap:10px;margin:0 2px 12px;color:#334155;font-weight:650}.quick-section-head small{color:#94a3b8;font-weight:400}.quick-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:12px}
.quick-card-body {
  display: flex;
  align-items: center;
  gap: 14px;
}
.quick-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tone-blue{background:linear-gradient(135deg,#1677ff,#38bdf8)}.tone-green{background:linear-gradient(135deg,#16a34a,#4ade80)}.tone-amber{background:linear-gradient(135deg,#f59e0b,#fbbf24)}.tone-cyan{background:linear-gradient(135deg,#0891b2,#22d3ee)}
.quick-info {
  flex: 1;
  min-width: 0;
}
.quick-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.quick-sub {
  font-size: 12px;
  color: #909399;
}
.quick-arrow {
  color: #c0c4cc;
}
@media(min-width:768px){
  .pager{justify-content:flex-end}
}

// 双击行可打开详情，行上给手型提示
.order-table :deep(.el-table__row){cursor:pointer}
</style>
