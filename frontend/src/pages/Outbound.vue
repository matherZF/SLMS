<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">出库管理</PageHeader>

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

    <!-- 过滤栏：移动端胶囊横滑 + 全宽搜索 / 桌面端原版 -->
    <div v-if="isMobile" class="m-filter card-m">
      <div class="m-chips">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          class="m-chip"
          :class="{ on: filters.status === opt.value }"
          @click="setStatus(opt.value)"
        >{{ opt.label }}</button>
      </div>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索单号 / 申请人 / 工单号"
        clearable
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button v-if="auth.can('outbound:create')" class="m-create-btn" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>&nbsp;新建领用单
      </el-button>
    </div>
    <div v-else class="filter-bar">
      <el-select v-model="filters.status" placeholder="状态筛选" style="width: 160px" clearable @change="onStatusChange">
        <el-option label="全部" value="" />
        <el-option label="待审批" value="PENDING_APPROVAL" />
        <el-option label="待拣货" value="APPROVED" />
        <el-option label="拣货中" value="PICKING" />
        <el-option label="已完成" value="DONE" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="单号/申请人/部门/领用人/工单号"
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
      <el-button v-if="auth.can('outbound:create')" type="success" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>&nbsp;新建领用单
      </el-button>
    </div>

    <!-- 数据表格：桌面表格 / App 壳卡片列表 -->
    <div v-if="isMobile" v-loading="loading" class="m-card-list">
      <el-empty v-if="!loading && list.length === 0" description="暂无领用单" :image-size="70" />
      <article v-for="row in list" :key="row.id" class="m-card" @click="viewDetail(row)">
        <div class="m-card-head">
          <b class="m-no">{{ row.orderNo }}</b>
          <span class="m-tag" :class="statusTone(row.status)">{{ statusText(row.status) }}</span>
        </div>
        <div class="m-line"><span class="m-label">部门/领用人</span><span class="m-val">{{ row.department || '-' }} / {{ row.recipient || '-' }}</span></div>
        <div class="m-line"><span class="m-label">用途</span><span class="m-val">{{ row.purpose || '-' }}</span></div>
        <div class="m-line"><span class="m-label">货品/时间</span><span class="m-val">{{ row.itemCount || 0 }} 项 · {{ formatTime(row.createdAt) }}</span></div>
      </article>
    </div>

    <el-table
      v-else
      v-loading="loading"
      :data="list"
      stripe
      border
      style="width: 100%"
    >
      <el-table-column prop="orderNo" label="领用单号" min-width="170" />
      <el-table-column prop="department" label="领用部门" min-width="120" />
      <el-table-column prop="recipient" label="领用人" min-width="100" />
      <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
      <el-table-column prop="itemCount" label="货品项数" width="100" align="center" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" effect="light">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'APPROVED' || row.status === 'PICKING'"
            type="primary"
            size="small"
            link
            @click="goPicking(row)"
          >
            拣货
          </el-button>
          <el-button size="small" link @click="viewDetail(row)">
            详情
          </el-button>
          <el-button v-if="row.status === 'REJECTED' && auth.can('outbound:create')" size="small" link type="warning" @click="resubmitOrder(row)">重新提交</el-button>
          <el-button v-if="row.status === 'PENDING_APPROVAL' || row.status === 'APPROVED'" size="small" link type="danger" @click="cancelOrder(row)">取消</el-button>
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

    <!-- 新建领用单 Dialog -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建领用单"
      :width="dialogWidth"
      :top="isMobile ? '5vh' : '15vh'"
      @closed="onCreateClosed"
      destroy-on-close
    >
      <el-form :model="createForm" label-width="0" @submit.prevent>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12"><el-form-item label="领用部门" label-width="88px"><el-select v-model="createForm.department" filterable clearable style="width:100%"><el-option v-for="x in departmentOptions" :key="x.id" :label="x.itemLabel" :value="x.itemLabel"/></el-select></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="实际领用人" label-width="96px" required><el-select v-model="createForm.recipient" filterable allow-create default-first-option placeholder="选择或输入实际领用人" style="width:100%"><el-option v-for="u in userOptions" :key="u.username" :label="u.displayName || u.username" :value="u.displayName || u.username"/></el-select></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="联系电话" label-width="88px"><el-input v-model="createForm.contactPhone" maxlength="32" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="工单号" label-width="88px"><el-input v-model="createForm.workOrderNo" maxlength="64" placeholder="维修单/工作票编号" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="期望日期" label-width="88px"><el-date-picker v-model="createForm.requiredDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :xs="24" :sm="12"><el-form-item label="交付地点" label-width="88px"><el-select v-model="createForm.deliveryLocation" filterable clearable style="width:100%"><el-option v-for="x in deliveryOptions" :key="x.id" :label="x.itemLabel" :value="x.itemLabel"/></el-select></el-form-item></el-col>
        </el-row>
        <el-form-item label="领用用途" label-width="88px" required>
          <el-input
            v-model="createForm.purpose"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="请填写设备维护、故障处理等具体领用用途"
          />
        </el-form-item>
        <div class="items-header">
          <span style="font-weight: 600">货品明细</span>
          <el-button type="primary" size="small" @click="addItem">
            <el-icon><Plus /></el-icon>&nbsp;添加货品
          </el-button>
        </div>

        <div
          v-for="(item, idx) in createForm.items"
          :key="idx"
          class="item-row"
          :class="{ 'item-row-mobile': isMobile }"
        >
          <el-select
            v-model="item.productId"
            filterable
            placeholder="搜索或选择货品"
            class="item-select"
            :loading="productsLoading"
          >
            <el-option
              v-for="p in productList"
              :key="p.id"
              :label="`${p.sku} - ${p.name}`"
              :value="p.id"
            >
              <span>{{ p.sku }} - {{ p.name }}</span>
              <span class="stock-tag" :class="{ 'stock-zero': getStock(p.id) <= 0 }">
                库存 {{ getStock(p.id) }}
              </span>
            </el-option>
          </el-select>
          <el-select
            v-model="item.equipmentId"
            clearable filterable
            class="item-equipment"
            :disabled="equipmentOptionsOf(item).length === 0"
            :placeholder="equipmentPlaceholderOf(item)"
          >
            <el-option v-for="e in equipmentOptionsOf(item)" :key="e.id" :value="e.id" :label="`${e.code} - ${e.name}`" />
          </el-select>
          <div class="item-qty-wrap">
            <el-input-number
              v-model="item.requestedQty"
              :min="1"
              :max="Math.max(1, getStock(item.productId))"
              controls-position="right"
              class="item-qty"
            />
            <el-button
              type="danger"
              link
              class="item-del"
              :disabled="createForm.items.length <= 1"
              @click="removeItem(idx)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="stock-hint" v-if="createForm.items.some(i => i.productId)">
          <el-icon><InfoFilled /></el-icon>
          <span>数量不能超过当前可用库存</span>
        </div>
        <div class="stock-hint" v-if="createForm.items.some(i => i.productId)">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ equipmentHint }}</span>
        </div>

        <el-empty
          v-if="createForm.items.length === 0"
          description="请添加货品"
          :image-size="60"
        />
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">
          提交审批
        </el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as outboundApi from '@/api/outbound'
import * as productsApi from '@/api/products'
import * as inventoryApi from '@/api/inventory'
import * as equipmentApi from '@/api/equipment'
import { useMobile } from '@/composables/useMobile'
import { useAuthStore } from '@/stores/auth'
import * as dictApi from '@/api/dictionaries'
import usersApi from '@/api/users'

const router = useRouter()
const auth = useAuthStore()

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待审批', value: 'PENDING_APPROVAL' },
  { label: '待拣货', value: 'APPROVED' },
  { label: '拣货中', value: 'PICKING' },
  { label: '已完成', value: 'DONE' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已取消', value: 'CANCELLED' }
]

// 快捷入口：移动端用首页同款彩色宫格配色，桌面端沿用渐变 tone
const quickActions = computed(() => [
  ...(auth.can('outbound:create') ? [{ title: '新建领用单', desc: '填写用途和备件需求，提交审批', icon: 'Plus', tone: 'tone-blue', color: '#2e6cf6', bg: '#eaf1ff', action: openCreateDialog }] : []),
  ...(auth.can('outbound:picking') ? [{ title: '进入拣货作业', desc: '按库位指引完成核验与出库', icon: 'Box', tone: 'tone-green', color: '#12b76a', bg: '#e6f7ef', action: () => goPicking() }] : []),
  ...(auth.can('page:approval') ? [{ title: '审批中心', desc: '处理各类待办审批', icon: 'Stamp', tone: 'tone-violet', color: '#7c5cf0', bg: '#efeaff', action: () => router.push('/approvals') }] : []),
  { title: '库存台账', desc: '查询当前库存与批次分布', icon: 'DataAnalysis', tone: 'tone-amber', color: '#0aa5a8', bg: '#e3f7f7', action: goInventory },
  { title: '使用台账', desc: '查看使用时间与寿命进度', icon: 'TrendCharts', tone: 'tone-cyan', color: '#d9a400', bg: '#fbf3da', action: () => router.push('/spare-part-usages') }
])
const { isMobile } = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '92%' : '640px'))

const filters = reactive({ status: '', keyword: '' })
const page = reactive({ page: 1, size: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const createDialogVisible = ref(false)
const submitting = ref(false)
const productsLoading = ref(false)
const productList = ref([])
const stockMap = ref({}) // productId -> totalQty
const departmentOptions = ref([]), deliveryOptions = ref([])
const userOptions = ref([]) // 实际领用人下拉：启用用户的显示名，默认当前登录用户
const equipmentPool = ref([]) // 全部设备，用于把候选 id 还原成编码+名称
const productEquipmentMap = ref({}) // productId -> [equipmentId]

const emptyCreateForm = () => ({ purpose: '', department: '', recipient: auth.displayName || '', contactPhone: '', workOrderNo: '', requiredDate: '', deliveryLocation: '', items: [{ productId: null, requestedQty: 1, equipmentId: null }] })
const createForm = reactive(emptyCreateForm())


async function loadData() {
  loading.value = true
  try {
    const r = await outboundApi.list({
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      page: page.page - 1,
      size: page.size
    })
    list.value = r.content || []
    total.value = r.totalElements || 0
  } finally {
    loading.value = false
  }
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

function setStatus(value) {
  filters.status = value
  page.page = 1
  loadData()
}

function onSearch() {
  page.page = 1
  loadData()
}

function statusTone(s) {
  if (s === 'PENDING_APPROVAL') return 't-amber'
  if (s === 'APPROVED') return 't-blue'
  if (s === 'PICKING') return 't-orange'
  if (s === 'DONE') return 't-green'
  if (s === 'REJECTED') return 't-red'
  return 't-gray'
}

function statusText(s) {
  if (s === 'PENDING_APPROVAL') return '待审批'
  if (s === 'APPROVED') return '待拣货'
  if (s === 'PICKING') return '拣货中'
  if (s === 'DONE') return '已完成'
  if (s === 'REJECTED') return '已驳回'
  if (s === 'CANCELLED') return '已取消'
  return s
}

function statusTagType(s) {
  if (s === 'PENDING_APPROVAL') return 'warning'
  if (s === 'APPROVED') return 'info'
  if (s === 'PICKING') return 'warning'
  if (s === 'DONE') return 'success'
  return ''
}

/**
 * 实际领用人下拉数据：只拉一次，失败不影响填单（输入框仍可手输）
 * 后端 /api/user-options 对任意登录用户开放，不需要 system:user 权限。
 */
async function loadUserOptions() {
  if (userOptions.value.length) return
  try {
    const list = await usersApi.options()
    userOptions.value = Array.isArray(list) ? list : []
  } catch {
    userOptions.value = []
  }
}

async function openCreateDialog() {
  Object.assign(createForm, emptyCreateForm())
  if (!departmentOptions.value.length) [departmentOptions.value, deliveryOptions.value] = await Promise.all([dictApi.options('DEPARTMENT'), dictApi.options('DELIVERY_LOCATION')])
  createDialogVisible.value = true
  loadProducts()
  loadEquipments()
  loadUserOptions()
}

function onCreateClosed() {
  Object.assign(createForm, emptyCreateForm())
}

async function loadProducts() {
  if (productList.value.length === 0) {
    productsLoading.value = true
    try {
      const r = await productsApi.list({ page: 0, size: 500 })
      productList.value = r.content || []
    } finally {
      productsLoading.value = false
    }
  }
  // 每次打开都刷新库存汇总
  try {
    const stocks = await inventoryApi.summary()
    const map = {}
    for (const s of stocks || []) {
      map[s.productId] = s.totalQty || 0
    }
    stockMap.value = map
  } catch {
    stockMap.value = {}
  }
}

function getStock(productId) {
  if (!productId) return 0
  return stockMap.value[productId] || 0
}

/* ---------- 关联设备（挂在明细上）----------
 * 备件大多是通用件，一件备件可能同时挂多台设备，系统无法判断这批具体装到哪台，
 * 所以由领用人在每行明细上指定。一张单可以有多个备件、装在不同设备上，
 * 设备只能挂在明细——挂在单头会把所有明细都记到同一台，台账寿命与更换周期全部算错。
 * 候选 = 这一行那个备件在基础数据里绑定的设备；没绑就留空（不臆测），只绑一台则自动选中减少录入。
 */
async function loadEquipments() {
  if (equipmentPool.value.length === 0) {
    try {
      const list = await equipmentApi.list({})
      equipmentPool.value = Array.isArray(list) ? list : (list.content || [])
    } catch {
      equipmentPool.value = []
    }
  }
}

const selectedProductIds = computed(() =>
  (createForm.items || []).map(i => i.productId).filter(Boolean)
)

/** 某一行明细可选的关联设备 */
function equipmentOptionsOf(item) {
  const pid = item && item.productId
  if (!pid) return []
  const byId = new Map(equipmentPool.value.map(e => [e.id, e]))
  return (productEquipmentMap.value[pid] || []).map(id => byId.get(id)).filter(Boolean)
}

function equipmentPlaceholderOf(item) {
  if (!item || !item.productId) return '先选择货品'
  if (equipmentOptionsOf(item).length === 0) return '该备件未关联设备'
  return '选择关联设备'
}

async function ensureProductEquipmentMap() {
  const missing = selectedProductIds.value.filter(id => !(id in productEquipmentMap.value))
  if (!missing.length) return
  await Promise.all(missing.map(async pid => {
    try {
      const links = await equipmentApi.devicesOfProduct(pid)
      productEquipmentMap.value = {
        ...productEquipmentMap.value,
        [pid]: (links || []).map(x => x.equipmentId ?? x.id).filter(Boolean)
      }
    } catch {
      productEquipmentMap.value = { ...productEquipmentMap.value, [pid]: [] }
    }
  }))
  syncEquipmentSelection()
}

/** 候选收敛回来时把失效的选择清掉，并给唯一候选自动选中（逐行处理） */
function syncEquipmentSelection() {
  for (const item of (createForm.items || [])) {
    const options = equipmentOptionsOf(item)
    if (!item.productId) {
      item.equipmentId = null
      continue
    }
    if (options.some(e => e.id === item.equipmentId)) continue
    item.equipmentId = options.length === 1 ? options[0].id : null
  }
}

watch(() => selectedProductIds.value.join(','), () => {
  if (!createDialogVisible.value) return
  ensureProductEquipmentMap()
})

const equipmentHint = computed(() => {
  if (!selectedProductIds.value.length) return '选定货品后，每行列出该备件在基础数据里绑定的设备'
  const rows = (createForm.items || []).filter(i => i.productId)
  const none = rows.filter(i => equipmentOptionsOf(i).length === 0)
  const multi = rows.filter(i => equipmentOptionsOf(i).length > 1 && !i.equipmentId)
  if (none.length) return '有备件在基础数据里没绑定任何设备，该行留空即可；需要按设备跟踪寿命请先去「设备档案」做关联'
  if (multi.length) return '有备件同时挂在多台设备上，请在对应行指定这批装在哪台；留空则台账记为未绑定设备'
  return '每行可指定这批备件装到哪台设备；不同明细可以选不同设备'
})

function addItem() {
  createForm.items.push({ productId: null, requestedQty: 1, equipmentId: null })
}

function removeItem(idx) {
  createForm.items.splice(idx, 1)
}

async function submitCreate() {
  // 统一用 (x || '') 兜底：el-select 的 clearable 清空后值为 undefined，
  // 直接 .trim() 会抛 TypeError 且异常发生在 try 之外，表现为"点提交没任何反应"
  // 领用部门为选填；实际领用人必填（下拉默认当前登录用户）
  const recipient = (createForm.recipient || '').trim()
  if (!recipient) {
    ElMessage.warning('请选择实际领用人')
    return
  }
  if (recipient.length > 64) {
    ElMessage.warning('实际领用人不能超过 64 个字符')
    return
  }
  if (!(createForm.purpose || '').trim()) {
    ElMessage.warning('请填写领用用途')
    return
  }
  const items = createForm.items
  if (items.length === 0) {
    ElMessage.warning('请至少添加一个货品')
    return
  }
  for (let i = 0; i < items.length; i++) {
    if (!items[i].productId) {
      ElMessage.warning(`第 ${i + 1} 行请选择货品`)
      return
    }
    if (!items[i].requestedQty || items[i].requestedQty <= 0) {
      ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`)
      return
    }
    const avail = getStock(items[i].productId)
    if (avail <= 0) {
      ElMessage.warning(`第 ${i + 1} 行货品无可用库存`)
      return
    }
    if (items[i].requestedQty > avail) {
      ElMessage.warning(`第 ${i + 1} 行数量超过可用库存（${avail}）`)
      return
    }
  }
  // 检查重复货品
  const ids = items.map((it) => it.productId)
  if (new Set(ids).size !== ids.length) {
    ElMessage.warning('存在重复货品，请合并数量')
    return
  }
  submitting.value = true
  try {
    const res = await outboundApi.create({
      purpose: (createForm.purpose || '').trim(),
      department: (createForm.department || '').trim() || null,
      recipient,
      contactPhone: (createForm.contactPhone || '').trim() || null,
      workOrderNo: (createForm.workOrderNo || '').trim() || null,
      requiredDate: createForm.requiredDate || null,
      deliveryLocation: (createForm.deliveryLocation || '').trim() || null,
      items: items.map((it) => ({
        productId: it.productId,
        requestedQty: it.requestedQty,
        equipmentId: it.equipmentId || null
      }))
    })
    ElMessage.success(`领用申请已提交审批：${res.orderNo}`)
    createDialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

function goPicking(row) {
  if (row && row.id != null) {
    router.push(`/outbound/picking/${row.id}`)
  } else {
    router.push('/outbound/picking/list')
  }
}

function goInventory() {
  router.push('/inventory')
}

function viewDetail(row) {
  router.push(`/outbound/${row.id}`)
}

async function resubmitOrder(row) {
  try {
    await ElMessageBox.confirm(`确认重新提交领用单 ${row.orderNo}？`, '重新提交')
  } catch {
    return
  }
  await outboundApi.resubmit(row.id); ElMessage.success('已重新提交审批'); loadData()
}

async function cancelOrder(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因（可选）', `取消领用单 ${row.orderNo}`, { inputType: 'textarea', confirmButtonText: '确认取消', cancelButtonText: '返回' })
    await outboundApi.cancel(row.id, value || '')
    ElMessage.success('领用单已取消')
    loadData()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') throw e
  }
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
// ---------- 移动端（App 外壳，与首页同款套路） ----------
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
.m-filter {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
  .m-chip { flex-shrink: 0; }
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
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-blue { background: #eaf1ff; color: #2e6cf6; }
  &.t-orange { background: #fef0e6; color: #f5722c; }
  &.t-green { background: #e6f7ef; color: #12b76a; }
  &.t-red { background: #fdeaea; color: #e34d59; }
  &.t-gray { background: #f1f5f9; color: #94a3b8; }
}
.m-line {
  display: flex;
  font-size: 13px;
  padding: 3px 0;
}
.m-label {
  width: 76px;
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
.tone-blue{background:linear-gradient(135deg,#1677ff,#38bdf8)}.tone-green{background:linear-gradient(135deg,#16a34a,#4ade80)}.tone-amber{background:linear-gradient(135deg,#f59e0b,#fbbf24)}.tone-cyan{background:linear-gradient(135deg,#0891b2,#22d3ee)}.tone-violet{background:linear-gradient(135deg,#7c3aed,#a78bfa)}
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
.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.equipment-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
.item-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  .item-select {
    flex: 1;
  }
  .item-equipment {
    width: 220px;
    margin-left: 12px;
  }
  .item-qty-wrap {
    display: flex;
    align-items: center;
    margin-left: 12px;
  }
  .item-qty {
    width: 160px;
  }
  .item-del {
    margin-left: 8px;
  }
}
/* 移动端：货品行垂直排列，选择器和数量各占一行 */
.item-row-mobile {
  flex-direction: column;
  align-items: stretch;
  .item-select {
    width: 100%;
    margin-bottom: 8px;
  }
  .item-equipment {
    width: 100%;
    margin-left: 0;
    margin-bottom: 8px;
  }
  .item-qty-wrap {
    margin-left: 0;
    justify-content: space-between;
    align-items: center;
  }
  .item-qty {
    flex: 1;
    width: auto;
  }
  .item-del {
    margin-left: 12px;
  }
}
.stock-tag {
  float: right;
  color: #67C23A;
  font-size: 12px;
  &.stock-zero {
    color: #F56C6C;
  }
}
.stock-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-top: -4px;
  margin-bottom: 8px;
}
/* 移动端弹窗底部按钮占满 */
@media (max-width: 768px) {
  :deep(.el-dialog__footer .el-dialog__btns) {
    display: flex;
    gap: 12px;
    .el-button {
      flex: 1;
    }
  }
}
@media(min-width:768px){
  .pager{justify-content:flex-end}
}
</style>
