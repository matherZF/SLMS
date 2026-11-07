<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <el-page-header v-if="!isMobile" :content="null" @back="$router.back()" style="margin-bottom: 12px">
      <template #content><span style="font-size: 18px; font-weight: 600">领用单详情</span></template>
    </el-page-header>

    <div v-loading="loading">
      <template v-if="detail">
        <!-- 头卡：单号 + 状态 + 基础信息 -->
        <section class="card-d">
          <div class="detail-top">
            <span class="order-no">{{ detail.orderNo }}</span>
            <span class="m-tag" :class="statusTone(detail.status)">{{ statusText(detail.status) }}</span>
          </div>
          <div class="info-grid">
            <div class="info-item"><div class="label-muted">申请人</div><div class="value">{{ detail.applicant || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">领用部门 / 领用人</div><div class="value">{{ detail.department || '-' }} / {{ detail.recipient || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">联系电话</div><div class="value">{{ detail.contactPhone || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">工单号</div><div class="value">{{ detail.workOrderNo || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">期望日期</div><div class="value">{{ detail.requiredDate || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">交付地点</div><div class="value">{{ detail.deliveryLocation || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">关联设备</div><div class="value">{{ detailEquipmentText }}</div></div>
            <div class="info-item"><div class="label-muted">领用用途</div><div class="value">{{ detail.purpose || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">审批信息</div><div class="value">{{ detail.reviewedBy || '-' }}<template v-if="detail.reviewOpinion"> / {{ detail.reviewOpinion }}</template></div></div>
            <div class="info-item"><div class="label-muted">完成信息</div><div class="value">{{ detail.completedBy || '-' }}<template v-if="detail.completedAt"> / {{ formatTime(detail.completedAt) }}</template></div></div>
          </div>
        </section>

        <!-- 货品明细 -->
        <section class="card-d">
          <div class="card-title">货品明细（{{ detail.items?.length || 0 }}）</div>
          <div v-if="isMobile" class="d-items">
            <div v-for="row in detail.items || []" :key="row.id" class="d-item">
              <div class="d-item-main">
                <div class="d-item-name"><b>{{ row.sku }}</b> {{ row.name }}</div>
                <div v-if="row.spec" class="d-item-sub">{{ row.spec }} · {{ row.unit || '件' }}</div>
                <div class="d-item-sub">
                  库位 <b>{{ row.locationCode || '未分配' }}</b><template v-if="row.locationArea">（{{ row.locationArea }}）</template>
                  <template v-if="row.deviceNo"> · 设备 {{ row.deviceNo }}</template>
                </div>
                <div class="d-item-sub">关联设备 <b>{{ row.equipmentCode ? `${row.equipmentCode} - ${row.equipmentName}` : '未指定' }}</b></div>
              </div>
              <div class="d-item-right">
                <span class="d-qty">需 {{ row.requestedQty }} / 拣 {{ row.pickedQty || 0 }}</span>
                <span v-if="detail.status === 'DONE'" class="d-qty">可退 {{ row.returnableQty }}</span>
                <el-button v-if="detail.status === 'DONE' && row.returnableQty > 0" type="primary" plain size="small" @click="openReturn(row)">退库</el-button>
              </div>
            </div>
          </div>
          <el-table v-else :data="detail.items" stripe border style="width: 100%">
            <el-table-column label="货品" min-width="180">
              <template #default="{ row }">
                <b>{{ row.sku }}</b>
                <span style="margin-left:6px">{{ row.name }}</span>
                <div v-if="row.spec" style="font-size:12px;color:#909399">{{ row.spec }} / {{ row.unit || '件' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="关联设备" min-width="150">
              <template #default="{ row }">
                <span v-if="row.equipmentCode">{{ row.equipmentCode }} - {{ row.equipmentName }}</span>
                <span v-else style="color:#909399">未指定</span>
              </template>
            </el-table-column>
            <el-table-column prop="requestedQty" label="需求数量" width="100" align="center" />
            <el-table-column prop="pickedQty" label="已拣数量" width="100" align="center" />
            <el-table-column prop="returnedQty" label="已退数量" width="90" align="center" />
            <el-table-column prop="returnableQty" label="可退数量" width="90" align="center" />
            <el-table-column label="库位" min-width="140">
              <template #default="{ row }">
                <span v-if="row.locationCode">
                  {{ row.locationCode }}
                  <span v-if="row.locationArea" style="color:#909399">({{ row.locationArea }})</span>
                </span>
                <span v-else style="color:#c0c4cc">未分配</span>
              </template>
            </el-table-column>
            <el-table-column label="设备" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.deviceNo" size="small" type="warning" effect="plain">{{ row.deviceNo }}</el-tag>
                <span v-else style="color:#c0c4cc">未绑定</span>
              </template>
            </el-table-column>
            <el-table-column v-if="detail.status === 'DONE'" label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link :disabled="row.returnableQty <= 0" @click="openReturn(row)">退库</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <!-- 审批记录 -->
        <section v-if="detail.approvalRecords?.length" class="card-d">
          <div class="card-title">审批记录</div>
          <div v-if="isMobile" class="a-rows">
            <div v-for="(r, i) in detail.approvalRecords" :key="i" class="a-row">
              <div class="a-main">
                <div class="a-node">{{ r.nodeName || '审批节点' }}<span class="a-round">第 {{ r.roundNo }} 轮</span></div>
                <div class="a-sub">{{ r.approver || '-' }} · {{ formatTime(r.handledAt || r.submittedAt) }}</div>
                <div v-if="r.opinion" class="a-opinion">{{ r.opinion }}</div>
              </div>
              <span class="m-tag" :class="recordTone(r.status)">{{ r.status }}</span>
            </div>
          </div>
          <el-table v-else :data="detail.approvalRecords" border size="small" style="width: 100%">
            <el-table-column prop="roundNo" label="审批轮次" width="90" />
            <el-table-column prop="nodeName" label="流程节点" width="130" />
            <el-table-column prop="applicant" label="提交人" width="110" />
            <el-table-column prop="approver" label="审批人" width="110" />
            <el-table-column prop="status" label="结果" width="100" />
            <el-table-column prop="opinion" label="审批意见" min-width="180" />
            <el-table-column label="处理时间" width="170"><template #default="{ row }">{{ formatTime(row.handledAt || row.submittedAt) }}</template></el-table-column>
          </el-table>
        </section>

        <!-- 实物与库存流转 -->
        <section class="card-d">
          <div class="card-title">库存流转</div>
          <div v-if="isMobile">
            <div v-for="(row, i) in detail.inventoryLogs || []" :key="i" class="a-row">
              <div class="a-main">
                <div class="a-node">{{ row.sku }} {{ row.productName }}</div>
                <div class="a-sub">{{ formatTime(row.createdAt) }} · {{ row.operator || '-' }}</div>
                <div class="a-sub">{{ row.locationCode || '-' }} · {{ row.beforeQty }} → {{ row.afterQty }}（{{ row.changeType === 'RETURN' ? '+' : '-' }}{{ row.changeQty }}）</div>
              </div>
              <span class="m-tag" :class="row.changeType === 'RETURN' ? 't-amber' : 't-green'">{{ row.changeType === 'RETURN' ? '退库' : '出库' }}</span>
            </div>
            <el-empty v-if="!detail.inventoryLogs?.length" description="尚未发生库存变动" :image-size="60" />
          </div>
          <el-table v-else-if="detail.inventoryLogs?.length" :data="detail.inventoryLogs" border stripe size="small">
            <el-table-column label="时间" width="165"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="动作" width="85"><template #default="{ row }"><el-tag :type="row.changeType === 'RETURN' ? 'warning' : 'success'">{{ row.changeType === 'RETURN' ? '退库' : '出库' }}</el-tag></template></el-table-column>
            <el-table-column prop="sku" label="备件编码" min-width="120" />
            <el-table-column prop="productName" label="备件名称" min-width="130" />
            <el-table-column prop="itemKey" label="批次" min-width="110"><template #default="{ row }">{{ row.itemKey || '无批次' }}</template></el-table-column>
            <el-table-column prop="locationCode" label="原库位" min-width="110" />
            <el-table-column label="库存变化" min-width="130"><template #default="{ row }">{{ row.beforeQty }} → {{ row.afterQty }}（{{ row.changeType === 'RETURN' ? '+' : '-' }}{{ row.changeQty }}）</template></el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="remark" label="说明" min-width="180" show-overflow-tooltip />
          </el-table>
          <el-empty v-else description="尚未发生库存变动" :image-size="60" />
        </section>

        <!-- 操作审计 -->
        <section class="card-d">
          <div class="card-title">操作记录</div>
          <el-timeline v-if="detail.auditLogs?.length" style="padding-left: 4px">
            <el-timeline-item v-for="log in detail.auditLogs" :key="log.id" :timestamp="formatTime(log.createdAt)" placement="top">
              <b>{{ auditActionText(log.action) }}</b>
              <span v-if="log.fromStatus !== log.toStatus" class="m-tag t-blue" style="margin-left: 8px">{{ statusText(log.fromStatus) }} → {{ statusText(log.toStatus) }}</span>
              <div class="a-sub" style="margin-top: 4px">操作人：{{ log.operator || '-' }}；{{ log.detail || '-' }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审计记录" :image-size="60" />
        </section>

        <!-- 底部操作 -->
        <div class="footer-actions">
          <el-button v-if="detail.status === 'APPROVED' || detail.status === 'PICKING'" class="m-primary-btn" @click="goPicking">
            <el-icon><Box /></el-icon>&nbsp;去拣货
          </el-button>
          <el-button v-if="detail.status === 'REJECTED' && auth.can('outbound:create')" @click="resubmitOrder">重新提交</el-button>
          <el-button v-if="detail.status === 'PENDING_APPROVAL' || detail.status === 'APPROVED'" type="danger" plain @click="cancelOrder">取消申请</el-button>
          <el-button @click="$router.back()"><el-icon><ArrowLeft /></el-icon>&nbsp;返回列表</el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="单据不存在或已被删除" />
    </div>

    <!-- 退库 Dialog -->
    <el-dialog v-model="returnDialogVisible" title="领用退库" :width="isMobile ? '92%' : '500px'" destroy-on-close>
      <el-descriptions v-if="returnItemRow" :column="1" border class="return-summary">
        <el-descriptions-item label="原领用单">{{ detail?.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="备件">{{ returnItemRow.sku }} - {{ returnItemRow.name }}</el-descriptions-item>
        <el-descriptions-item label="原库位/批次">{{ returnItemRow.locationCode }} / {{ returnItemRow.itemKey || '无批次' }}</el-descriptions-item>
        <el-descriptions-item label="领用/已退/可退">{{ returnItemRow.pickedQty }} / {{ returnItemRow.returnedQty }} / {{ returnItemRow.returnableQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="86px">
        <el-form-item label="退库数量" required>
          <el-input-number v-model="returnForm.qty" :min="1" :max="returnItemRow?.returnableQty || 1" />
        </el-form-item>
        <el-form-item label="退库原因" required>
          <el-input v-model="returnForm.reason" type="textarea" :rows="3" maxlength="180" show-word-limit placeholder="请填写未使用、错领等退库原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="returnSubmitting" @click="submitReturn">确认退库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as outboundApi from '@/api/outbound'
import { useMobile } from '@/composables/useMobile'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isMobile } = useMobile()

const detail = ref(null)
const loading = ref(false)
const returnDialogVisible = ref(false)
const returnSubmitting = ref(false)
const returnItemRow = ref(null)
const returnForm = reactive({ qty: 1, reason: '' })

/**
 * 单头那格「关联设备」只是给列表/详情看的汇总：设备实际挂在明细上，
 * 一条明细一台。多个明细指向不同设备时显示台数，避免看起来像整单都是某一台。
 */
const detailEquipmentText = computed(() => {
  const items = detail.value?.items || []
  const names = [...new Set(items
    .map(i => (i.equipmentCode ? `${i.equipmentCode} - ${i.equipmentName}` : null))
    .filter(Boolean))]
  if (names.length === 1) return names[0]
  if (names.length > 1) return `多台设备（${names.length} 台）`
  // 老单据明细上没有设备（V26 之前建的），退回单头那份
  return detail.value?.equipmentCode ? `${detail.value.equipmentCode} - ${detail.value.equipmentName}` : '-'
})

async function loadDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    detail.value = await outboundApi.detail(id)
  } finally {
    loading.value = false
  }
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

function statusTone(s) {
  if (s === 'PENDING_APPROVAL') return 't-amber'
  if (s === 'APPROVED') return 't-blue'
  if (s === 'PICKING') return 't-orange'
  if (s === 'DONE') return 't-green'
  if (s === 'REJECTED') return 't-red'
  return 't-gray'
}

function recordTone(s) {
  if (s === '通过' || s === 'APPROVED' || s === 'AUTO_APPROVED') return 't-green'
  if (s === '驳回' || s === 'REJECTED') return 't-red'
  return 't-gray'
}

function auditActionText(action) {
  return ({ CREATE: '创建申请', RESUBMIT: '重新提交', APPROVE: '审批通过', REJECT: '审批驳回', ASSIGN_LOCATION: '分配库位', START_PICKING: '开始拣货', SCAN_VERIFY: '扫码核验', COMPLETE: '完成出库', RETURN: '领用退库', CANCEL: '取消申请' })[action] || action
}

function goPicking() {
  router.push(`/outbound/picking/${detail.value.id}`)
}

async function resubmitOrder() {
  try {
    await ElMessageBox.confirm(`确认重新提交领用单 ${detail.value.orderNo}？`, '重新提交')
  } catch {
    return
  }
  await outboundApi.resubmit(detail.value.id)
  ElMessage.success('已重新提交审批')
  loadDetail()
}

async function cancelOrder() {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因（可选）', `取消领用单 ${detail.value.orderNo}`, { inputType: 'textarea', confirmButtonText: '确认取消', cancelButtonText: '返回' })
    await outboundApi.cancel(detail.value.id, value || '')
    ElMessage.success('领用单已取消')
    loadDetail()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') throw e
  }
}

function openReturn(row) {
  returnItemRow.value = row
  returnForm.qty = 1
  returnForm.reason = ''
  returnDialogVisible.value = true
}

async function submitReturn() {
  if (!returnItemRow.value) return
  if (!returnForm.reason.trim()) {
    ElMessage.warning('请填写退库原因')
    return
  }
  if (returnForm.qty < 1 || returnForm.qty > returnItemRow.value.returnableQty) {
    ElMessage.warning(`退库数量不能超过可退数量 ${returnItemRow.value.returnableQty}`)
    return
  }
  returnSubmitting.value = true
  try {
    const result = await outboundApi.returnItem(detail.value.id, {
      itemId: returnItemRow.value.id,
      qty: returnForm.qty,
      reason: returnForm.reason.trim()
    })
    ElMessage.success(`退库申请已提交审批：${result.returnNo}`)
    returnDialogVisible.value = false
    await loadDetail()
  } finally {
    returnSubmitting.value = false
  }
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}
function formatTime(isoStr) {
  if (!isoStr) return ''
  const d = new Date(isoStr)
  if (isNaN(d.getTime())) return ''
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

onMounted(loadDetail)
</script>

<style scoped lang="scss">
// ---------- 详情页通用（入库/出库单详情统一风格） ----------
.m-page {
  background: transparent;
  padding: 0 0 12px;
  box-shadow: none;
  border: 0;
  border-radius: 0;
  min-height: auto;
}
.card-d {
  margin: 12px 12px 0;
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
}
.card-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 10px;
}
.detail-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.order-no {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  word-break: break-all;
}
.m-tag {
  flex-shrink: 0;
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
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.info-item .label-muted {
  color: #94a3b8;
  font-size: 12px;
}
.info-item .value {
  margin-top: 3px;
  font-size: 13px;
  color: #334155;
  word-break: break-all;
}
// 货品明细（移动端行卡）
.d-items { display: flex; flex-direction: column; }
.d-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child { border-bottom: 0; }
}
.d-item-main { min-width: 0; }
.d-item-name { font-size: 14px; color: #1e293b; b { margin-right: 6px; } }
.d-item-sub { font-size: 12px; color: #94a3b8; margin-top: 3px; }
.d-item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}
.d-qty { font-size: 12px; color: #475569; }
// 审批/流转行
.a-rows { display: flex; flex-direction: column; }
.a-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child { border-bottom: 0; }
}
.a-main { min-width: 0; flex: 1; }
.a-node { font-size: 14px; color: #1e293b; }
.a-round {
  margin-left: 8px;
  font-size: 11px;
  color: #94a3b8;
}
.a-sub { font-size: 12px; color: #94a3b8; margin-top: 3px; }
.a-opinion {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  background: #f8fafc;
  border-radius: 8px;
  padding: 6px 8px;
}
// 底部操作
.footer-actions {
  margin: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  .el-button { margin: 0; min-height: 42px; }
}
.m-primary-btn {
  border: 0;
  color: #fff;
  background: linear-gradient(135deg, #2e6ef2, #1e50d8);
  &:active { opacity: .9; }
}
.return-summary { margin-bottom: 18px; }

// ---------- 桌面端 ----------
@media(min-width:768px) {
  .info-grid { grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
  .footer-actions { flex-direction: row; justify-content: flex-start; }
  .m-page { background: rgba(255,255,255,.94); }
}
</style>
