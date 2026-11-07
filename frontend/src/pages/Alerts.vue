<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">预警管理</PageHeader>
    <el-alert :title="introText" type="info" :closable="false" class="page-alert" />

    <div class="toolbar">
      <el-radio-group v-model="status" size="small" @change="load">
        <el-radio-button label="OPEN">待处理</el-radio-button>
        <el-radio-button label="ACK">已处理</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="doRefresh" :loading="refreshing">立即计算</el-button>
      <el-button v-if="status==='OPEN'" size="small" @click="doReadAll">全部标已读</el-button>
      <span class="updated">最近更新：{{ updatedAt || '-' }}</span>
    </div>

    <!-- 移动端卡片列表 -->
    <div v-if="isMobile" v-loading="loading" class="m-card-list">
      <el-empty v-if="!loading && rows.length === 0" description="当前没有预警" :image-size="70" />
      <article v-for="row in rows" :key="row.id" class="m-card" :class="{ unread: !row.readFlag }" @click="doRead(row)">
        <div class="m-card-head">
          <span class="m-tag" :class="row.alertType === 'STOCK' ? 't-red' : 't-amber'">{{ typeText(row.alertType) }}</span>
          <span class="m-tag" :class="row.severity === 'HIGH' ? 't-red' : 't-gray'">{{ row.severity === 'HIGH' ? '高' : '中' }}</span>
        </div>
        <div class="m-line"><span class="m-label">备件</span><span class="m-val">{{ row.sku }} - {{ row.productName }}</span></div>
        <div class="m-line" v-if="row.equipmentCode || row.equipmentName"><span class="m-label">设备</span><span class="m-val">{{ [row.equipmentCode, row.equipmentName].filter(Boolean).join(' / ') }}</span></div>
        <div class="m-line"><span class="m-label">提醒</span><span class="m-val action-text">{{ row.message }}</span></div>
        <div class="m-line" v-if="row.alertType === 'STOCK'"><span class="m-label">库存/补货点</span><span class="m-val"><b>{{ row.stockQty }}</b> / {{ row.thresholdQty }}<small class="scope-hint">安全库存 {{ row.safetyStock }}</small></span></div>
        <div class="m-line" v-if="row.alertType === 'FORECAST'"><span class="m-label">可用/可撑</span><span class="m-val"><b>{{ row.stockQty }}</b> 可用 / 撑 {{ row.coverageDays }} 天<small class="scope-hint">{{ row.dailyDemand }} 件/天</small></span></div>
        <div class="m-line" v-if="row.orderByDate"><span class="m-label">最晚采购</span><span class="m-val" :class="{ expired: row.severity === 'HIGH' }">{{ row.orderByDate }}<small v-if="row.leadEstimated" class="scope-hint">交期按默认值估</small></span></div>
        <div class="m-line" v-if="row.receiveByDate"><span class="m-label">最迟到货</span><span class="m-val" :class="{ expired: row.severity === 'HIGH' }">{{ row.receiveByDate }}</span></div>
        <div class="m-line" v-if="row.suggestedQty"><span class="m-label">建议补货</span><span class="m-val"><b>{{ row.suggestedQty }}</b> 件</span></div>
        <div class="m-line" v-if="status==='ACK' && row.handlerName"><span class="m-label">处理人</span><span class="m-val">{{ row.handlerName }} {{ row.handledAt ? '· ' + fmt(row.handledAt) : '' }}</span></div>
        <div class="m-actions" v-if="row.status === 'OPEN'">
          <el-button size="small" type="primary" @click.stop="doAck(row)">已处理</el-button>
          <el-button size="small" @click.stop="doIgnore(row)">忽略</el-button>
        </div>
      </article>
    </div>

    <el-table v-else v-loading="loading" :data="rows" border stripe :row-class-name="rowClass">
      <el-table-column label="类型" width="110"><template #default="{row}"><el-tag :type="tagType(row)">{{ typeText(row.alertType) }}</el-tag></template></el-table-column>
      <el-table-column label="级别" width="80"><template #default="{row}"><el-tag :type="row.severity==='HIGH'?'danger':'warning'">{{ row.severity==='HIGH'?'高':'中' }}</el-tag></template></el-table-column>
      <el-table-column prop="sku" label="SKU" min-width="120" />
      <el-table-column prop="productName" label="备件" min-width="140" />
      <el-table-column label="设备" min-width="150"><template #default="{row}">{{ [row.equipmentCode,row.equipmentName].filter(Boolean).join(' / ') || '-' }}</template></el-table-column>
      <el-table-column label="提醒内容" min-width="320"><template #default="{row}"><span class="action-text">{{ row.message }}</span></template></el-table-column>
      <el-table-column label="库存/补货点" width="150">
        <template #default="{row}">
          <template v-if="row.alertType==='STOCK'">
            <div>{{ row.stockQty }} / {{ row.thresholdQty }}</div>
            <small class="scope-hint">安全库存 {{ row.safetyStock }}</small>
          </template>
          <template v-else-if="row.alertType==='FORECAST'">
            <div>{{ row.stockQty }} 可用 / 撑 {{ row.coverageDays }} 天</div>
            <small class="scope-hint">{{ row.dailyDemand }} 件/天</small>
          </template>
          <span v-else>{{ row.stockQty ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最晚采购" width="130">
        <template #default="{row}">
          <span v-if="row.orderByDate" :class="{expired:row.severity==='HIGH'}">{{ row.orderByDate }}</span>
          <span v-else-if="row.leadEstimated" class="dim">交期未填</span>
          <span v-else class="dim">-</span>
          <small v-if="row.leadEstimated && row.orderByDate" class="scope-hint">按默认值估</small>
        </template>
      </el-table-column>
      <el-table-column label="最迟到货" width="120"><template #default="{row}"><span :class="{expired:row.severity==='HIGH'}">{{ row.receiveByDate || '-' }}</span></template></el-table-column>
      <el-table-column label="建议补货" width="100"><template #default="{row}"><b v-if="row.suggestedQty">{{ row.suggestedQty }} 件</b><span v-else class="dim">-</span></template></el-table-column>
      <el-table-column v-if="status==='ACK'" label="处理人" width="160">
        <template #default="{row}">
          <span v-if="row.handlerName">{{ row.handlerName }}</span>
          <span v-else class="dim">-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="status==='ACK'" label="处理时间" width="160"><template #default="{row}">{{ fmt(row.handledAt) }}</template></el-table-column>
      <el-table-column label="更新时间" width="160"><template #default="{row}">{{ fmt(row.updatedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{row}">
          <template v-if="row.status==='OPEN'">
            <el-button link type="primary" @click="doAck(row)">已处理</el-button>
            <el-button link type="info" @click="doIgnore(row)">忽略</el-button>
          </template>
          <span v-else class="dim">已处理</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!isMobile && !loading && rows.length === 0" description="当前没有预警" />
  </div>
</template>
<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as api from '@/api/alerts'
import { useMobile } from '@/composables/useMobile'
import { dismissDeliveredAlerts, clearDeliveredAlerts } from '@/composables/useMessagePush'
const { isMobile } = useMobile()
const rows=ref([]),loading=ref(false),refreshing=ref(false),status=ref('OPEN')
const typeText=type=>({STOCK:'库存不足',EXPIRY:'批次临期',LIFECYCLE:'更换预警',FORECAST:'需求预测'}[type]||type)
// 移动端屏幕小，说明文字压成一句，别把列表挤到首屏外
const introText=computed(()=>isMobile.value
  ? '每天 09:00 自动计算。库存落到补货点就该下单，落到安全库存以下要加急。'
  : '预警每天早上 09:00 自动计算一次并落表，这里读的是计算结果。补货点 = 安全库存 + 各设备采购周期内的换件需求之和，库存落到补货点就该下单；落到安全库存以下说明已在动用缓冲，常规交期多半来不及，需要加急。换件需要的货由需求预测提前提醒，更换预警只在到期前几天提醒安排检修。已读/忽略各用户独立，已处理为全局状态。')
const tagType=row=>row.alertType==='STOCK'?'danger':row.alertType==='FORECAST'?(row.severity==='HIGH'?'danger':'warning'):'warning'
const updatedAt=computed(()=>{
  const t=rows.value.map(r=>r.updatedAt).filter(Boolean).sort().pop()
  return t ? fmt(t) : ''
})
function fmt(v){ if(!v) return '-'; const s=String(v).replace('T',' ').slice(0,16); return s }
const rowClass=({row})=>(row.readFlag?'':'unread-row')
async function load(){
  loading.value=true
  try{ rows.value=await api.inbox(status.value) } finally{ loading.value=false }
}
async function doRefresh(){
  refreshing.value=true
  try{ const n=await api.refresh(); ElMessage.success(`计算完成，新增 ${n} 条`); status.value='OPEN'; await load() }
  finally{ refreshing.value=false }
}
async function doAck(row){
  await ElMessageBox.confirm('确认处理此预警吗？处理后所有人将看到此预警为已处理状态。', '确认处理', {
    confirmButtonText: '确认处理',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await api.acknowledge(row.id); ElMessage.success('已标记为已处理'); dismissDeliveredAlerts([row]); await load()
}
async function doIgnore(row){
  await ElMessageBox.confirm('确认忽略此预警吗？忽略后仅您自己将不再看到此预警。', '确认忽略', {
    confirmButtonText: '确认忽略',
    cancelButtonText: '取消',
    type: 'info'
  })
  await api.ignore(row.id); ElMessage.success('已忽略'); dismissDeliveredAlerts([row]); await load()
}
/** 移动端点卡片即已读（点操作按钮不算，用 .stop 隔开）；本地改状态，避免整表刷新打断浏览 */
async function doRead(row){
  if (row.readFlag) return
  try { await api.markRead(row.id); row.readFlag = true; dismissDeliveredAlerts([row]) } catch { /* 读失败不影响看内容 */ }
}
async function doReadAll(){ await api.markAllRead(); ElMessage.success('已全部标为已读'); clearDeliveredAlerts(); await load() }
onMounted(load)
</script>
<style scoped lang="scss">
.page-alert { margin-bottom: 12px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.updated { margin-left: auto; color: #94a3b8; font-size: 12px; }
.expired { color: #f56c6c; font-weight: 700; }
.dim { color: #b0b7c3; }
.action-text { color: #b45309; font-size: 12px; line-height: 1.5; }
.scope-hint { margin-left: 6px; color: #909399; font-size: 11px; }
:deep(.unread-row) { background: #fff8ec; }
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
.m-page .toolbar { margin: 12px 12px 0; }
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
  &.unread { border-left: 3px solid #e6a23c; }
}
.m-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.m-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  &.t-amber { background: #fbf3da; color: #d9a400; }
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
  b { color: #e34d59; }
}
.m-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
}
</style>
