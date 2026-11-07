<template>
  <div class="page-container usage-page" :class="{ 'm-page': isMobile }">
    <PageHeader>备件使用台账</PageHeader>
    <el-alert v-if="!isMobile" type="info" :closable="false" show-icon class="notice">
      <template #default>
        <div>领用单确认出库即视为备件开始使用，系统自动建立台账并记录设备运行基准；本阶段不登记更换下来的旧备件。</div>
        <div class="notice-sub">预计更换日取两者较早：按历史消耗速度推算的<b>寿命耗尽日</b>（实际磨损），与设备关联里填的<b>更换周期</b>推算的计划更换日（保养计划）。谁先到就按谁安排。</div>
      </template>
    </el-alert>

    <!-- 移动端：筛选 + 卡片列表 -->
    <template v-if="isMobile">
      <div class="m-filter">
        <el-input v-model="filters.keyword" clearable placeholder="台账号/领用单/SKU/备件/设备" @keyup.enter="search" />
        <el-select v-model="filters.status" clearable placeholder="使用状态" @change="search">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <div class="m-btns">
          <el-button type="primary" class="m-primary-btn" @click="search"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
          <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>&nbsp;重置</el-button>
        </div>
        <el-button v-if="canOperateUsage" type="success" class="m-add" :loading="refreshingAll" @click="refreshAll"><el-icon><Refresh /></el-icon>&nbsp;刷新全部寿命数据</el-button>
      </div>

      <div class="m-count">共 {{ total }} 条台账</div>

      <div v-loading="loading" class="m-list">
        <div v-for="row in rows" :key="row.id" class="m-card">
          <div class="m-card-top">
            <div class="m-title-wrap">
              <span class="m-title">{{ row.productName }}</span>
              <span class="m-sub">{{ row.sku }}<template v-if="row.spec"> · {{ row.spec }}</template></span>
            </div>
            <span class="m-tag" :class="statusTone(row.status)">{{ statusText(row.status) }}</span>
          </div>
          <div class="m-rows">
            <div class="d-item"><span class="l">出库/在用/已退</span><span>{{ row.qty }} / {{ row.activeQty }} / {{ row.returnedQty }}</span></div>
            <div class="d-item"><span class="l">使用设备</span><span>{{ equipmentText(row) }}</span></div>
            <div class="d-item"><span class="l">领用单号</span><span>{{ row.outboundOrderNo || '-' }}</span></div>
            <div class="d-item"><span class="l">开始使用</span><span>{{ formatTime(row.startedAt) }}</span></div>
            <div class="d-item">
              <span class="l">预计更换</span>
              <span v-if="row.expectedReplaceAt">{{ formatDate(row.expectedReplaceAt) }} · {{ replaceHint(row) }}</span>
              <span v-else class="m-dim">{{ noForecastText(row) }}</span>
            </div>
          </div>
          <div v-if="row.lifeMetric !== 'NONE' && row.usedPercent != null" class="m-life">
            <el-progress :percentage="Math.min(row.usedPercent, 100)" :status="progressStatus(row)" :stroke-width="8" />
            <small>已用 {{ valueText(row.usedLifeValue, row.lifeMetric) }} / {{ valueText(row.ratedLifeValue, row.lifeMetric) }}</small>
            <small v-if="multiHint(row)" class="m-multi">{{ multiHint(row) }}</small>
          </div>
          <div v-else class="m-life m-dim">{{ row.lifeMetric === 'NONE' ? '未配置寿命计算' : '尚无可用基准' }}</div>
          <div class="m-actions">
            <el-button size="small" type="primary" plain @click="showDetail(row.id)">详情</el-button>
            <el-button v-if="canOperateUsage && row.lifeMetric!=='NONE'&&!isEnded(row)" size="small" type="success" plain :loading="refreshingId===row.id" @click="refreshOne(row)">刷新</el-button>
            <el-button v-if="canOperateUsage && !isEnded(row)" size="small" type="danger" plain @click="endUsage(row)">结束跟踪</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !rows.length" description="暂无使用台账" />
      </div>

      <div class="m-pager">
        <el-pagination
          v-model:current-page="page.page"
          :page-size="page.size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </template>

    <!-- 桌面端 -->
    <template v-else>
    <div class="filter-bar">
      <el-input v-model="filters.keyword" clearable placeholder="台账号/领用单/SKU/备件/设备" style="width:300px" @keyup.enter="search" />
      <el-select v-model="filters.status" clearable placeholder="使用状态" style="width:160px" @change="search">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>&nbsp;重置</el-button>
      <el-button v-if="canOperateUsage" type="success" :loading="refreshingAll" @click="refreshAll"><el-icon><Refresh /></el-icon>&nbsp;刷新全部寿命数据</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="usageNo" label="使用台账号" min-width="210" show-overflow-tooltip />
      <el-table-column label="备件" min-width="210">
        <template #default="{row}"><div>{{ row.productName }}</div><small>{{ row.sku }}<span v-if="row.spec"> · {{ row.spec }}</span></small></template>
      </el-table-column>
      <el-table-column label="出库/在用/已退" width="145" align="center"><template #default="{row}">{{ row.qty }} / {{ row.activeQty }} / {{ row.returnedQty }}</template></el-table-column>
      <el-table-column label="使用设备" min-width="170"><template #default="{row}">{{ equipmentText(row) }}</template></el-table-column>
      <el-table-column prop="outboundOrderNo" label="领用单号" min-width="150" />
      <el-table-column label="开始使用时间" width="170"><template #default="{row}">{{ formatTime(row.startedAt) }}</template></el-table-column>
      <el-table-column label="寿命进度" min-width="190">
        <template #default="{row}">
          <template v-if="row.lifeMetric !== 'NONE' && row.usedPercent != null">
            <el-progress :percentage="Math.min(row.usedPercent,100)" :status="progressStatus(row)" />
            <small>已用 {{ valueText(row.usedLifeValue, row.lifeMetric) }} / {{ valueText(row.ratedLifeValue, row.lifeMetric) }}</small>
            <small v-if="multiHint(row)" class="multi-hint">{{ multiHint(row) }}</small>
          </template>
          <span v-else>{{ row.lifeMetric === 'NONE' ? '未配置寿命计算' : '尚无可用基准' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="预计更换" width="165">
        <template #default="{row}">
          <template v-if="row.expectedReplaceAt">
            <div class="replace-date">{{ formatDate(row.expectedReplaceAt) }}</div>
            <small :class="['replace-hint', { 'is-overdue': daysTo(row.expectedReplaceAt) < 0 }]">{{ replaceHint(row) }} · {{ replaceBasis(row) }}</small>
          </template>
          <span v-else class="m-dim">{{ noForecastText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="showDetail(row.id)">详情</el-button>
          <el-button v-if="canOperateUsage && row.lifeMetric!=='NONE'&&!isEnded(row)" link type="success" :loading="refreshingId===row.id" @click="refreshOne(row)">刷新</el-button>
          <el-button v-if="canOperateUsage && !isEnded(row)" link type="danger" @click="endUsage(row)">结束跟踪</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination"><el-pagination v-model:current-page="page.page" v-model:page-size="page.size" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load" /></div>
    </template>

    <el-dialog v-model="detailVisible" title="备件使用详情" width="760px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="使用台账号" :span="2">{{ current.usageNo }}</el-descriptions-item>
        <el-descriptions-item label="备件">{{ current.sku }} / {{ current.productName }}</el-descriptions-item>
        <el-descriptions-item label="批次">{{ current.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="领用单">{{ current.outboundOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="出库/在用/已退">{{ current.qty }} / {{ current.activeQty }} / {{ current.returnedQty }} {{ current.unit || '' }}</el-descriptions-item>
        <el-descriptions-item label="使用设备" :span="2">{{ equipmentText(current) }}</el-descriptions-item>
        <el-descriptions-item label="开始使用">{{ formatTime(current.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="最近同步">{{ formatTime(current.lastSyncedAt) }}</el-descriptions-item>
        <el-descriptions-item label="寿命计量">{{ metricText(current.lifeMetric) }}</el-descriptions-item>
        <el-descriptions-item label="额定寿命">{{ valueText(current.ratedLifeValue,current.lifeMetric) }}</el-descriptions-item>
        <el-descriptions-item label="已使用">{{ valueText(current.usedLifeValue,current.lifeMetric) }}</el-descriptions-item>
        <el-descriptions-item label="剩余寿命">{{ valueText(current.remainingLifeValue,current.lifeMetric) }}</el-descriptions-item>
        <el-descriptions-item label="日均消耗">{{ rateText(current) }}</el-descriptions-item>
        <el-descriptions-item label="预计寿命耗尽">
          {{ formatDate(current.expectedEndAt) }}
          <span v-if="forecastMetric(current)" class="forecast-note">（{{ metricText(forecastMetric(current)) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="设备更换周期">{{ current.plannedCycleDays ? current.plannedCycleDays + ' 天' : '未填' }}</el-descriptions-item>
        <el-descriptions-item label="计划更换日">{{ formatDate(current.plannedEndAt) }}</el-descriptions-item>
        <el-descriptions-item label="建议更换日" :span="2">
          <b>{{ formatDate(current.expectedReplaceAt) }}</b>
          <span v-if="current.expectedReplaceAt" class="forecast-note">{{ replaceHint(current) }} · {{ replaceBasis(current) }}先到</span>
        </el-descriptions-item>
        <el-descriptions-item label="运行小时基准/当前">{{ pair(current.baselineRunningHours,current.currentRunningHours) }}</el-descriptions-item>
        <el-descriptions-item label="任务数基准/当前">{{ pair(current.baselineTaskCount,current.currentTaskCount) }}</el-descriptions-item>
        <el-descriptions-item label="件数基准/当前">{{ pair(current.baselinePieceQty,current.currentPieceQty) }}</el-descriptions-item>
        <el-descriptions-item label="台账状态"><el-tag :type="statusType(current.status)">{{ statusText(current.status) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="基准状态">{{ current.baselineStatus === 'READY' ? '已建立' : current.baselineStatus === 'PENDING' ? '待补充' : '无需建立' }}</el-descriptions-item>
        <el-descriptions-item v-if="current.dataError" label="数据异常" :span="2"><span class="error-text">{{ current.dataError }}</span></el-descriptions-item>
        <el-descriptions-item v-if="current.endedAt" label="结束时间">{{ formatTime(current.endedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="current.endReason" label="结束原因">{{ current.endReason }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="(current?.lifeMetrics||[]).length">
        <el-divider content-position="left">计量口径明细（多口径按最严判定）</el-divider>
        <el-table :data="current.lifeMetrics" size="small" border>
          <el-table-column label="计量口径" width="180">
            <template #default="{row}">
              {{ metricText(row.metric) }}
              <el-tag v-if="row.primary" size="small" type="primary" effect="plain" style="margin-left:6px">取严</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="已用" width="140"><template #default="{row}">{{ valueText(row.usedLifeValue,row.metric) }}</template></el-table-column>
          <el-table-column label="额定" width="140"><template #default="{row}">{{ valueText(row.ratedLifeValue,row.metric) }}</template></el-table-column>
          <el-table-column label="已用比例" min-width="180">
            <template #default="{row}">
              <el-progress v-if="row.usedPercent!=null" :percentage="Math.min(row.usedPercent,100)"
                           :status="progressOf(row.usedPercent,current.status)" :stroke-width="8" />
              <span v-else class="m-dim">尚无基准</span>
            </template>
          </el-table-column>
          <el-table-column label="预计耗尽" width="115">
            <template #default="{row}">
              <span v-if="row.expectedEndAt">{{ formatDate(row.expectedEndAt) }}</span>
              <span v-else class="m-dim">样本不足</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-divider content-position="left">同步历史（最近100条）</el-divider>
      <el-table :data="history" size="small" border max-height="280">
        <el-table-column label="采集时间" width="170"><template #default="{row}">{{ formatTime(row.collectedAt) }}</template></el-table-column>
        <el-table-column label="来源" width="100"><template #default="{row}">{{ sourceText(row.source) }}</template></el-table-column>
        <el-table-column label="主导口径" width="110"><template #default="{row}">{{ metricText(row.lifeMetric) }}</template></el-table-column>
        <el-table-column prop="runningHours" label="累计小时" width="100"><template #default="{row}">{{ row.runningHours ?? '-' }}</template></el-table-column>
        <el-table-column prop="taskCount" label="累计任务" width="100"><template #default="{row}">{{ row.taskCount ?? '-' }}</template></el-table-column>
        <el-table-column prop="pieceQty" label="累计件数" width="100"><template #default="{row}">{{ row.pieceQty ?? '-' }}</template></el-table-column>
        <el-table-column label="已用寿命" min-width="100"><template #default="{row}">{{ valueText(row.usedLifeValue,row.lifeMetric) }}</template></el-table-column>
        <el-table-column label="比例" width="80"><template #default="{row}">{{ row.usedPercent == null ? '-' : row.usedPercent+'%' }}</template></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as usageApi from '@/api/sparePartUsages'
import { useMobile } from '@/composables/useMobile'
import { useAuthStore } from '@/stores/auth'

const { isMobile } = useMobile()
const auth = useAuthStore()
// 与后端 SparePartUsageController 的 hasAnyAuthority('outbound:manage','outbound:picking') 对齐，
// 避免出现"按钮可见但点击必 403"（旧版本只授了 outbound:manage，内置角色无一持有）
const canOperateUsage = computed(() => auth.can('outbound:manage') || auth.can('outbound:picking'))

const filters=reactive({keyword:'',status:''}),page=reactive({page:1,size:20})
const rows=ref([]),total=ref(0),loading=ref(false),refreshingId=ref(null),refreshingAll=ref(false),detailVisible=ref(false),current=ref(null),history=ref([])
const statusOptions=[{value:'IN_USE',label:'使用中'},{value:'NEAR_DUE',label:'寿命临近'},{value:'DUE',label:'已到寿命'},{value:'BASELINE_PENDING',label:'待补基准'},{value:'NOT_TRACKED',label:'不计算'},{value:'RETURNED',label:'已全部退回'},{value:'CLOSED',label:'已结束'}]
async function load(){loading.value=true;try{const r=await usageApi.list({keyword:filters.keyword.trim()||undefined,status:filters.status||undefined,page:page.page-1,size:page.size});rows.value=r.content||[];total.value=r.totalElements||0}finally{loading.value=false}}
function search(){page.page=1;load()} function reset(){filters.keyword='';filters.status='';search()}
async function showDetail(id){[current.value,history.value]=await Promise.all([usageApi.detail(id),usageApi.snapshots(id)]);detailVisible.value=true}
async function refreshOne(row){refreshingId.value=row.id;try{await usageApi.refresh(row.id);ElMessage.success('寿命数据已刷新并保存快照');await load();if(current.value?.id===row.id)await showDetail(row.id)}finally{refreshingId.value=null}}
async function refreshAll(){refreshingAll.value=true;try{const r=await usageApi.refreshAll();ElMessage.success(`刷新完成：成功 ${r.success} 条，失败 ${r.failed} 条`);await load()}finally{refreshingAll.value=false}}
async function endUsage(row){try{const {value}=await ElMessageBox.prompt('只结束本条备件使用跟踪，不登记更换下来的旧备件信息。请输入结束原因：','结束使用跟踪',{confirmButtonText:'确认结束',cancelButtonText:'取消',inputValidator:v=>!!v?.trim()||'请输入结束原因',inputType:'textarea'});await usageApi.end(row.id,value.trim());ElMessage.success('使用台账已结束');await load();if(current.value?.id===row.id)await showDetail(row.id)}catch(e){if(e!=='cancel'&&e!=='close'&&e?.action!=='cancel'&&e?.action!=='close')throw e}}
function isEnded(row){return ['RETURNED','CLOSED'].includes(row.status)}
function statusText(v){return ({IN_USE:'使用中',NEAR_DUE:'寿命临近',DUE:'已到寿命',BASELINE_PENDING:'待补基准',NOT_TRACKED:'不计算',RETURNED:'已全部退回',CLOSED:'已结束'})[v]||v}
function statusType(v){return ({IN_USE:'success',NEAR_DUE:'warning',DUE:'danger',BASELINE_PENDING:'warning',NOT_TRACKED:'info',RETURNED:'info',CLOSED:'info'})[v]||'info'}
function statusTone(v){return ({IN_USE:'t-green',NEAR_DUE:'t-amber',DUE:'t-red',BASELINE_PENDING:'t-amber',NOT_TRACKED:'t-gray',RETURNED:'t-gray',CLOSED:'t-gray'})[v]||'t-gray'}
function metricText(v){return ({NONE:'不计算',HOURS:'设备运行小时',TASKS:'设备任务次数',QTY:'设备处理件数',DAYS:'自然使用天数'})[v]||v}
function unit(v){return ({HOURS:'小时',TASKS:'次',QTY:'件',DAYS:'天'})[v]||''}
function valueText(v,m){return v==null?'-':`${v} ${unit(m)}`}
function progressOf(pct,st){return pct>=100?'exception':(st==='NEAR_DUE'||st==='DUE')?'warning':'success'}
function multiHint(row){const n=(row.lifeMetrics||[]).length;return n>1?`共 ${n} 个口径，按${metricText(row.lifeMetric)}取严`:''}
function equipmentText(row){return [row.equipmentCode,row.equipmentName].filter(Boolean).join(' / ')||'未绑定设备'}
function pair(a,b){return `${a??'-'} / ${b??'-'}`}
function progressStatus(row){return row.status==='DUE'?'exception':row.status==='NEAR_DUE'?'warning':'success'}
function sourceText(v){return ({OUTBOUND:'出库建档',MANUAL:'人工刷新',SCHEDULED:'夜间同步',RETURN:'退库联动',CLOSE:'结束跟踪'})[v]||v}
function formatTime(v){return v?new Date(v).toLocaleString('zh-CN',{hour12:false}):'-'}
function formatDate(v){return v?new Date(v).toLocaleDateString('zh-CN'):'-'}
// 按自然日算差值：今天到期返回 0，避免"今天 18:00"被算成还有 1 天
function daysTo(v){
  if(v==null)return null
  const target=new Date(v),now=new Date()
  const a=Date.UTC(target.getFullYear(),target.getMonth(),target.getDate())
  const b=Date.UTC(now.getFullYear(),now.getMonth(),now.getDate())
  return Math.round((a-b)/86400000)
}
function replaceHint(row){
  const d=daysTo(row.expectedReplaceAt)
  if(d==null)return ''
  return d<0?`已逾期 ${-d} 天`:d===0?'今天到期':`还有 ${d} 天`
}
function sameInstant(a,b){return a!=null&&b!=null&&Math.abs(new Date(a).getTime()-new Date(b).getTime())<1000}
// 建议更换日取早，这里说明是寿命先到还是计划周期先到
function replaceBasis(row){
  if(!row?.expectedReplaceAt)return ''
  if(sameInstant(row.expectedReplaceAt,row.plannedEndAt))return '计划周期'
  if(sameInstant(row.expectedReplaceAt,row.expectedEndAt))return '寿命耗尽'
  return ''
}
function noForecastText(row){
  if(row.lifeMetric==='NONE')return '不计算'
  return row.rateSampleDays?`已累积 ${row.rateSampleDays} 天`:'暂无样本'
}
// 决定更换日的是"最早耗尽的那个口径"，可能不是列表里展示的主导口径
function forecastMetric(row){
  const dated=(row?.lifeMetrics||[]).filter(m=>m.expectedEndAt)
  if(!dated.length)return null
  return dated.reduce((a,b)=>new Date(a.expectedEndAt)<=new Date(b.expectedEndAt)?a:b).metric
}
function rateText(row){
  const m=forecastMetric(row)
  if(m==='DAYS')return '天数口径，按开始使用时间推算'
  if(row.dailyRate==null)return row.rateSampleDays?`样本不足（已累积 ${row.rateSampleDays} 天）`:'样本不足'
  return `${row.dailyRate} ${unit(m)}/天（样本 ${row.rateSampleDays} 天）`
}
onMounted(load)
</script>

<style scoped>
.notice{margin-bottom:16px}.pagination{margin-top:16px;display:flex;justify-content:flex-end}small{color:#909399}.error-text{color:#f56c6c}
.notice-sub{margin-top:6px;color:#7a8699;line-height:1.65}
.replace-date{font-size:13px;color:#1f2d3d;line-height:1.4}
.replace-hint{display:block;font-size:11px;color:#909399;line-height:1.5}
.replace-hint.is-overdue{color:#f56c6c}
.forecast-note{margin-left:8px;color:#909399;font-size:12px}
.multi-hint{display:block;color:#b0b8c4;font-size:11px;line-height:1.5}
@media(max-width:767px){.filter-bar{display:grid;grid-template-columns:1fr 1fr}.filter-bar .el-input,.filter-bar .el-select{width:100%!important}.pagination{overflow-x:auto}:deep(.el-dialog){width:95%!important}}

/* ---------- 移动端 ---------- */
.m-page { background: transparent; padding: 0 0 12px; box-shadow: none; border: 0; border-radius: 0; min-height: auto; }
.m-filter {
  margin: 12px 12px 0;
  background: #fff;
  border-radius: 14px;
  padding: 12px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-btns { display: flex; gap: 10px; }
.m-btns .el-button { flex: 1; margin: 0; min-height: 40px; }
.m-primary-btn { border: 0; color: #fff; background: linear-gradient(135deg, #2e6ef2, #1e50d8); }
.m-add { width: 100%; margin: 0; min-height: 40px; }
.m-count { margin: 12px 14px 0; font-size: 12px; color: #7a8699; }
.m-list { display: flex; flex-direction: column; gap: 10px; padding: 10px 12px 0; min-height: 120px; }
.m-card { background: #fff; border-radius: 14px; padding: 12px; box-shadow: 0 2px 10px rgba(30, 60, 120, .05); }
.m-card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.m-title-wrap { min-width: 0; }
.m-title { font-size: 15px; font-weight: 600; color: #1f2d3d; }
.m-sub { display: block; margin-top: 2px; font-size: 12px; color: #7a8699; }
.m-tag { flex-shrink: 0; font-size: 11px; padding: 3px 10px; border-radius: 10px; }
.m-tag.t-blue { background: #eaf1ff; color: #2e6cf6; }
.m-tag.t-amber { background: #fbf3da; color: #d9a400; }
.m-tag.t-green { background: #e6f7ef; color: #12b76a; }
.m-tag.t-red { background: #fdeaea; color: #e34d59; }
.m-tag.t-gray { background: #f1f5f9; color: #94a3b8; }
.m-rows { display: grid; grid-template-columns: 1fr; gap: 6px; }
.d-item { display: flex; justify-content: space-between; gap: 8px; font-size: 13px; color: #1f2d3d; }
.d-item .l { color: #98a2b3; flex-shrink: 0; }
.d-item > :last-child { text-align: right; }
.m-life { margin-top: 8px; }
.m-life small { color: #98a2b3; }
.m-life .m-multi { display: block; color: #b0b8c4; font-size: 11px; }
.m-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.m-actions .el-button { margin: 0; }
.m-pager { display: flex; justify-content: center; padding: 12px 0 4px; }
.m-dim { color: #c0c4cc; font-size: 12px; }
</style>
