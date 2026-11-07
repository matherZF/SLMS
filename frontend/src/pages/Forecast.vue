<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">需求预测</PageHeader>

    <el-alert type="info" :closable="false" show-icon class="page-alert">
      <template #title>
        按「设备运行强度 → 备件磨损」推算需求：日均需求 = 各在产设备的装机数 ÷ 额定承受量（件数 / 运行时长 / 任务次数，取最严的那个），
        也可以换成 ÷ 更换周期或 ÷ 寿命天数。可撑天数 = 可用库存 ÷ 日均需求。
        最迟到货日 = 预计缺货日 − {{ safetyDays }} 天安全缓冲；最晚采购日 = 最迟到货日 − 采购周期。
        没填采购周期的也照样预警，只是采购日按 {{ defaultLeadDays }} 天交期估算。
        只统计最近 {{ windowDays }} 天真有运行数据的设备——闲置设备不磨损，算进去会把需求摊薄、补货补晚。
      </template>
    </el-alert>

    <div class="summary">
      <div class="stat"><b>{{ rows.length }}</b><span>在用备件</span></div>
      <div class="stat"><b class="ok">{{ readyCount }}</b><span>可预测</span></div>
      <div class="stat"><b class="warn">{{ actionCount }}</b><span>需要补货</span></div>
      <div class="stat"><b class="gap">{{ gapCount }}</b><span>待补参数</span></div>
    </div>

    <!-- 移动端卡片 -->
    <div v-if="isMobile" v-loading="loading" class="m-card-list">
      <el-empty v-if="!loading && rows.length === 0" description="暂无数据" :image-size="70" />
      <article v-for="row in rows" :key="row.productId" class="m-card">
        <div class="m-card-head">
          <span class="m-title">{{ row.sku }} {{ row.productName }}</span>
          <span class="m-tag" :class="statusClass(row)">{{ row.statusText }}</span>
        </div>
        <div class="m-line"><span class="m-label">日均需求</span><span class="m-val">{{ row.dailyDemand != null ? `${row.dailyDemand} 件/天` : '-' }}</span></div>
        <div class="m-line"><span class="m-label">可用库存</span><span class="m-val">{{ row.stockQty }} {{ row.unit || '' }}</span></div>
        <div class="m-line"><span class="m-label">可撑天数</span><span class="m-val"><b :class="{ danger: row.severity }">{{ row.coverageDays != null ? `${row.coverageDays} 天` : '-' }}</b></span></div>
        <div class="m-line"><span class="m-label">预计缺货</span><span class="m-val">{{ row.stockoutDate || '-' }}</span></div>
        <div class="m-line"><span class="m-label">最迟到货</span><span class="m-val"><b :class="{ danger: row.severity }">{{ row.receiveByDate || '-' }}</b></span></div>
        <div class="m-line"><span class="m-label">最晚采购</span><span class="m-val">{{ purchaseText(row) }}</span></div>
        <div class="m-line"><span class="m-label">最少补货</span><span class="m-val">{{ qtyText(row) }}</span></div>
        <div class="m-line" v-if="row.actionHint"><span class="m-label">建议</span><span class="m-val action-text">{{ row.actionHint }}</span></div>
        <div class="m-line" v-if="row.gaps && row.gaps.length"><span class="m-label">还缺</span><span class="m-val gap-text">{{ row.gaps.join('；') }}</span></div>
        <div class="m-line" v-if="row.devices && row.devices.length"><span class="m-label">设备</span><span class="m-val">{{ row.devices.map(d => d.equipmentCode + '×' + d.installedQty).join('、') }}</span></div>
      </article>
    </div>

    <el-table v-else v-loading="loading" :data="rows" border stripe row-key="productId">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand">
            <div v-if="row.devices && row.devices.length" class="expand-block">
              <div class="expand-title">需求来源（每台设备各自的日耗，取最大口径）</div>
              <el-table :data="row.devices" size="small" border>
                <el-table-column prop="equipmentCode" label="设备编号" width="130" />
                <el-table-column prop="equipmentName" label="设备名称" min-width="140" />
                <el-table-column prop="installedQty" label="单机数量" width="100" />
                <el-table-column label="日均运行" width="120">
                  <template #default="{ row: d }">{{ d.avgDailyHours != null ? `${d.avgDailyHours} 小时` : '未上报' }}</template>
                </el-table-column>
                <el-table-column label="日均任务" width="110">
                  <template #default="{ row: d }">{{ d.avgDailyTasks != null ? `${d.avgDailyTasks} 次` : '未上报' }}</template>
                </el-table-column>
                <el-table-column label="日均产量" width="120">
                  <template #default="{ row: d }">{{ d.avgDailyOutput != null ? `${d.avgDailyOutput} 件` : '无产量数据' }}</template>
                </el-table-column>
                <el-table-column label="日耗" width="110">
                  <template #default="{ row: d }">{{ d.dailyDemand }} 件/天</template>
                </el-table-column>
                <el-table-column prop="basis" label="依据" min-width="320" />
              </el-table>
            </div>
            <div v-if="row.gaps && row.gaps.length" class="expand-block">
              <div class="expand-title">要让它能预测，还缺这些</div>
              <ul class="gap-list"><li v-for="(g, i) in row.gaps" :key="i">{{ g }}</li></ul>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="sku" label="SKU" width="120" />
      <el-table-column prop="productName" label="备件" min-width="150" />
      <el-table-column label="日均需求" width="120">
        <template #default="{ row }">{{ row.dailyDemand != null ? `${row.dailyDemand} 件/天` : '-' }}</template>
      </el-table-column>
      <el-table-column label="可用库存" width="100"><template #default="{ row }">{{ row.stockQty }}</template></el-table-column>
      <el-table-column label="可撑天数" width="110">
        <template #default="{ row }">
          <span v-if="row.coverageDays != null" :class="{ danger: row.severity }">{{ row.coverageDays }} 天</span>
          <span v-else class="dim">-</span>
        </template>
      </el-table-column>
      <el-table-column label="预计缺货日" width="120"><template #default="{ row }">{{ row.stockoutDate || '-' }}</template></el-table-column>
      <el-table-column label="最迟到货日" width="120">
        <template #default="{ row }">
          <b v-if="row.receiveByDate" :class="{ danger: row.severity }">{{ row.receiveByDate }}</b>
          <span v-else class="dim">-</span>
        </template>
      </el-table-column>
      <el-table-column label="最晚采购日" width="140">
        <template #default="{ row }">
          <span v-if="row.orderByDate" :class="{ danger: row.severity }">{{ row.orderByDate }}</span>
          <span v-else-if="row.stockoutDate" class="dim">交期未填</span>
          <span v-else class="dim">-</span>
          <div v-if="row.leadEstimated" class="lead-hint">按 {{ defaultLeadDays }} 天估</div>
        </template>
      </el-table-column>
      <el-table-column label="最少补货量" width="120">
        <template #default="{ row }">
          <b v-if="row.suggestedQty > 0">{{ row.suggestedQty }} {{ row.unit || '' }}</b>
          <span v-else class="dim">{{ row.suggestedQty === 0 ? '暂不用补' : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="行动建议" min-width="260">
        <template #default="{ row }">
          <span v-if="row.actionHint" :class="{ 'action-text': row.severity }">{{ row.actionHint }}</span>
          <span v-else class="dim">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row)" size="small">{{ row.statusText }}</el-tag>
          <div v-if="row.leadDays" class="lead-hint">采购 {{ row.leadDays }} 天</div>
          <div v-else-if="row.status === 'READY'" class="lead-hint warn-text">未填采购周期</div>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!isMobile && !loading && rows.length === 0" description="暂无在用备件" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { sparePartForecasts } from '@/api/equipment'
import { useMobile } from '@/composables/useMobile'

const { isMobile } = useMobile()
const rows = ref([])
const loading = ref(false)
const windowDays = 7
const safetyDays = 7
const defaultLeadDays = 7

const readyCount = computed(() => rows.value.filter(r => r.status === 'READY').length)
const actionCount = computed(() => rows.value.filter(r => r.severity).length)
const gapCount = computed(() => rows.value.filter(r => r.status !== 'READY').length)

function statusClass(row) {
  if (row.severity === 'HIGH') return 't-red'
  if (row.severity === 'MEDIUM') return 't-amber'
  if (row.status !== 'READY') return 't-gray'
  return 't-green'
}
function purchaseText(row) {
  if (!row.orderByDate) return row.stockoutDate ? '交期未填' : '-'
  return row.leadEstimated ? `${row.orderByDate}（估算）` : row.orderByDate
}
function qtyText(row) {
  if (row.suggestedQty == null) return '-'
  return row.suggestedQty > 0 ? `${row.suggestedQty} ${row.unit || ''}` : '暂不用补'
}
function statusTagType(row) {
  if (row.severity === 'HIGH') return 'danger'
  if (row.severity === 'MEDIUM') return 'warning'
  if (row.status !== 'READY') return 'info'
  return 'success'
}
async function load() {
  loading.value = true
  try { rows.value = await sparePartForecasts() } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped lang="scss">
.page-alert { margin-bottom: 14px; }
.summary {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  .stat {
    flex: 1;
    background: #f8fafc;
    border-radius: 10px;
    padding: 10px 14px;
    display: flex;
    flex-direction: column;
    b { font-size: 20px; color: #1e293b; }
    b.ok { color: #15803d; }
    b.warn { color: #e34d59; }
    b.gap { color: #b45309; }
    span { font-size: 12px; color: #94a3b8; margin-top: 2px; }
  }
}
.danger { color: #e34d59; font-weight: 700; }
.dim { color: #b0b7c3; }
.lead-hint { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.warn-text { color: #b45309; }
.action-text { color: #b45309; font-size: 12px; line-height: 1.5; }
.expand { padding: 8px 16px 14px; background: #fafbfc; }
.expand-block { margin-bottom: 12px; }
.expand-title { font-size: 12px; color: #64748b; margin-bottom: 6px; }
.gap-list { margin: 0; padding-left: 18px; color: #b45309; font-size: 12px; line-height: 1.8; }
// ---------- 移动端 ----------
.m-page {
  background: transparent;
  padding: 0 0 12px;
  box-shadow: none;
  border: 0;
  border-radius: 0;
  min-height: auto;
}
.m-page .page-alert { margin: 12px 12px 0; border-radius: 10px; }
.m-page .summary { margin: 12px 12px 0; gap: 8px; .stat { padding: 8px 10px; b { font-size: 17px; } } }
.m-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 12px;
  min-height: 120px;
}
.m-card { background: #fff; border-radius: 14px; padding: 14px; box-shadow: 0 2px 10px rgba(30, 60, 120, .05); }
.m-card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; gap: 8px; }
.m-title { font-size: 14px; color: #1e293b; font-weight: 500; }
.m-tag {
  flex-shrink: 0; font-size: 11px; padding: 3px 10px; border-radius: 10px;
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-red { background: #fdeaea; color: #e34d59; }
  &.t-gray { background: #f1f5f9; color: #94a3b8; }
  &.t-green { background: #e8f5ec; color: #15803d; }
}
.m-line { display: flex; font-size: 13px; padding: 3px 0; }
.m-label { width: 76px; flex-shrink: 0; color: #94a3b8; }
.m-val { flex: 1; min-width: 0; color: #334155; word-break: break-all; b { color: #334155; } b.danger { color: #e34d59; } }
.gap-text { color: #b45309; font-size: 12px; }
</style>
