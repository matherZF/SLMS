<template>
  <div class="page-container approval-page" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">统一审批中心<template #desc>集中处理领用、退库、报废与盘点差异复核任务</template><template #actions><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button></template></PageHeader>

    <section v-if="!isMobile" class="approval-overview">
      <button v-for="item in overview" :key="item.value" class="metric-card" :class="{ active: filter === item.value }" @click="filter=item.value">
        <span class="metric-icon" :class="item.class"><el-icon><component :is="item.icon" /></el-icon></span>
        <span><strong>{{ item.count }}</strong><small>{{ item.label }}</small></span>
      </button>
    </section>

    <!-- 移动端：分类胶囊 + 计数 + 刷新（一张卡） -->
    <div v-if="isMobile" class="m-filter card-m">
      <div class="m-chips-row">
        <button
          v-for="x in filterOptions"
          :key="x.value"
          class="m-chip-btn"
          :class="{ on: filter === x.value }"
          @click="filter = x.value"
        >{{ x.label }}</button>
        <span class="m-icon-btn" @click="load"><el-icon :class="{ spinning: loading }"><Refresh /></el-icon></span>
      </div>
      <div class="m-count">共 {{ filteredRows.length }} 项需要处理</div>
    </div>

    <div v-if="!isMobile" class="task-toolbar">
      <div><b>{{ filter === 'ALL' ? '全部待办' : typeMeta(filter).label }}</b><span>共 {{ filteredRows.length }} 项需要处理</span></div>
      <el-segmented v-model="filter" :options="filterOptions" />
    </div>

    <el-table v-if="!isMobile" v-loading="loading" :data="filteredRows" stripe class="task-table">
      <el-table-column label="业务" min-width="230"><template #default="{row}"><div class="business-cell"><span class="business-mark" :class="typeMeta(row.businessType).class"><el-icon><component :is="typeMeta(row.businessType).icon" /></el-icon></span><div><b>{{typeMeta(row.businessType).label}}</b><small>{{row.businessNo}}</small></div></div></template></el-table-column>
      <el-table-column prop="applicant" label="申请人" width="120" />
      <el-table-column label="流程版本" width="100"><template #default="{row}">{{row.workflowVersion ? `V${row.workflowVersion}` : '系统判定'}}</template></el-table-column>
      <el-table-column label="当前节点" min-width="160"><template #default="{row}"><span class="node-pill"><i />{{row.nodeName || '审批'}}</span></template></el-table-column>
      <el-table-column label="等待时长" width="150"><template #default="{row}"><span :class="{overdue:row.overdue}">{{waitText(row.submittedAt)}}</span><el-tag v-if="row.overdue" size="small" type="danger" class="overdue-tag">已超时</el-tag></template></el-table-column>
      <el-table-column label="提交时间" width="180"><template #default="{row}">{{formatTime(row.submittedAt)}}</template></el-table-column>
      <el-table-column label="操作" width="180" fixed="right"><template #default="{row}"><el-button type="primary" plain size="small" @click="openReview(row,true)">通过</el-button><el-button type="danger" plain size="small" @click="openReview(row,false)">驳回</el-button></template></el-table-column>
    </el-table>

    <div v-else v-loading="loading" class="mobile-tasks">
      <article v-for="row in filteredRows" :key="`${row.businessType}-${row.businessId}`" class="mobile-task">
        <div class="mobile-head"><div class="business-cell"><span class="business-mark" :class="typeMeta(row.businessType).class"><el-icon><component :is="typeMeta(row.businessType).icon" /></el-icon></span><div><b>{{typeMeta(row.businessType).label}}</b><small>{{row.businessNo}}</small></div></div><span class="m-tag" :class="row.overdue ? 't-red' : 't-amber'">{{row.overdue?'已超时':'待处理'}}</span></div>
        <div class="mobile-info"><span>申请人 <b>{{row.applicant}}</b></span><span>流程版本 <b>{{row.workflowVersion?`V${row.workflowVersion}`:'系统判定'}}</b></span><span>当前节点 <b>{{row.nodeName||'审批'}}</b></span><span>已等待 <b>{{waitText(row.submittedAt)}}</b></span></div>
        <div class="mobile-actions"><el-button type="danger" plain @click="openReview(row,false)">驳回</el-button><el-button class="m-primary-btn" @click="openReview(row,true)">审批通过</el-button></div>
      </article>
    </div>
    <el-empty v-if="!loading&&!filteredRows.length" description="当前分类暂无待审批任务" />

    <el-dialog v-model="reviewVisible" :title="approved?'确认审批通过':'确认驳回申请'" :width="dialogWidth" destroy-on-close>
      <div v-if="selected" class="review-sheet">
        <div class="review-title"><span class="business-mark" :class="typeMeta(selected.businessType).class"><el-icon><component :is="typeMeta(selected.businessType).icon" /></el-icon></span><div><b>{{typeMeta(selected.businessType).label}}</b><small>{{selected.businessNo}}</small></div></div>

        <!-- 业务详情区 -->
        <div class="biz-detail" v-if="selected.businessSnapshot && Object.keys(selected.businessSnapshot).length">
          <!-- OUTBOUND 领用申请 -->
          <template v-if="selected.businessType === 'OUTBOUND'">
            <div class="biz-summary">
              <div v-if="selected.businessSnapshot.purpose"><span class="label">用途</span><span class="value">{{ selected.businessSnapshot.purpose }}</span></div>
              <div v-if="selected.businessSnapshot.department"><span class="label">申请部门</span><span class="value">{{ selected.businessSnapshot.department }}</span></div>
              <div v-if="selected.businessSnapshot.recipient"><span class="label">领用人</span><span class="value">{{ selected.businessSnapshot.recipient }}</span></div>
              <div v-if="selected.businessSnapshot.contactPhone"><span class="label">联系方式</span><span class="value">{{ selected.businessSnapshot.contactPhone }}</span></div>
              <div v-if="selected.businessSnapshot.workOrderNo"><span class="label">工单/项目</span><span class="value">{{ selected.businessSnapshot.workOrderNo }}</span></div>
              <div v-if="selected.businessSnapshot.requiredDate"><span class="label">需求日期</span><span class="value">{{ selected.businessSnapshot.requiredDate }}</span></div>
              <div v-if="selected.businessSnapshot.deliveryLocation"><span class="label">送达地点</span><span class="value">{{ selected.businessSnapshot.deliveryLocation }}</span></div>
              <div v-if="selected.businessSnapshot.equipment"><span class="label">关联设备</span><span class="value">{{ selected.businessSnapshot.equipment }}</span></div>
              <div class="total-row"><span class="label">领料合计</span><span class="value"><b>{{ selected.businessSnapshot.totalQty || 0 }}</b> 件 / <b>{{ (selected.businessSnapshot.items || []).length }}</b> 种</span></div>
            </div>
            <el-table :data="selected.businessSnapshot.items || []" size="small" border stripe>
              <el-table-column prop="sku" label="SKU" width="140" />
              <el-table-column prop="name" label="备件名称" min-width="160" />
              <el-table-column prop="spec" label="规格" width="140" />
              <el-table-column prop="location" label="库位" width="100" />
              <el-table-column label="数量" width="80" align="right"><template #default="{row}">{{ row.requestedQty }} {{ row.unit || '' }}</template></el-table-column>
              <el-table-column prop="equipment" label="设备" width="120" />
            </el-table>
          </template>

          <!-- RETURN 退库申请 -->
          <template v-else-if="selected.businessType === 'RETURN'">
            <div class="biz-summary">
              <div v-if="selected.businessSnapshot.orderNo"><span class="label">关联领用单</span><span class="value">{{ selected.businessSnapshot.orderNo }}</span></div>
              <div v-if="selected.businessSnapshot.applicant"><span class="label">原申请人</span><span class="value">{{ selected.businessSnapshot.applicant }}</span></div>
              <div v-if="selected.businessSnapshot.department"><span class="label">申请部门</span><span class="value">{{ selected.businessSnapshot.department }}</span></div>
              <div class="total-row"><span class="label">退库数量</span><span class="value"><b>{{ selected.businessSnapshot.returnQty }}</b> 件</span></div>
            </div>
            <el-table :data="[{sku: selected.businessSnapshot.sku, name: selected.businessSnapshot.name, spec: selected.businessSnapshot.spec, location: selected.businessSnapshot.location, requestedQty: selected.businessSnapshot.requestedQty, pickedQty: selected.businessSnapshot.pickedQty, returnedQty: selected.businessSnapshot.returnedQty}]" size="small" border stripe>
              <el-table-column prop="sku" label="SKU" width="140" />
              <el-table-column prop="name" label="备件名称" min-width="160" />
              <el-table-column prop="spec" label="规格" width="140" />
              <el-table-column prop="location" label="库位" width="100" />
              <el-table-column label="原申请/已领/已退" width="180" align="center"><template #default="{row}">{{ row.requestedQty || 0 }} / {{ row.pickedQty || 0 }} / {{ row.returnedQty || 0 }}</template></el-table-column>
            </el-table>
            <div class="reason-box"><span class="label">退库原因</span><div class="value reason-text">{{ selected.businessSnapshot.reason }}</div></div>
          </template>

          <!-- SCRAP 报废申请 -->
          <template v-else-if="selected.businessType === 'SCRAP'">
            <div class="biz-summary">
              <div v-if="selected.businessSnapshot.sku"><span class="label">SKU</span><span class="value">{{ selected.businessSnapshot.sku }}</span></div>
              <div v-if="selected.businessSnapshot.productName"><span class="label">备件名称</span><span class="value">{{ selected.businessSnapshot.productName }}</span></div>
              <div v-if="selected.businessSnapshot.spec"><span class="label">规格</span><span class="value">{{ selected.businessSnapshot.spec }}</span></div>
              <div v-if="selected.businessSnapshot.location"><span class="label">库位</span><span class="value">{{ selected.businessSnapshot.location }}</span></div>
              <div v-if="selected.businessSnapshot.batchNo"><span class="label">批次</span><span class="value">{{ selected.businessSnapshot.batchNo }}</span></div>
              <div v-if="selected.businessSnapshot.currentStock !== undefined"><span class="label">当前库存</span><span class="value">{{ selected.businessSnapshot.currentStock }} {{ selected.businessSnapshot.unit || '' }}</span></div>
              <div v-if="selected.businessSnapshot.type"><span class="label">调整类型</span><span class="value">{{ selected.businessSnapshot.type }}</span></div>
              <div class="total-row"><span class="label">报废数量</span><span class="value"><b class="danger-num">{{ selected.businessSnapshot.scrapQty }}</b> {{ selected.businessSnapshot.unit || '' }}</span></div>
            </div>
            <div class="reason-box"><span class="label">报废原因</span><div class="value reason-text">{{ selected.businessSnapshot.reason }}</div></div>
          </template>

          <!-- STOCKTAKE_DIFF 盘点差异 -->
          <template v-else-if="selected.businessType === 'STOCKTAKE_DIFF'">
            <div class="biz-summary">
              <div v-if="selected.businessSnapshot.orderNo"><span class="label">盘点单号</span><span class="value">{{ selected.businessSnapshot.orderNo }}</span></div>
              <div v-if="selected.businessSnapshot.createdBy"><span class="label">盘点人</span><span class="value">{{ selected.businessSnapshot.createdBy }}</span></div>
              <div v-if="selected.businessSnapshot.remark"><span class="label">备注</span><span class="value">{{ selected.businessSnapshot.remark }}</span></div>
              <div class="total-row"><span class="label">差异项数</span><span class="value"><b class="danger-num">{{ selected.businessSnapshot.diffCount || 0 }}</b> 项</span></div>
            </div>
            <el-table :data="selected.businessSnapshot.diffItems || []" size="small" border stripe>
              <el-table-column prop="sku" label="SKU" width="140" />
              <el-table-column prop="productName" label="备件名称" min-width="160" />
              <el-table-column prop="location" label="库位" width="100" />
              <el-table-column prop="batchNo" label="批次" width="120" />
              <el-table-column label="账面" width="70" align="right"><template #default="{row}">{{ row.bookQty }}</template></el-table-column>
              <el-table-column label="实盘" width="70" align="right"><template #default="{row}">{{ row.actualQty }}</template></el-table-column>
              <el-table-column label="差异" width="80" align="right"><template #default="{row}"><span :class="row.diffQty > 0 ? 'qty-in' : 'qty-out'">{{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}</span></template></el-table-column>
            </el-table>
          </template>
        </div>

        <!-- 流程元信息 -->
        <dl><div><dt>申请人</dt><dd>{{selected.applicant}}</dd></div><div><dt>流程版本</dt><dd>{{selected.workflowVersion?`V${selected.workflowVersion}`:'系统判定'}}</dd></div><div><dt>审批节点</dt><dd>{{selected.nodeName||'审批'}}</dd></div><div><dt>提交时间</dt><dd>{{formatTime(selected.submittedAt)}}</dd></div><div><dt>处理期限</dt><dd :class="{overdue:selected.overdue}">{{formatTime(selected.dueAt)}}{{selected.overdue?'（已超时）':''}}</dd></div><div class="reason-row"><dt>条件判定</dt><dd>{{selected.matchReason||'按业务默认流程进入审批'}}</dd></div><div><dt>流转规则</dt><dd>{{approved?'通过后进入下一节点，末级通过后执行业务':'驳回后本轮流程立即结束'}}</dd></div></dl>
        <el-alert :title="approved?'请确认业务信息无误后通过':'驳回不会执行对应的库存变更'" :type="approved?'info':'warning'" :closable="false" show-icon />
        <label class="opinion-label">审批意见 <em v-if="!approved">必填</em></label>
        <el-input v-model="opinion" type="textarea" :rows="4" maxlength="200" show-word-limit :placeholder="approved?'可填写审批意见':'请明确填写驳回原因'" />
      </div>
      <template #footer><el-button @click="reviewVisible=false">取消</el-button><el-button :type="approved?'primary':'danger'" :loading="submitting" @click="submitReview">{{approved?'确认通过':'确认驳回'}}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as approvalApi from '@/api/approvals'
import { useMobile } from '@/composables/useMobile'

const rows = ref([])
const loading = ref(false)
const filter=ref('ALL'), reviewVisible=ref(false), selected=ref(null), approved=ref(true), opinion=ref(''), submitting=ref(false)
const {isMobile}=useMobile()
const dialogWidth=computed(()=>isMobile.value?'94%':'560px')
const types = { OUTBOUND:{label:'领用申请',tag:'primary',class:'blue',icon:'Tickets'}, RETURN:{label:'退库确认',tag:'success',class:'green',icon:'RefreshLeft'}, SCRAP:{label:'报废申请',tag:'danger',class:'red',icon:'Delete'}, STOCKTAKE_DIFF:{label:'盘点差异复核',tag:'warning',class:'amber',icon:'Finished'} }
const typeMeta = type => types[type] || { label:type, tag:'info' }
const filterOptions=[{label:'全部',value:'ALL'},...Object.entries(types).map(([value,x])=>({label:x.label,value}))]
const filteredRows=computed(()=>filter.value==='ALL'?rows.value:rows.value.filter(x=>x.businessType===filter.value))
const overview=computed(()=>[{value:'ALL',label:'全部待办',class:'cyan',icon:'Stamp',count:rows.value.length},...Object.entries(types).map(([value,x])=>({...x,value,count:rows.value.filter(r=>r.businessType===value).length}))])

async function load() {
  loading.value = true
  try {
    rows.value = await approvalApi.list()
  } finally {
    loading.value = false
  }
}

function openReview(row,result){selected.value=row;approved.value=result;opinion.value='';reviewVisible.value=true}
async function submitReview(){if(!approved.value&&!opinion.value.trim())return ElMessage.warning('请填写驳回原因');submitting.value=true;try{const result=await approvalApi.review(selected.value,approved.value,opinion.value.trim());reviewVisible.value=false;ElMessage.success(!approved.value?'申请已驳回':result.completed?'审批完成，业务已执行':`当前节点通过，已流转至：${result.nextNode}`);await load()}finally{submitting.value=false}}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}
function waitText(value){if(!value)return '-';const minutes=Math.max(1,Math.floor((Date.now()-new Date(value).getTime())/60000));if(minutes<60)return `${minutes} 分钟`;const hours=Math.floor(minutes/60);if(hours<24)return `${hours} 小时`;return `${Math.floor(hours/24)} 天`}

onMounted(load)
</script>

<style scoped lang="scss">
.approval-overview{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin-bottom:18px}.metric-card{appearance:none;border:1px solid #e6edf5;background:linear-gradient(145deg,#fff,#f8fbff);border-radius:14px;padding:15px;text-align:left;display:flex;align-items:center;gap:12px;cursor:pointer;transition:.2s;color:#172b4d}.metric-card:hover,.metric-card.active{transform:translateY(-2px);border-color:#7dd3fc;box-shadow:0 10px 25px rgba(14,165,233,.11)}.metric-icon,.business-mark{display:grid;place-items:center;flex:none;border-radius:11px;color:#fff;background:linear-gradient(135deg,#06b6d4,#1677ff)}.metric-icon{width:42px;height:42px;font-size:20px}.metric-card strong{font-size:22px;display:block;line-height:1.1}.metric-card small,.business-cell small,.review-title small{display:block;color:#8492a6;margin-top:5px}.blue{background:linear-gradient(135deg,#1677ff,#38bdf8)}.green{background:linear-gradient(135deg,#10b981,#4ade80)}.red{background:linear-gradient(135deg,#f43f5e,#fb7185)}.amber{background:linear-gradient(135deg,#f59e0b,#fbbf24)}.cyan{background:linear-gradient(135deg,#06b6d4,#3b82f6)}.task-toolbar{display:flex;align-items:center;justify-content:space-between;padding:13px 15px;background:#f7fafc;border:1px solid #e8eef5;border-radius:11px;margin-bottom:12px}.task-toolbar>div{display:flex;align-items:baseline;gap:10px}.task-toolbar span{font-size:12px;color:#8492a6}.task-table{border:1px solid #edf1f6}.business-cell,.review-title{display:flex;align-items:center;gap:11px}.business-mark{width:38px;height:38px;font-size:18px}.business-cell b{display:block;color:#20324d}.node-pill{display:inline-flex;align-items:center;gap:7px;padding:5px 10px;border-radius:12px;background:#eff8ff;color:#1677ff}.node-pill i{width:6px;height:6px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 3px rgba(34,197,94,.15)}.review-sheet{display:flex;flex-direction:column;gap:16px}.review-title{padding:15px;border-radius:12px;background:linear-gradient(135deg,#f3f8ff,#f8fcff);border:1px solid #e4eef9}.review-title b{font-size:16px}.review-sheet dl{display:grid;grid-template-columns:1fr 1fr;margin:0;border:1px solid #e7edf4;border-radius:10px;overflow:hidden}.review-sheet dl div{padding:11px 13px;border-bottom:1px solid #edf1f5}.review-sheet dl div:nth-child(odd){border-right:1px solid #edf1f5}.review-sheet dl div:nth-last-child(-n+2){border-bottom:0}.review-sheet dt{color:#8492a6;font-size:12px;margin-bottom:4px}.review-sheet dd{margin:0;color:#263750;font-weight:500}.opinion-label{font-weight:600;color:#334155;margin-bottom:-8px}.opinion-label em{font-style:normal;color:#f56c6c;font-size:12px}.mobile-tasks{display:flex;flex-direction:column;gap:12px;min-height:100px}.mobile-task{border:1px solid #e5edf5;border-radius:14px;background:#fff;padding:14px;box-shadow:0 6px 18px rgba(15,35,64,.05)}.mobile-head{display:flex;justify-content:space-between;align-items:flex-start}.mobile-info{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:15px 0;padding:12px;background:#f8fafc;border-radius:10px}.mobile-info span{font-size:12px;color:#8492a6}.mobile-info b{display:block;color:#334155;margin-top:3px}.mobile-actions{display:grid;grid-template-columns:1fr 1.6fr;gap:10px}.mobile-actions .el-button{margin:0;width:100%}
.overdue{color:#ef4444;font-weight:700}.overdue-tag{margin-left:6px}
.biz-detail{border:1px solid #e7edf4;border-radius:10px;padding:12px;margin-bottom:14px;background:#fbfcfe}
.biz-summary{display:grid;grid-template-columns:1fr 1fr;gap:4px 14px;margin-bottom:10px;font-size:13px}
.biz-summary>div{display:flex;gap:8px;align-items:baseline;padding:4px 0;border-bottom:1px dashed #eef2f7}
.biz-summary .label{color:#8492a6;flex-shrink:0;font-size:12px}
.biz-summary .value{color:#263750;font-weight:500;word-break:break-all}
.biz-summary .total-row{grid-column:1/-1;border-top:2px solid #e0e7f0;border-bottom:0;margin-top:4px;padding-top:8px}
.biz-summary .total-row .value b{color:#ef4444;font-size:15px}
.reason-box{margin-top:10px;padding:10px 12px;background:#fef3f2;border:1px solid #fde2e0;border-radius:8px}
.reason-box .label{color:#b42318;font-size:12px;display:block;margin-bottom:4px}
.reason-box .reason-text{color:#7a271a;font-size:13px;line-height:1.6}
.qty-in{color:#16a34a;font-weight:700}
.qty-out{color:#ef4444;font-weight:700}
.danger-num{color:#ef4444;font-size:15px}
@media(max-width:767px){.biz-summary{grid-template-columns:1fr}}
// ---------- 移动端（App 外壳，统一套路） ----------
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
  padding: 12px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
}
.m-filter {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.m-chips-row {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
  .m-chip-btn { flex-shrink: 0; }
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
.m-icon-btn {
  margin-left: auto;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f6f9ff;
  color: #2e6cf6;
  display: grid;
  place-items: center;
  cursor: pointer;
}
.spinning { animation: m-spin 0.8s linear infinite; }
@keyframes m-spin { to { transform: rotate(360deg); } }
.m-count { font-size: 11px; color: #b6c0cd; }
.m-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-red { background: #fdeaea; color: #e34d59; }
}
.m-primary-btn {
  border: 0;
  color: #fff;
  background: linear-gradient(135deg, #2e6ef2, #1e50d8);
  &:active { opacity: .9; }
}
// 移动任务卡软阴影
.mobile-task {
  margin: 0 12px;
  border: 0;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
}
@media(max-width:1100px){.approval-overview{grid-template-columns:repeat(3,1fr)}}
@media(max-width:767px){.approval-overview{display:flex;overflow-x:auto;padding-bottom:5px}.metric-card{min-width:135px;padding:12px}.metric-icon{width:36px;height:36px}.metric-card strong{font-size:18px}.task-toolbar{padding:10px;gap:10px}.task-toolbar>div{display:block}.task-toolbar span{display:block;margin-top:3px}.review-sheet dl{grid-template-columns:1fr}.review-sheet dl div,.review-sheet dl div:nth-child(odd){border-right:0;border-bottom:1px solid #edf1f5}.review-sheet dl div:last-child{border-bottom:0}}
</style>
