<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader v-if="!isMobile">库存盘点</PageHeader>
    <div class="toolbar" :class="{ 'm-toolbar': isMobile }">
      <el-button v-if="auth.can('stocktake:create')" :class="{ 'm-primary-btn': isMobile }" @click="createVisible = true"><el-icon><Plus /></el-icon>&nbsp;创建全库盘点</el-button>
      <el-button @click="load"><el-icon><Refresh /></el-icon>&nbsp;刷新</el-button>
    </div>
    <div v-if="isMobile" v-loading="loading" class="m-card-list">
      <el-empty v-if="!loading && rows.length === 0" description="暂无盘点单" :image-size="70" />
      <article v-for="row in rows" :key="row.id" class="m-card" @click="openDetail(row)">
        <div class="m-card-head">
          <b class="m-no">{{ row.orderNo }}</b>
          <span class="m-tag" :class="statusTone(row.status)">{{ statusText(row.status) }}</span>
        </div>
        <div class="m-line"><span class="m-label">创建人</span><span class="m-val">{{ row.createdBy || '-' }}</span></div>
        <div class="m-line"><span class="m-label">创建时间</span><span class="m-val">{{ formatTime(row.createdAt) }}</span></div>
        <div class="m-line" v-if="row.remark"><span class="m-label">备注</span><span class="m-val">{{ row.remark }}</span></div>
        <div class="m-line"><span class="m-label">进度</span><span class="m-val"><b>{{ row.countedCount || 0 }}</b> / {{ row.itemCount || 0 }} 项</span></div>
        <el-progress :percentage="row.itemCount ? Math.round(row.countedCount*100/row.itemCount) : 0" :color="row.status==='DONE' ? '#12b76a' : '#2e6ef2'" :show-text="false" :stroke-width="6" style="margin-top:10px" />
      </article>
    </div>
    <el-table v-else v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="orderNo" label="盘点单号" min-width="190" />
      <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="row.status==='DONE'?'success':row.status==='PENDING_REVIEW'?'warning':'primary'">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="进度" min-width="160"><template #default="{row}"><el-progress :percentage="row.itemCount ? Math.round(row.countedCount*100/row.itemCount) : 0" /></template></el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="110" />
      <el-table-column label="创建时间" width="170"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openDetail(row)">{{ row.status==='DONE'?'查看':'盘点' }}</el-button></template></el-table-column>
    </el-table>
    <div class="pagination"><el-pagination v-model:current-page="pager.page" v-model:page-size="pager.size" :total="pager.total" layout="total, prev, pager, next" @current-change="load" /></div>

    <el-dialog v-model="createVisible" title="创建全库盘点" :width="dialogWidth">
      <el-alert title="创建时将冻结当前所有库存记录的账面数量；盘点完成前不会调整库存。" type="info" :closable="false" />
      <el-input v-model="createRemark" type="textarea" :rows="3" maxlength="255" placeholder="盘点说明（可选）" class="remark-input" />
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="creating" @click="createOrder">确认创建</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="current?.orderNo || '盘点明细'" :width="detailWidth" top="4vh">
      <div v-loading="detailLoading">
        <el-row v-if="current" :gutter="12" class="summary-row">
          <el-col :span="8"><el-statistic title="明细数" :value="current.itemCount" /></el-col>
          <el-col :span="8"><el-statistic title="已盘" :value="current.countedCount" /></el-col>
          <el-col :span="8"><el-statistic title="差异项" :value="differenceCount" /></el-col>
        </el-row>
        <el-table v-if="current" :data="current.items" border stripe max-height="58vh">
          <el-table-column prop="locationCode" label="库位" min-width="110" />
          <el-table-column label="备件" min-width="190"><template #default="{row}"><b>{{row.sku}}</b> {{row.productName}}</template></el-table-column>
          <el-table-column prop="itemKey" label="批次" min-width="120"><template #default="{row}">{{row.itemKey||'-'}}</template></el-table-column>
          <el-table-column prop="bookQty" label="账面数" width="85" align="center" />
          <el-table-column label="实盘数" width="145" align="center"><template #default="{row}"><el-input-number v-if="current.status==='COUNTING'" v-model="row.actualQty" :min="0" size="small" controls-position="right" @change="saveCount(row)" /><span v-else>{{row.actualQty}}</span></template></el-table-column>
          <el-table-column label="差异" width="80" align="center"><template #default="{row}"><span :class="differenceClass(row)">{{ row.actualQty == null ? '-' : row.actualQty-row.bookQty }}</span></template></el-table-column>
        </el-table>
      </div>
      <template #footer><el-button @click="detailVisible=false">关闭</el-button><el-button v-if="current?.status==='COUNTING' && auth.can('stocktake:complete')" type="primary" :disabled="current.countedCount!==current.itemCount" :loading="completing" @click="completeOrder">确认差异并完成</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'
import * as api from '@/api/stocktake'

const auth=useAuthStore(), {isMobile}=useMobile()
const dialogWidth=computed(()=>isMobile.value?'92%':'480px'), detailWidth=computed(()=>isMobile.value?'96%':'900px')
const loading=ref(false), rows=ref([]), pager=reactive({page:1,size:20,total:0})
const createVisible=ref(false), createRemark=ref(''), creating=ref(false)
const detailVisible=ref(false), detailLoading=ref(false), current=ref(null), completing=ref(false)
const differenceCount=computed(()=>current.value?.items?.filter(i=>i.actualQty!=null&&i.actualQty!==i.bookQty).length||0)

async function load(){loading.value=true;try{const r=await api.list({page:pager.page-1,size:pager.size});rows.value=r.content||[];pager.total=r.totalElements||0}finally{loading.value=false}}
async function createOrder(){creating.value=true;try{const r=await api.create(createRemark.value.trim());createVisible.value=false;createRemark.value='';await load();await openDetail(r)}finally{creating.value=false}}
async function openDetail(row){detailVisible.value=true;detailLoading.value=true;try{current.value=await api.detail(row.id)}finally{detailLoading.value=false}}
async function saveCount(row){if(row.actualQty==null)return;try{current.value=await api.count(current.value.id,row.id,row.actualQty);ElMessage.success('实盘数已保存')}catch{current.value=await api.detail(current.value.id)}}
async function completeOrder(){try{await ElMessageBox.confirm(`确认提交盘点结果吗？共有 ${differenceCount.value} 个差异项；有差异时审批通过后才调整库存。`,'提交盘点',{type:'warning'})}catch{return}completing.value=true;try{current.value=await api.complete(current.value.id);ElMessage.success(current.value.status==='PENDING_REVIEW'?'盘点差异已提交复核':'盘点完成，无库存差异');await load()}finally{completing.value=false}}
const statusText=s=>({COUNTING:'盘点中',PENDING_REVIEW:'差异复核中',DONE:'已完成',CANCELLED:'已取消'}[s]||s)
const statusTone=s=>({COUNTING:'t-blue',PENDING_REVIEW:'t-amber',DONE:'t-green',CANCELLED:'t-gray'}[s]||'t-gray')
function differenceClass(row){if(row.actualQty==null||row.actualQty===row.bookQty)return '';return row.actualQty>row.bookQty?'diff-up':'diff-down'}
function formatTime(v){return v?new Date(v).toLocaleString('zh-CN',{hour12:false}):''}
onMounted(load)
</script>

<style scoped lang="scss">
.toolbar{display:flex;gap:10px;margin-bottom:16px}.pagination{display:flex;justify-content:flex-end;margin-top:16px}.remark-input{margin-top:16px}.summary-row{margin-bottom:16px;text-align:center}.diff-up{color:#67c23a;font-weight:700}.diff-down{color:#f56c6c;font-weight:700}
// ---------- 移动端 ----------
.m-page{background:transparent;padding:0 0 12px;box-shadow:none;border:0;border-radius:0;min-height:auto}
.m-toolbar{margin:12px 12px 0;background:#fff;border-radius:14px;padding:12px;box-shadow:0 2px 10px rgba(30,60,120,.05);.el-button{flex:1;margin:0;min-height:40px}}
.m-primary-btn{border:0;color:#fff;background:linear-gradient(135deg,#2e6ef2,#1e50d8);&:active{opacity:.9}}
.m-card-list{display:flex;flex-direction:column;gap:10px;margin:12px;min-height:120px}
.m-card{background:#fff;border-radius:14px;padding:14px;box-shadow:0 2px 10px rgba(30,60,120,.05);cursor:pointer}
.m-card-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.m-no{font-size:15px;color:#1e293b}
.m-tag{flex-shrink:0;font-size:11px;padding:3px 10px;border-radius:10px;&.t-blue{background:#eaf1ff;color:#2e6cf6}&.t-amber{background:#fbf3da;color:#d9a400}&.t-green{background:#e6f7ef;color:#12b76a}&.t-gray{background:#f1f5f9;color:#94a3b8}}
.m-line{display:flex;font-size:13px;padding:3px 0}
.m-label{width:64px;flex-shrink:0;color:#94a3b8}
.m-val{flex:1;min-width:0;color:#334155;word-break:break-all}
@media(max-width:767px){:deep(.el-dialog__body){padding:12px}}
</style>
