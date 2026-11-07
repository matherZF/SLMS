<template>
  <div class="workbench" v-loading="loading">
    <section class="welcome"><div><span class="hello">你好，{{ auth.displayName }}</span><p>备品备件库存与作业概况</p></div><el-icon :size="42"><Box /></el-icon></section>
    <section class="stats-grid"><button v-for="item in stats" :key="item.label" @click="router.push(item.path)"><el-icon :size="25"><component :is="item.icon" /></el-icon><strong>{{ item.value }}</strong><span>{{ item.label }}</span></button></section>
    <section class="quick-grid"><button v-for="item in quickActions" :key="item.path" @click="router.push(item.path)"><el-icon :size="25"><component :is="item.icon" /></el-icon><span>{{ item.label }}</span><small>{{ item.description }}</small></button></section>
    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card class="panel" shadow="never"><template #header><b>业务待办</b></template>
          <div v-if="auth.can('page:approval')" class="todo-row" @click="router.push('/approvals')"><span>待办审批</span><el-tag type="warning">{{ data.pendingApproval||0 }}</el-tag></div>
          <div v-if="auth.can('page:approval') && data.overdueApproval" class="todo-row overdue-row" @click="router.push('/approvals')"><span>超时审批</span><el-tag type="danger">{{ data.overdueApproval }}</el-tag></div>
          <div v-if="auth.can('outbound:picking')" class="todo-row" @click="router.push('/outbound/picking/list')"><span>待拣货领用单</span><el-tag type="danger">{{ data.pendingPicking||0 }}</el-tag></div>
          <div v-if="auth.can('page:stocktake')" class="todo-row" @click="router.push('/stocktakes')"><span>进行中盘点</span><el-tag type="primary">{{ data.stocktakeCounting||0 }}</el-tag></div>
          <div v-if="auth.can('page:alerts')" class="todo-row" @click="router.push('/alerts')"><span>库存与更换预警（{{ data.alertCount||0 }} 条）</span><el-tag v-if="data.alertUnread" type="danger">{{ data.alertUnread }} 条未读</el-tag><el-tag v-else>已全部读完</el-tag></div>
        </el-card>
        <el-card class="panel export-panel" shadow="never"><template #header><b>数据导出</b></template><el-button type="primary" plain :loading="exportingInventory" @click="exportInventory">导出库存明细 CSV</el-button><el-button plain :loading="exportingLogs" @click="exportLogs">导出库存流水 CSV</el-button></el-card>
      </el-col>
      <el-col :xs="24" :lg="16"><el-card class="panel" shadow="never"><template #header><div class="panel-header"><b>近期库存变动</b><el-button link type="primary" @click="router.push('/inventory/logs')">查看全部</el-button></div></template>
        <el-table :data="data.recentLogs||[]" stripe><el-table-column label="时间" min-width="150"><template #default="{row}">{{ formatTime(row.createdAt) }}</template></el-table-column><el-table-column prop="sku" label="SKU" min-width="110"/><el-table-column prop="productName" label="备件" min-width="130"/><el-table-column prop="locationCode" label="库位" width="100"/><el-table-column label="变动" width="90" align="center"><template #default="{row}"><span :class="row.afterQty>=row.beforeQty?'up':'down'">{{row.afterQty>=row.beforeQty?'+':'-'}}{{row.changeQty}}</span></template></el-table-column><el-table-column prop="operator" label="操作人" width="90"/></el-table>
      </el-card></el-col>
    </el-row>
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
const router=useRouter(),auth=useAuthStore(),loading=ref(false),data=reactive({recentLogs:[]}),exportingInventory=ref(false),exportingLogs=ref(false)
const stats=computed(()=>[{label:'库存总量',value:data.totalQty||0,path:'/inventory',icon:'Goods'},{label:'有库存备件',value:data.skuCount||0,path:'/inventory',icon:'Box'},{label:'使用库位',value:data.locationCount||0,path:'/inventory',icon:'Location'},{label:'30天内临期批次',value:data.expiryCount||0,path:'/alerts',icon:'Warning'}])
const quickActions=computed(()=>[
  ...(auth.canOperateWarehouse?[{path:'/orders',label:'入库管理',description:'收货与上架',icon:'Download'},{path:'/outbound',label:'领用出库',description:'申请与扫码确认',icon:'Upload'},{path:'/stocktakes',label:'库存盘点',description:'实盘与差异调整',icon:'Finished'}]:[]),
  ...(auth.can('page:approval')?[{path:'/approvals',label:'审批中心',description:'处理待审批业务',icon:'Stamp'}]:[]),
  {path:'/inventory',label:'库存台账',description:'库存与批次查询',icon:'Search'}
])
async function load(){loading.value=true;try{Object.assign(data,await dashboardApi.summary())}finally{loading.value=false}}
function csvCell(value){let text=value==null?'':String(value);if(/^[=+\-@]/.test(text))text="'"+text;return `"${text.replaceAll('"','""')}"`}
function downloadCsv(name,headers,rows){const content='\ufeff'+[headers,...rows].map(row=>row.map(csvCell).join(',')).join('\r\n');const url=URL.createObjectURL(new Blob([content],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=name;a.click();URL.revokeObjectURL(url)}
async function exportInventory(){exportingInventory.value=true;try{const rows=await inventoryApi.details({});downloadCsv(`库存明细_${dateText()}.csv`,['SKU','备件名称','库位','区域','数量','批次','生产日期','到期日','生产厂商'],rows.map(r=>[r.sku,r.productName,r.locationCode,r.area,r.qty,r.itemKey,r.productionDate,r.expiryDate,r.manufacturer]));ElMessage.success('库存明细已导出')}finally{exportingInventory.value=false}}
async function exportLogs(){exportingLogs.value=true;try{const result=await logApi.page({page:0,size:10000});const rows=result.content||[];downloadCsv(`库存流水_${dateText()}.csv`,['时间','类型','SKU','备件名称','库位','变动数量','变动前','变动后','关联单号','操作人','备注'],rows.map(r=>[formatTime(r.createdAt),r.changeType,r.sku,r.productName,r.locationCode,r.changeQty,r.beforeQty,r.afterQty,r.refNo,r.operator,r.remark]));ElMessage.success(`已导出 ${rows.length} 条流水`)}finally{exportingLogs.value=false}}
function dateText(){const d=new Date();return `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`}
function formatTime(v){return v?new Date(v).toLocaleString('zh-CN',{hour12:false}):''}
onMounted(load)
</script>

<style scoped lang="scss">
.workbench{padding:14px}.welcome{display:flex;justify-content:space-between;align-items:center;padding:20px;color:#fff;background:linear-gradient(135deg,$primary,#2563a8);border-radius:14px}.hello{font-size:20px;font-weight:700}.welcome p{margin:6px 0 0;opacity:.86}.stats-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0}.stats-grid button{border:0;background:#fff;border-radius:12px;min-height:95px;display:grid;grid-template-columns:36px 1fr;grid-template-rows:1fr 1fr;text-align:left;padding:15px;box-shadow:0 2px 10px rgba(0,0,0,.05);cursor:pointer}.stats-grid .el-icon{grid-row:1/3;align-self:center;color:$primary}.stats-grid strong{font-size:23px}.stats-grid span{font-size:12px;color:#909399}.quick-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:16px}.quick-grid button{min-height:90px;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:4px;border:0;border-radius:12px;background:#fff;color:#303133;box-shadow:0 2px 10px rgba(0,0,0,.05);cursor:pointer}.quick-grid .el-icon{color:$primary}.quick-grid small{color:#909399}.panel{border-radius:12px;margin-bottom:16px}.todo-row{min-height:48px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #ebeef5;cursor:pointer}.todo-row:last-child{border-bottom:0}.panel-header{display:flex;justify-content:space-between;align-items:center}.export-panel .el-button{margin:0 8px 8px 0}.up{color:#67c23a;font-weight:700}.down{color:#f56c6c;font-weight:700}
@media(max-width:767px){.workbench{padding:10px}.stats-grid,.quick-grid{grid-template-columns:repeat(2,1fr)}.stats-grid button{min-height:82px;padding:10px}.stats-grid strong{font-size:20px}}
</style>
