<template>
  <div class="page-container">
    <PageHeader>系统日志</PageHeader>
    <el-alert title="日志只读，集中记录登录/登出、用户管理、配置变更、关键业务操作及状态变化；库存数量明细仍以库存流水为准。" type="info" :closable="false" show-icon style="margin-bottom:16px" />
    <div class="filter-bar">
      <el-select v-model="filters.businessType" clearable placeholder="业务类型" style="width:180px" @change="search">
        <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="filters.keyword" clearable placeholder="单号/编码/操作人/内容" style="width:280px" @keyup.enter="search" />
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>&nbsp;重置</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column label="操作时间" width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="业务类型" width="120"><template #default="{ row }"><el-tag>{{ businessTypeText(row.businessType) }}</el-tag></template></el-table-column>
      <el-table-column prop="businessNo" label="单号/编码" min-width="150"><template #default="{ row }">{{ row.businessNo || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="130"><template #default="{ row }">{{ actionText(row.action) }}</template></el-table-column>
      <el-table-column label="状态变化" min-width="170"><template #default="{ row }"><span v-if="row.fromStatus !== row.toStatus">{{ statusText(row.fromStatus) }} → {{ statusText(row.toStatus) }}</span><span v-else>{{ statusText(row.toStatus) || '-' }}</span></template></el-table-column>
      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column prop="detail" label="操作内容" min-width="300" show-overflow-tooltip />
    </el-table>
    <div style="margin-top:16px;display:flex;justify-content:flex-end">
      <el-pagination v-model:current-page="page.page" v-model:page-size="page.size" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import * as auditApi from '@/api/auditLogs'

const filters = reactive({ businessType: '', keyword: '' })
const page = reactive({ page: 1, size: 20 })
const rows = ref([]); const total = ref(0); const loading = ref(false)
const businessTypes = [
  { value: 'AUTH', label: '登录认证' },
  { value: 'OUTBOUND', label: '领用出库' }, { value: 'PUTAWAY', label: '入库上架' },
  { value: 'INVENTORY', label: '库存作业' }, { value: 'STOCKTAKE', label: '库存盘点' },
  { value: 'PRODUCT', label: '备件主数据' }, { value: 'LOCATION', label: '库位' },
  { value: 'LOCATION_LAYOUT', label: '仓库布局' }, { value: 'EQUIPMENT', label: '设备' },
  { value: 'USER', label: '用户权限' }, { value: 'ROLE', label: '角色权限' },
  { value: 'DICTIONARY', label: '数据字典' }, { value: 'SPARE_USAGE', label: '备件使用台账' },
  { value: 'ALERT', label: '预警管理' }, { value: 'SYSTEM_CONFIG', label: '系统配置' },
  { value: 'WORKFLOW', label: '流程配置' }, { value: 'ES_TAG', label: '电子标签' },
  { value: 'DEVICE_STAT', label: '设备运行数据' }
]
async function load() { loading.value = true; try { const r = await auditApi.list({ businessType: filters.businessType || undefined, keyword: filters.keyword.trim() || undefined, page: page.page - 1, size: page.size }); rows.value = r.content || []; total.value = r.totalElements || 0 } finally { loading.value = false } }
function search() { page.page = 1; load() }
function reset() { filters.businessType = ''; filters.keyword = ''; search() }
function businessTypeText(v) { return businessTypes.find(x => x.value === v)?.label || v }
function actionText(v) { return ({ LOGIN:'登录', LOGIN_FAIL:'登录失败', CHANGE_PASSWORD:'修改密码', CREATE:'创建', UPDATE:'修改', DELETE:'删除', APPROVE:'审批通过', REJECT:'审批驳回', CANCEL:'取消', ASSIGN_LOCATION:'分配库位', START_PICKING:'开始拣货', SCAN_VERIFY:'扫码核验', COMPLETE:'完成', RETURN:'退库', RETURN_SUBMIT:'提交退库', SCRAP:'报废', SCRAP_SUBMIT:'提交报废', TRANSFER:'移库', COUNT:'录入实盘', PUTAWAY_CONFIRM:'确认上架', INITIALIZE:'初始化', BIND_PART:'关联备件', UPDATE_PART:'修改关联', RESET_PASSWORD:'重置密码', REFRESH:'刷新运行数据', BATCH_REFRESH:'批量刷新运行数据', CLOSE:'结束使用跟踪', ACKNOWLEDGE:'处理预警', IGNORE:'忽略预警', RESET:'恢复默认值', RESUBMIT:'重新提交', SYNC:'同步数据', UNBIND:'解除绑定' })[v] || v }
function statusText(v) { return ({ PENDING_APPROVAL:'待审批', APPROVED:'待拣货', PICKING:'拣货中', DONE:'已完成', REJECTED:'已驳回', CANCELLED:'已取消', PENDING:'待上架', COUNTING:'盘点中', IN_USE:'使用中', NEAR_DUE:'寿命临近', DUE:'已到寿命', BASELINE_PENDING:'待补基准', NOT_TRACKED:'不计算', RETURNED:'已全部退回', CLOSED:'已结束' })[v] || v || '' }
function formatTime(v) { if (!v) return '-'; return new Date(v).toLocaleString('zh-CN', { hour12: false }) }
onMounted(load)
</script>
