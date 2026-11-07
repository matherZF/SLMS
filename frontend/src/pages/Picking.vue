<template>
  <div class="page-container picking-page" :class="{ 'm-page': isMobile }">
    <!-- 模式一：待拣货单列表 -->
    <template v-if="isListMode">
      <PageHeader v-if="!isMobile">选择待拣货领用单</PageHeader>

      <div v-loading="pendingLoading" class="m-card-list">
        <el-empty
          v-if="!pendingLoading && pendingList.length === 0"
          description="暂无待拣货领用单"
        />

        <article
          v-for="order in pendingList"
          :key="order.id"
          class="m-card"
          @click="selectOrder(order)"
        >
          <div class="m-card-head">
            <b class="m-no">{{ order.orderNo }}</b>
            <span class="m-tag" :class="order.status === 'PICKING' ? 't-orange' : 't-amber'">
              {{ order.status === 'PICKING' ? '拣货中' : '待拣货' }}
            </span>
          </div>
          <div v-if="order.department || order.recipient" class="m-line">
            <span class="m-label">部门/领用人</span><span class="m-val">{{ order.department || '-' }} / {{ order.recipient || '-' }}</span>
          </div>
          <div v-if="order.purpose" class="m-line">
            <span class="m-label">用途</span><span class="m-val">{{ order.purpose }}</span>
          </div>
          <div class="m-line">
            <span class="m-label">货品/时间</span><span class="m-val">{{ order.itemCount || 0 }} 项 · {{ formatTime(order.createdAt) }}</span>
          </div>
        </article>
      </div>
    </template>

    <!-- 模式二：拣货作业 -->
    <template v-else>
      <el-page-header
        v-if="!isMobile"
        :content="null"
        @back="backToList"
        style="margin-bottom: 12px"
      >
        <template #content>
          <span style="font-size: 18px; font-weight: 600">拣货作业</span>
        </template>
      </el-page-header>

      <div v-loading="loading">
        <template v-if="detail">
          <!-- 单据基本信息 -->
          <el-card class="info-card">
            <div class="info-top">
              <span class="order-no">{{ detail.orderNo }}</span>
              <el-tag :type="statusTagType(detail.status)" effect="light" size="large">
                {{ statusText(detail.status) }}
              </el-tag>
            </div>
            <div class="info-line">
              <span class="lbl">货品项数：</span>
              <b>{{ detail.items.length }}</b>
            </div>
          </el-card>

          <!-- 状态：待拣货 -->
          <template v-if="detail.status === 'APPROVED'">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              style="margin-top: 16px"
            >
              <template #title>
                可点击「开始拣货」由系统按 FIFO 自动分配库位，也可逐项「选择库位」人工指定批次。
              </template>
            </el-alert>

            <!-- 货品清单（未分配库位） -->
            <el-card style="margin-top: 16px">
              <template #header>
                <span style="font-weight: 600">货品清单</span>
              </template>
              <el-table :data="detail.items" stripe border style="width: 100%">
                <el-table-column label="货品" min-width="180">
                  <template #default="{ row }">
                    <b>{{ row.sku }}</b>
                    <span style="margin-left:6px">{{ row.name }}</span>
                    <div v-if="row.spec" style="font-size:12px;color:#909399">{{ row.spec }} / {{ row.unit || '件' }}</div>
                  </template>
                </el-table-column>
                <el-table-column prop="requestedQty" label="需求数量" width="110" align="center" />
                <el-table-column label="已选库位/批次" min-width="200">
                  <template #default="{ row }">
                    <template v-if="row.locationCode">
                      <div><b>{{ row.locationCode }}</b></div>
                      <div style="font-size:12px;color:#909399" v-if="row.itemKey">
                        批次：{{ row.itemKey }}
                      </div>
                    </template>
                    <span v-else style="color:#c0c4cc">未分配</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center">
                  <template #default="{ row }">
                    <el-button type="primary" size="small" link @click="openSelectDialog(row)">
                      选择库位
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <div style="margin-top: 20px">
              <el-button
                type="primary"
                size="large"
                class="action-btn"
                :loading="starting"
                @click="handleStart"
              >
                <el-icon><VideoPlay /></el-icon>&nbsp;开始拣货
              </el-button>
            </div>
          </template>

          <!-- 状态：拣货中 -->
          <template v-else-if="detail.status === 'PICKING'">
            <el-alert
              type="warning"
              :closable="false"
              show-icon
              style="margin-top: 16px"
            >
              <template #title>
                正在拣货中。按照库位指引取货后，需在对应电子标签上按下右下按键确认。页面每 3 秒自动同步按键状态。
              </template>
            </el-alert>

            <!-- 库位指引卡片 -->
            <div style="margin-top: 16px">
              <div
                v-for="(item, idx) in detail.items"
                :key="item.id"
                class="pick-card"
              >
                <div class="pick-card-header">
                  <div class="pick-index">{{ idx + 1 }}</div>
                  <div class="pick-product">
                    <b>{{ item.sku }}</b>
                    <span style="margin-left:6px">{{ item.name }}</span>
                    <div v-if="item.spec" style="font-size:12px;color:#909399">{{ item.spec }} / {{ item.unit || '件' }}</div>
                  </div>
                  <el-tag
                    class="pick-button-tag"
                    :type="item.buttonPressed ? 'success' : 'danger'"
                    effect="light"
                    size="small"
                  >
                    {{ item.buttonPressed ? '✓ 按键已确认' : '⏳ 待按键确认' }}
                  </el-tag>
                  <el-tag
                    v-if="item.lightConfirmed != null"
                    effect="light"
                    size="small"
                    :type="item.lightConfirmed === 1 ? 'primary' : 'warning'"
                    style="margin-left:4px"
                    :title="item.lightMessage || ''"
                  >
                    {{ item.lightConfirmed === 1 ? '💡 亮灯已确认' : ('⚠️ 亮灯失败' + (item.lightMessage ? '：' + item.lightMessage : '')) }}
                  </el-tag>
                </div>
                <div class="pick-card-body">
                  <div class="pick-location">
                    <el-icon :size="20" color="#E6A23C"><Location /></el-icon>
                    <span class="loc-code">{{ item.locationCode }}</span>
                    <span v-if="item.locationArea" class="loc-area">{{ item.locationArea }}区</span>
                  </div>
                  <div class="pick-qty">
                    取货数量：<b class="qty-num">{{ item.requestedQty }}</b> 件
                  </div>
                  <!-- 批次信息 -->
                  <div class="pick-batch" v-if="item.itemKey">
                    <el-icon><Box /></el-icon>
                    <span class="batch-label">批次：</span>
                    <b>{{ item.itemKey }}</b>
                    <span v-if="item.productionDate" class="batch-meta">
                      生产 {{ item.productionDate }}
                    </span>
                    <span v-if="item.expiryDate" class="batch-meta">
                      到期 {{ item.expiryDate }}
                    </span>
                    <span v-if="item.manufacturer" class="batch-meta">
                      {{ item.manufacturer }}
                    </span>
                  </div>
                  <div class="pick-device" v-if="item.deviceNo">
                    <el-icon><MagicStick /></el-icon>
                    <span>灯光设备：{{ item.deviceNo }}（已点亮）</span>
                  </div>
                  <div class="pick-device" v-else>
                    <el-icon><InfoFilled /></el-icon>
                    <span style="color:#909399">该库位未绑定灯光设备</span>
                  </div>
                  <div style="margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap">
                    <el-button size="small" type="primary" link @click="openSelectDialog(item)">
                      更换库位
                    </el-button>
                    <el-button
                      size="small"
                      :type="item.scanVerified ? 'success' : 'default'"
                      @click="openScanDialog(item)"
                    >
                      {{ item.scanVerified ? '已扫码核验' : '扫码核验（推荐）' }}
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <div style="margin-top: 20px">
              <el-button
                type="success"
                size="large"
                class="action-btn"
                :loading="completing"
                @click="handleComplete"
              >
                <el-icon><CircleCheck /></el-icon>&nbsp;确认出库
              </el-button>
              <div v-if="!allItemsButtonPressed" class="verify-tip">
                （提示）还有 {{ detail.items.filter(i => !i.buttonPressed).length }} 项尚未收到电子标签按键回调，后端校验时会拒绝
              </div>
              <div v-else class="verify-tip" style="color:#67C23A">
                ✓ 全部标签已按键确认
              </div>
            </div>
          </template>

          <!-- 状态：已完成 -->
          <template v-else-if="detail.status === 'DONE'">
            <el-result icon="success" title="拣货完成" sub-title="该领用单已全部出库" style="padding: 30px 0">
              <template #extra>
                <el-button type="primary" @click="backToList">返回领用单列表</el-button>
              </template>
            </el-result>
          </template>
        </template>
      </div>
    </template>

    <QrVerificationDialog
      v-model="scanDialogVisible"
      :item="currentScanItem"
      :loading="scanVerifying"
      @confirm="handleScanConfirm"
    />

    <!-- 人工选择库位 Dialog -->
    <el-dialog
      v-model="selectDialogVisible"
      title="选择库位 / 批次"
      :width="dialogWidth"
      destroy-on-close
      @closed="onSelectClosed"
    >
      <div v-loading="stockLoading">
        <el-alert
          v-if="currentSelectItem"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        >
          <template #title>
            货品：<b>{{ currentSelectItem.sku }} - {{ currentSelectItem.name }}</b>
            ，需求数量 <b>{{ currentSelectItem.requestedQty }}</b> 件
          </template>
        </el-alert>

        <el-empty
          v-if="!stockLoading && availableStock.length === 0"
          description="该货品暂无可用库存"
        />

        <el-table
          v-else
          :data="availableStock"
          stripe
          border
          style="width: 100%"
          highlight-current-row
          @current-change="onStockSelect"
        >
          <el-table-column label="库位" min-width="110">
            <template #default="{ row }">
              <b>{{ row.locationCode }}</b>
              <div style="font-size:11px;color:#909399">{{ row.locationArea || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="可用数量" width="90" align="center">
            <template #default="{ row }">
              <b :class="{ 'stock-low': row.availableQty < (currentSelectItem?.requestedQty || 0) }">
                {{ row.availableQty }}
              </b>
            </template>
          </el-table-column>
          <el-table-column label="批次号" min-width="130">
            <template #default="{ row }">
              <b>{{ row.itemKey || '-' }}</b>
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
          <el-table-column label="保质期(天)" width="90" align="center">
            <template #default="{ row }">{{ row.shelfLifeDays || '-' }}</template>
          </el-table-column>
          <el-table-column label="生产厂商" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.manufacturer || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="selectDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!selectedStock"
          :loading="assigning"
          @click="confirmAssign"
        >
          确认选择
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import QrVerificationDialog from '@/components/QrVerificationDialog.vue'
import * as outboundApi from '@/api/outbound'
import { useMobile } from '@/composables/useMobile'

const route = useRoute()
const router = useRouter()
const { isMobile } = useMobile()
const dialogWidth = computed(() => (isMobile.value ? '92%' : '720px'))

const orderId = computed(() => route.params.id)
const isListMode = computed(() => !orderId.value || orderId.value === 'list')

// 列表模式
const pendingList = ref([])
const pendingLoading = ref(false)

// 作业模式
const detail = ref(null)
const loading = ref(false)
const starting = ref(false)
const completing = ref(false)
const scanDialogVisible = ref(false)
const currentScanItem = ref(null)
const scanVerifying = ref(false)

// 必要条件：所有出库项的电子标签按键已确认
const allItemsButtonPressed = computed(() =>
  detail.value?.items?.length > 0 && detail.value.items.every((item) => item.buttonPressed)
)

// 人工选库位
const selectDialogVisible = ref(false)
const currentSelectItem = ref(null)
const availableStock = ref([])
const stockLoading = ref(false)
const selectedStock = ref(null)
const assigning = ref(false)

// 轮询定时器：PICKING 状态下每 3 秒刷新一次，以便看到按键回调到达
let pollTimer = null
function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (detail.value?.status === 'PICKING') {
      loadDetailSilently()
    }
  }, 3000)
}
function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function loadPending() {
  pendingLoading.value = true
  try {
    pendingList.value = await outboundApi.pending()
  } finally {
    pendingLoading.value = false
  }
}

function selectOrder(order) {
  if (order && order.id != null) {
    router.push(`/outbound/picking/${order.id}`)
  }
}

function backToList() {
  router.push('/outbound')
}

async function loadDetail() {
  if (isListMode.value) return
  loading.value = true
  try {
    detail.value = await outboundApi.detail(orderId.value)
    if (detail.value?.status === 'PICKING') startPolling()
    else stopPolling()
  } finally {
    loading.value = false
  }
}

/** 静默刷新（轮询用），不展示 loading 遮罩，只更新 detail */
async function loadDetailSilently() {
  try {
    detail.value = await outboundApi.detail(orderId.value)
    if (detail.value?.status !== 'PICKING') stopPolling()
  } catch {
    // 轮询失败不打断作业
  }
}

async function handleStart() {
  starting.value = true
  try {
    const res = await outboundApi.startPicking(orderId.value)
    ElMessage.success('已开始拣货，库位灯光已点亮')
    if (res && res.items) {
      detail.value = {
        ...detail.value,
        status: 'PICKING',
        items: res.items.map((it) => ({
          id: it.itemId,
          productId: it.productId,
          sku: it.sku,
          name: it.name,
          spec: it.spec,
          unit: it.unit,
          requestedQty: it.requestedQty,
          pickedQty: 0,
          locationId: it.locationId,
          locationCode: it.locationCode,
          locationArea: it.locationArea,
          deviceNo: it.deviceNo,
          availableQty: it.availableQty,
          batchId: it.batchId,
          itemKey: it.itemKey,
          productionDate: it.productionDate,
          shelfLifeDays: it.shelfLifeDays,
          manufacturer: it.manufacturer,
          expiryDate: it.expiryDate,
          scanVerified: Boolean(it.scanVerified),
          buttonPressed: Boolean(it.buttonPressed),
          buttonPressedAt: it.buttonPressedAt || null,
          lightConfirmed: it.lightConfirmed ?? null,   // null=未收到回调, 1=result=true, 2=result=false
          lightConfirmedAt: it.lightConfirmedAt || null,
          lightMessage: it.lightMessage || null
        }))
      }
      startPolling()
    } else {
      await loadDetail()
    }
  } finally {
    starting.value = false
  }
}

function openScanDialog(item) {
  currentScanItem.value = item
  scanDialogVisible.value = true
}

async function handleScanConfirm(payload) {
  scanVerifying.value = true
  try {
    await outboundApi.verifyScan(orderId.value, payload)
    detail.value = {
      ...detail.value,
      items: detail.value.items.map((item) =>
        item.id === payload.itemId ? { ...item, scanVerified: true } : item
      )
    }
    scanDialogVisible.value = false
    ElMessage.success('备件与货位二维码核验通过（建议完成；电子标签按键仍为必要条件）')
  } finally {
    scanVerifying.value = false
  }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm(
      '确认所有标签右下按键已按下、所有货品已按数量拣取完成？完成后将扣减库存。',
      '拣货完成确认',
      {
        type: 'warning',
        confirmButtonText: '确认完成',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  completing.value = true
  try {
    const res = await outboundApi.completePicking(orderId.value)
    ElMessage.success(res?.msg || '拣货完成，出库成功')
    detail.value = { ...detail.value, status: 'DONE' }
    stopPolling()
  } finally {
    completing.value = false
  }
}

// ===== 人工选库位 =====
async function openSelectDialog(item) {
  currentSelectItem.value = item
  selectedStock.value = null
  selectDialogVisible.value = true
  stockLoading.value = true
  try {
    availableStock.value = await outboundApi.availableStock(item.productId)
  } finally {
    stockLoading.value = false
  }
}

function onStockSelect(row) {
  selectedStock.value = row
}

function onSelectClosed() {
  currentSelectItem.value = null
  availableStock.value = []
  selectedStock.value = null
}

async function confirmAssign() {
  if (!selectedStock.value || !currentSelectItem.value) return
  const stock = selectedStock.value
  if (stock.availableQty < currentSelectItem.value.requestedQty) {
    ElMessage.warning(`所选可用库存不足（仅剩 ${stock.availableQty} 件）`)
    return
  }
  assigning.value = true
  try {
    const updated = await outboundApi.assignLocation(
      orderId.value,
      currentSelectItem.value.id,
      stock.locationId,
      stock.batchId
    )
    const items = detail.value.items.map((it) =>
      it.id === updated.id ? { ...it, ...updated } : it
    )
    detail.value = { ...detail.value, items }
    ElMessage.success(`已分配库位 ${stock.locationCode}（批次 ${stock.itemKey || '无'}）`)
    selectDialogVisible.value = false
  } finally {
    assigning.value = false
  }
}

function isExpiringSoon(expiryDate) {
  if (!expiryDate) return false
  const days = Math.ceil((new Date(expiryDate) - new Date()) / 86400000)
  return days <= 30
}

function statusText(s) {
  if (s === 'PENDING_APPROVAL') return '待审批'
  if (s === 'APPROVED') return '待拣货'
  if (s === 'PICKING') return '拣货中'
  if (s === 'DONE') return '已完成'
  return s
}

function statusTagType(s) {
  if (s === 'PENDING_APPROVAL') return 'warning'
  if (s === 'APPROVED') return 'info'
  if (s === 'PICKING') return 'warning'
  if (s === 'DONE') return 'success'
  return ''
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
  if (isListMode.value) {
    loadPending()
  } else {
    loadDetail()
  }
})

onBeforeUnmount(() => {
  stopPolling()
})

watch(
  () => route.params.id,
  (newId) => {
    stopPolling()
    detail.value = null
    if (!newId || newId === 'list') {
      loadPending()
    } else {
      loadDetail()
    }
  }
)
</script>

<style scoped lang="scss">
// ---------- 移动端（App 外壳，与入库/出库列表同款） ----------
.m-page {
  background: transparent;
  padding: 0 0 12px;
  box-shadow: none;
  border: 0;
  border-radius: 0;
  min-height: auto;
  max-width: none;
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
  flex-shrink: 0;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-orange { background: #fef0e6; color: #f5722c; }
  &.t-blue { background: #eaf1ff; color: #2e6cf6; }
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

// ---------- 作业模式 ----------
.picking-page {
  max-width: 720px;
  margin: 0 auto;
}
.order-no {
  font-weight: 700;
  font-size: 15px;
  color: #303133;
  letter-spacing: 0.3px;
}
.info-card {
  border-radius: 10px;
  .info-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }
  .info-line {
    font-size: 14px;
    .lbl { color: #909399; }
  }
}
.action-btn {
  width: 100%;
  min-height: 48px;
  font-size: 16px;
}
.pick-card {
  border: 1px solid #ebeef5;
  border-left: 4px solid $primary;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
  background: #fff;
}
.pick-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.pick-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: $primary;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}
.pick-product {
  flex: 1;
  font-size: 14px;
  color: #303133;
}
.pick-button-tag {
  margin-left: auto;
  flex-shrink: 0;
}
.pick-card-body {
  padding-left: 36px;
}
.pick-location {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  .loc-code {
    font-size: 18px;
    font-weight: 700;
    color: #E6A23C;
    letter-spacing: 0.5px;
  }
  .loc-area {
    font-size: 13px;
    color: #909399;
    margin-left: 4px;
  }
}
.pick-qty {
  font-size: 14px;
  color: #606266;
  margin-bottom: 6px;
  .qty-num {
    color: $danger;
    font-size: 16px;
  }
}
.pick-batch {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
  background: #f5f7fa;
  padding: 6px 10px;
  border-radius: 6px;
  .batch-label { color: #909399; }
  .batch-meta { color: #909399; font-size: 12px; }
}
.pick-device {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #67C23A;
}
.stock-low {
  color: #F56C6C;
}
.expire-soon {
  color: #E6A23C;
  font-weight: 600;
}
.verify-tip {
  text-align: center;
  margin-top: 8px;
  font-size: 13px;
  color: #E6A23C;
}
</style>
