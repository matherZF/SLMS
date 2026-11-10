<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">库存查询</PageHeader>

    <!-- 快捷入口宫格（与入库页同款） -->
    <div v-if="isMobile" class="m-grid card-m">
      <button v-for="item in quickActions" :key="item.label" class="m-svc" @click="router.push(item.path)">
        <span class="m-svc-icon" :style="{ background: item.bg, color: item.color }">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </span>
        <span class="m-svc-label">{{ item.label }}</span>
      </button>
    </div>

    <!-- 移动端：页签胶囊 + 搜索，一张卡（与入库筛选栏同款） -->
    <div v-if="isMobile" class="m-filter card-m">
      <div class="m-chips-row">
        <button class="m-chip-btn" :class="{ on: activeTab === 'summary' }" @click="switchTab('summary')">按货品汇总</button>
        <button class="m-chip-btn" :class="{ on: activeTab === 'details' }" @click="switchTab('details')">按库位明细</button>
        <span class="m-total">共 {{ activeTab === 'summary' ? summaries.length : details.length }} 条</span>
      </div>
      <el-input
        v-if="activeTab === 'summary'"
        v-model="s.summaryKeyword"
        placeholder="搜索 SKU / 货品名称"
        clearable
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template v-else>
        <el-input v-model="s.detailSku" placeholder="货品 SKU" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="s.detailLocation" placeholder="库位编码" clearable>
          <template #prefix><el-icon><Location /></el-icon></template>
        </el-input>
      </template>
    </div>

    <!-- 移动端卡片列表：按货品汇总 -->
    <div v-if="isMobile && activeTab === 'summary'" v-loading="s.loading" class="m-card-list">
      <el-empty v-if="!s.loading && summaries.length === 0" description="暂无库存数据" :image-size="70" />
      <article v-for="row in summaries" :key="row.sku" class="m-card" @click="openSummaryDetail(row)">
        <div class="m-card-head">
          <b class="m-no">{{ row.sku }}</b>
          <span class="m-qty">实存 <b>{{ row.totalQty || 0 }}</b> {{ row.unit || '件' }}</span>
        </div>
        <div class="m-line"><span class="m-label">货品名称</span><span class="m-val">{{ row.name }}</span></div>
        <div class="m-line" v-if="row.spec"><span class="m-label">规格</span><span class="m-val">{{ row.spec }}</span></div>
      </article>
    </div>

    <!-- 移动端卡片列表：按库位明细 -->
    <div v-if="isMobile && activeTab === 'details'" v-loading="s.dLoading" class="m-card-list">
      <el-empty v-if="!s.dLoading && details.length === 0" description="暂无库位明细" :image-size="70" />
      <article v-for="row in details" :key="row.id" class="m-card" @click="openRowDetail(row)">
        <div class="m-card-head">
          <b class="m-no">{{ row.locationCode }}<small v-if="row.area" class="m-no-sub">（{{ row.area }}区）</small></b>
          <span class="m-qty">可用 <b>{{ row.availableQty || 0 }}</b></span>
        </div>
        <div class="m-line"><span class="m-label">备件</span><span class="m-val">{{ row.sku }} - {{ row.productName }}</span></div>
        <div class="m-line" v-if="row.itemKey"><span class="m-label">批次</span><span class="m-val">{{ row.itemKey }}</span></div>
        <div class="m-line" v-if="row.expiryDate"><span class="m-label">到期日</span><span class="m-val">{{ row.expiryDate }}</span></div>
        <div class="m-chips">
          <span class="m-chip">实存 <b>{{ row.qty || 0 }}</b></span>
          <span class="m-chip">占用 <b>{{ row.reservedQty || 0 }}</b></span>
          <span class="m-chip">可用 <b>{{ row.availableQty || 0 }}</b></span>
        </div>
      </article>
    </div>

    <!-- 桌面端：页签 + 表格 -->
    <el-tabs v-if="!isMobile" v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <!-- Tab1：按货品汇总 -->
      <el-tab-pane label="按货品汇总" name="summary">
        <div class="filter-bar">
          <el-input
            v-model="s.summaryKeyword"
            placeholder="SKU / 货品名称关键字"
            clearable
            style="width: 260px"
            @keyup.enter="loadSummary"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="loadSummary">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="() => { s.summaryKeyword = ''; loadSummary() }">
            重置
          </el-button>
        </div>

        <el-table
          v-loading="s.loading"
          :data="summaries"
          stripe
          border
          style="width: 100%"
          empty-text="暂无库存数据"
        >
          <el-table-column prop="sku" label="SKU编码" min-width="140" />
          <el-table-column prop="name" label="货品名称" min-width="180" />
          <el-table-column prop="spec" label="规格" min-width="120" />
          <el-table-column prop="unit" label="单位" width="90" align="center" />
          <el-table-column label="实存数量" width="120" align="center">
            <template #default="{ row }">
              <b class="qty-num">{{ row.totalQty || 0 }}</b>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab2：按库位明细 -->
      <el-tab-pane label="按库位明细" name="details">
        <div class="filter-bar">
          <el-input
            v-model="s.detailSku"
            placeholder="货品 SKU 关键字"
            clearable
            style="width: 200px"
            @keyup.enter="loadDetails"
          />
          <el-input
            v-model="s.detailLocation"
            placeholder="库位编码关键字"
            clearable
            style="width: 200px"
            @keyup.enter="loadDetails"
          />
          <el-button type="primary" @click="loadDetails">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="() => { s.detailSku = ''; s.detailLocation = ''; loadDetails() }">
            重置
          </el-button>
        </div>

        <el-table
          v-loading="s.dLoading"
          :data="details"
          stripe
          border
          style="width: 100%"
          empty-text="暂无库位明细"
        >
          <el-table-column prop="locationCode" label="库位编码" min-width="140" />
          <el-table-column prop="area" label="区域" width="100" />
          <el-table-column prop="sku" label="SKU" min-width="120" />
          <el-table-column prop="productName" label="货品名称" min-width="160" />
          <el-table-column label="实存" width="90" align="center">
            <template #default="{ row }">
              <b class="qty-num">{{ row.qty || 0 }}</b>
            </template>
          </el-table-column>
          <el-table-column prop="reservedQty" label="占用" width="80" align="center" />
          <el-table-column label="可用" width="90" align="center">
            <template #default="{ row }"><b class="qty-num">{{ row.availableQty || 0 }}</b></template>
          </el-table-column>
          <el-table-column label="批次号" min-width="130">
            <template #default="{ row }">
              <b v-if="row.itemKey">{{ row.itemKey }}</b>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="生产日期" width="110" align="center">
            <template #default="{ row }">{{ row.productionDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="到期日" width="110" align="center">
            <template #default="{ row }">
              <span :class="{ 'expire-soon': isExpiringSoon(row.expiryDate) }">
                {{ row.expiryDate || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="生产厂商" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.manufacturer || '-' }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="库存处理" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="auth.can('inventory:transfer')" link type="primary" :disabled="row.availableQty <= 0" @click="openTransfer(row)">移库</el-button>
              <el-button v-if="auth.can('inventory:scrap')" link type="danger" :disabled="row.availableQty <= 0" @click="openAdjustment(row, 'SCRAP')">报废</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="adjustment.visible"
      title="备件报废"
      :width="isMobile ? '92%' : '520px'"
      class="adjustment-dialog"
      destroy-on-close
    >
      <el-descriptions v-if="adjustment.row" :column="1" border class="stock-summary">
        <el-descriptions-item label="备件">{{ adjustment.row.sku }} - {{ adjustment.row.productName }}</el-descriptions-item>
        <el-descriptions-item label="库存批次">{{ adjustment.row.itemKey || '无批次' }}</el-descriptions-item>
        <el-descriptions-item label="库位/可用库存">{{ adjustment.row.locationCode }} / {{ adjustment.row.availableQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="adjustmentFormRef" :model="adjustment" :rules="adjustmentRules" label-width="96px">
        <el-form-item label="处理数量" prop="qty">
          <el-input-number v-model="adjustment.qty" :min="1" :max="adjustment.type === 'SCRAP' ? adjustment.row?.availableQty : 999999" />
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input v-model="adjustment.sourceRefNo" maxlength="64" placeholder="可填写原领用单号或相关单号" />
        </el-form-item>
        <el-form-item label="处理原因" prop="reason">
          <el-input v-model="adjustment.reason" type="textarea" :rows="3" maxlength="180" show-word-limit placeholder="请填写退库或报废原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustment.visible = false">取消</el-button>
        <el-button type="danger" :loading="adjustment.submitting" @click="submitAdjustment">
          确认报废
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferForm.visible" title="库存移库" :width="isMobile ? '92%' : '520px'" class="transfer-dialog" destroy-on-close>
      <el-descriptions v-if="transferForm.row" :column="1" border class="stock-summary">
        <el-descriptions-item label="备件">{{ transferForm.row.sku }} - {{ transferForm.row.productName }}</el-descriptions-item>
        <el-descriptions-item label="来源库位">{{ transferForm.row.locationCode }}</el-descriptions-item>
        <el-descriptions-item label="批次/可用库存">{{ transferForm.row.itemKey || '无批次' }} / {{ transferForm.row.availableQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="96px">
        <el-form-item label="目标库位" required>
          <el-select v-model="transferForm.targetLocationId" filterable style="width:100%" placeholder="请选择目标库位">
            <el-option v-for="loc in targetLocations" :key="loc.id" :value="loc.id" :label="`${loc.code} - ${loc.area || '未分区'}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="移库数量" required><el-input-number v-model="transferForm.qty" :min="1" :max="transferForm.row?.availableQty || 1" /></el-form-item>
        <el-form-item label="移库原因" required><el-input v-model="transferForm.reason" type="textarea" :rows="3" maxlength="180" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="transferForm.visible=false">取消</el-button><el-button type="primary" :loading="transferForm.submitting" @click="submitTransfer">确认移库</el-button></template>
    </el-dialog>

    <!-- 库存详情弹框（移动端点卡片触发） -->
    <el-dialog
      v-model="detailDialog.visible"
      :title="detailDialog.mode === 'summary' ? '备件库存详情' : '库位库存详情'"
      :width="isMobile ? '92%' : '520px'"
      destroy-on-close
    >
      <div v-loading="detailDialog.loading">
        <el-descriptions v-if="detailDialog.row" :column="1" border class="stock-summary">
          <template v-if="detailDialog.mode === 'summary'">
            <el-descriptions-item label="SKU">{{ detailDialog.row.sku }}</el-descriptions-item>
            <el-descriptions-item label="货品名称">{{ detailDialog.row.name }}</el-descriptions-item>
            <el-descriptions-item label="规格/单位">{{ detailDialog.row.spec || '-' }} / {{ detailDialog.row.unit || '件' }}</el-descriptions-item>
            <el-descriptions-item label="实存总量">{{ detailDialog.row.totalQty || 0 }} {{ detailDialog.row.unit || '件' }}</el-descriptions-item>
          </template>
          <template v-else>
            <el-descriptions-item label="库位">{{ detailDialog.row.locationCode }}<template v-if="detailDialog.row.area">（{{ detailDialog.row.area }}区）</template></el-descriptions-item>
            <el-descriptions-item label="备件">{{ detailDialog.row.sku }} - {{ detailDialog.row.productName }}</el-descriptions-item>
            <el-descriptions-item label="批次">{{ detailDialog.row.itemKey || '无批次' }}</el-descriptions-item>
            <el-descriptions-item label="实存/占用/可用">{{ detailDialog.row.qty || 0 }} / {{ detailDialog.row.reservedQty || 0 }} / {{ detailDialog.row.availableQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="生产日期/到期日">{{ detailDialog.row.productionDate || '-' }} / {{ detailDialog.row.expiryDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生产厂商">{{ detailDialog.row.manufacturer || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatTime(detailDialog.row.updatedAt) }}</el-descriptions-item>
          </template>
        </el-descriptions>

        <!-- 汇总模式：展示该备件的库位分布 -->
        <template v-if="detailDialog.mode === 'summary'">
          <div class="dd-sub">库位分布（{{ detailDialog.skuLocations.length }}）</div>
          <div v-for="loc in detailDialog.skuLocations" :key="loc.id" class="dd-loc">
            <div class="dd-loc-main">
              <div class="dd-loc-code"><b>{{ loc.locationCode }}</b><span v-if="loc.area" class="dd-sub2">（{{ loc.area }}区）</span></div>
              <div class="dd-sub2">{{ loc.itemKey || '无批次' }}<template v-if="loc.expiryDate"> · 到期 {{ loc.expiryDate }}</template></div>
            </div>
            <div class="dd-loc-qty">可用 <b>{{ loc.availableQty || 0 }}</b> / 实存 {{ loc.qty || 0 }}</div>
          </div>
          <el-empty v-if="!detailDialog.loading && !detailDialog.skuLocations.length" description="该备件暂无库位库存" :image-size="60" />
        </template>

        <!-- 明细模式：库存操作 -->
        <template v-else>
          <div v-if="auth.can('inventory:transfer') || auth.can('inventory:scrap')" class="dd-actions">
            <el-button v-if="auth.can('inventory:transfer')" type="primary" plain :disabled="detailDialog.row?.availableQty <= 0" @click="transferFromDetail">移库</el-button>
            <el-button v-if="auth.can('inventory:scrap')" type="danger" plain :disabled="detailDialog.row?.availableQty <= 0" @click="adjustFromDetail">报废</el-button>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import * as inventoryApi from '@/api/inventory'
import * as locationApi from '@/api/locations'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const activeTab = ref('summary')
const router = useRouter()
const auth = useAuthStore()
const { isMobile } = useMobile()

// 快捷入口宫格（与入库页同款配色，按权限过滤）
const quickActions = computed(() => [
  ...(auth.can('stocktake:create') ? [{ label: '库存盘点', icon: 'Finished', color: '#7c5cf0', bg: '#efeaff', path: '/stocktakes' }] : []),
  { label: '库存流水', icon: 'List', color: '#0aa5a8', bg: '#e3f7f7', path: '/inventory/logs' },
  { label: '使用台账', icon: 'TrendCharts', color: '#12b76a', bg: '#e6f7ef', path: '/spare-part-usages' },
  ...(auth.can('page:alerts') ? [{ label: '预警中心', icon: 'Bell', color: '#e34d59', bg: '#fdeaea', path: '/alerts' }] : [])
])

const s = reactive({
  // summary
  summaryKeyword: '',
  loading: false,
  // details
  detailSku: '',
  detailLocation: '',
  dLoading: false
})

const summaries = ref([])
const details = ref([])
const adjustmentFormRef = ref(null)
const adjustment = reactive({
  visible: false,
  submitting: false,
  type: 'RETURN',
  row: null,
  qty: 1,
  reason: '',
  sourceRefNo: ''
})
const adjustmentRules = {
  qty: [{ required: true, message: '请输入处理数量', trigger: 'change' }],
  reason: [{ required: true, message: '请填写处理原因', trigger: 'blur' }]
}
const locations = ref([])
const transferForm = reactive({ visible: false, submitting: false, row: null, targetLocationId: null, qty: 1, reason: '' })
const targetLocations = computed(() => locations.value.filter(loc => loc.id !== transferForm.row?.locationId))

// 卡片点击详情弹框
const detailDialog = reactive({ visible: false, loading: false, mode: 'summary', row: null, skuLocations: [] })

async function openSummaryDetail(row) {
  Object.assign(detailDialog, { visible: true, loading: true, mode: 'summary', row, skuLocations: [] })
  try {
    detailDialog.skuLocations = await inventoryApi.details({ sku: row.sku }) || []
  } finally {
    detailDialog.loading = false
  }
}

function openRowDetail(row) {
  Object.assign(detailDialog, { visible: true, loading: false, mode: 'detail', row, skuLocations: [] })
}

function transferFromDetail() {
  const row = detailDialog.row
  detailDialog.visible = false
  openTransfer(row)
}

function adjustFromDetail() {
  const row = detailDialog.row
  detailDialog.visible = false
  openAdjustment(row, 'SCRAP')
}

// 加载汇总
async function loadSummary() {
  s.loading = true
  try {
    // 后端接口实际参数名是 sku
    const r = await inventoryApi.summary({
      sku: s.summaryKeyword?.trim() || undefined
    })
    summaries.value = r || []
  } finally {
    s.loading = false
  }
}

// 加载明细
async function loadDetails() {
  s.dLoading = true
  try {
    const r = await inventoryApi.details({
      sku: s.detailSku?.trim() || undefined,
      locationCode: s.detailLocation?.trim() || undefined
    })
    details.value = r || []
  } finally {
    s.dLoading = false
  }
}

function onTabChange(name) {
  if (name === 'summary') {
    loadSummary()
  } else if (name === 'details') {
    loadDetails()
  }
}

// 移动端页签切换（胶囊）
function switchTab(name) {
  if (activeTab.value === name) return
  activeTab.value = name
  if (name === 'summary') loadSummary()
  else loadDetails()
}

// 移动端输入即搜（400ms 防抖）
let searchTimer = null
function debouncedSearch(fn) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(fn, 400)
}
watch(() => s.summaryKeyword, () => debouncedSearch(loadSummary))
watch(() => [s.detailSku, s.detailLocation], () => debouncedSearch(loadDetails))

function openAdjustment(row, type) {
  Object.assign(adjustment, {
    visible: true,
    submitting: false,
    type,
    row,
    qty: 1,
    reason: '',
    sourceRefNo: ''
  })
  nextTick(() => adjustmentFormRef.value?.clearValidate())
}

async function submitAdjustment() {
  if (!adjustmentFormRef.value) return
  const valid = await adjustmentFormRef.value.validate().catch(() => false)
  if (!valid) return
  const action = '报废'
  try {
    await ElMessageBox.confirm(`确认将 ${adjustment.qty} 件备件执行${action}处理吗？`, `${action}确认`, {
      type: 'error'
    })
  } catch {
    // 用户点了取消/关闭：确认框的 reject 不是错误，直接退出即可
    return
  }
  adjustment.submitting = true
  try {
    const result = await inventoryApi.adjust({
      inventoryId: adjustment.row.id,
      type: adjustment.type,
      qty: adjustment.qty,
      reason: adjustment.reason.trim(),
      sourceRefNo: adjustment.sourceRefNo.trim() || null
    })
    ElMessage.success(`${action}申请已提交审批，申请单号：${result.refNo}`)
    adjustment.visible = false
    await Promise.all([loadSummary(), loadDetails()])
  } finally {
    adjustment.submitting = false
  }
}

async function openTransfer(row) {
  Object.assign(transferForm, { visible: true, submitting: false, row, targetLocationId: null, qty: 1, reason: '' })
  if (!locations.value.length) {
    const result = await locationApi.list({ page: 0, size: 500 })
    locations.value = result.content || result || []
  }
}

async function submitTransfer() {
  if (!transferForm.targetLocationId) return ElMessage.warning('请选择目标库位')
  if (!transferForm.reason.trim()) return ElMessage.warning('请填写移库原因')
  if (transferForm.qty < 1 || transferForm.qty > transferForm.row.availableQty) return ElMessage.warning('移库数量超出来源可用库存')
  transferForm.submitting = true
  try {
    const result = await inventoryApi.transfer({ sourceInventoryId: transferForm.row.id, targetLocationId: transferForm.targetLocationId, qty: transferForm.qty, reason: transferForm.reason.trim() })
    ElMessage.success(`移库成功：${result.transferNo}`)
    transferForm.visible = false
    await Promise.all([loadSummary(), loadDetails()])
  } finally { transferForm.submitting = false }
}

function isExpiringSoon(expiryDate) {
  if (!expiryDate) return false
  const days = Math.ceil((new Date(expiryDate) - new Date()) / 86400000)
  return days <= 30
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

onMounted(() => {
  loadSummary()
  // 预加载明细，tab 切换时不白屏
  loadDetails()
})
</script>

<style scoped lang="scss">
// ---------- 移动端（App 外壳，与入库/出库同款套路） ----------
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
// 快捷入口宫格（与入库页同款）
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
// 页签胶囊 + 搜索（与入库筛选栏同款）
.m-filter {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-chips-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.m-chip-btn {
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
// 库存卡片
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
// 详情弹框内：库位分布
.dd-sub {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin: 4px 0 8px;
}
.dd-loc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child { border-bottom: 0; }
}
.dd-loc-main { min-width: 0; }
.dd-loc-code { font-size: 14px; color: #1e293b; }
.dd-sub2 { font-size: 12px; color: #94a3b8; margin-left: 4px; }
.dd-loc-qty { font-size: 12px; color: #64748b; flex-shrink: 0; b { color: #1e54d8; font-size: 14px; } }
.dd-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  .el-button { flex: 1; margin: 0; min-height: 40px; }
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
.m-no-sub {
  font-size: 12px;
  color: #8a94a6;
  font-weight: 400;
}
.m-qty {
  font-size: 12px;
  color: #94a3b8;
  b { color: #1e54d8; font-size: 15px; margin: 0 2px; }
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
.m-chips {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.m-chip {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  background: #f1f5f9;
  color: #64748b;
  b { color: #1e54d8; }
}
.m-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  .el-button {
    flex: 1;
    margin: 0;
    min-height: 34px;
  }
}

// ---------- 桌面端 ----------
.qty-num {
  color: $primary;
  font-size: 15px;
}
.inventory-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.expire-soon {
  color: #f5722c;
  font-weight: 600;
}
:deep(.stock-summary) {
  margin-bottom: 18px;
}
@media (max-width: 767px) {
  :deep(.adjustment-dialog) {
    width: 92% !important;
  }
  :deep(.transfer-dialog) {
    width: 92% !important;
  }
}
</style>
