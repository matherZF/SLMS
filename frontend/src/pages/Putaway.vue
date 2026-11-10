<template>
  <div class="page-container putaway-page">
    <!-- Step 1：选单 -->
    <template v-if="step === 1">
      <PageHeader>选择待入库单</PageHeader>

      <div v-loading="pendingLoading">
        <el-empty
          v-if="!pendingLoading && pendingList.length === 0"
          description="暂无待入库单"
        />

        <div v-for="order in pendingList" :key="order.id" @click="selectOrder(order)">
          <el-card class="order-card" shadow="hover">
            <div class="order-card-header">
              <span class="order-no">{{ order.orderNo }}</span>
              <el-tag size="small" type="info" effect="light">待上架</el-tag>
            </div>
            <div class="order-card-product">
              <b>{{ order.sku }}</b>&nbsp;-&nbsp;{{ order.productName }}
            </div>
            <div class="order-card-qty">
              剩余 <b class="remain-num">{{ order.planQty - order.putQty }}</b> 件
              &nbsp;/&nbsp;计划 {{ order.planQty }} 件
            </div>
            <el-progress
              style="margin-top: 8px"
              :percentage="order.progress || 0"
              :stroke-width="6"
            />
          </el-card>
        </div>
      </div>
    </template>

    <!-- Step 2：货品确认 -->
    <template v-else-if="step === 2 && currentOrder">
      <PageHeader>
        <template #desc>上架作业 · 第 2 / 3 步</template>
        货品确认
      </PageHeader>

      <el-card class="info-card">
        <div class="info-line">
          <span class="lbl">SKU：</span>
          <b class="highlight">{{ currentOrder.sku }}</b>
        </div>
        <div class="info-line">
          <span class="lbl">名称：</span>
          <span>{{ currentOrder.productName }}</span>
        </div>
        <div class="info-line">
          <span class="lbl">计划：</span>
          <span>{{ currentOrder.planQty }} 件</span>
        </div>
        <div class="info-line">
          <span class="lbl">已上架：</span>
          <span style="color: #67C23A">{{ currentOrder.putQty }} 件</span>
        </div>
        <div class="info-line">
          <span class="lbl">剩余：</span>
          <el-tag type="warning" size="large" effect="dark">{{ remainingQty }} 件</el-tag>
        </div>
      </el-card>

      <el-form :model="{}" label-position="top" style="margin-top: 16px">
        <el-form-item label="SKU 核对（可选，扫码或输入后自动校验）">
          <el-input
            v-model="checkSku"
            placeholder="请扫描或输入 SKU 进行核对"
            clearable
            size="large"
            @input="onCheckSku"
          >
            <template #prefix>
              <el-icon><Scan /></el-icon>
            </template>
          </el-input>
          <p v-if="skuErrorState === 'mismatch'" class="sku-hint danger">
            <el-icon><CircleClose /></el-icon>
            &nbsp;SKU 与单据不匹配，请检查
          </p>
          <p v-if="skuErrorState === 'match'" class="sku-hint success">
            <el-icon><CircleCheck /></el-icon>
            &nbsp;核对通过
          </p>
        </el-form-item>
      </el-form>

      <div style="margin-top: 8px">
        <el-button @click="backToStep1" plain>
          <el-icon><ArrowLeft /></el-icon>&nbsp;返回选单
        </el-button>
      </div>
      <div style="margin-top: 14px">
        <el-button
          type="primary"
          class="primary-mobile-btn"
          :disabled="skuErrorState === 'mismatch'"
          @click="step = 3"
        >
          下一步，选择库位 <el-icon style="margin-left:4px"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </template>

    <!-- Step 3：库位 + 数量 + 确认 -->
    <template v-else-if="step === 3 && currentOrder">
      <PageHeader>
        <template #desc>上架作业 · 第 3 / 3 步</template>
        确认上架
      </PageHeader>

      <el-card class="info-card compact">
        <div class="mini-line">
          <b>{{ currentOrder.sku }}</b>&nbsp;-&nbsp;{{ currentOrder.productName }}
        </div>
        <div class="mini-line muted">
          单号：{{ currentOrder.orderNo }}
        </div>
        <div class="mini-line">
          本单还剩 <b class="warn-text">{{ remainingQty }}</b> 件待上架
        </div>
      </el-card>

      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-position="top"
        style="margin-top: 16px"
        @submit.prevent
      >
        <el-form-item label="选择库位" prop="locationId">
          <el-select
            v-model="form.locationId"
            filterable
            placeholder="搜索或选择库位"
            style="width: 100%"
            size="large"
            :loading="locationsLoading"
          >
            <el-option
              v-for="l in locations"
              :key="l.id"
              :label="`${l.code} - ${l.area || ''}（${l.type || '其他'}）`"
              :value="l.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="实际上架数量" prop="qty">
          <el-input-number
            v-model="form.qty"
            :min="1"
            :max="remainingQty"
            size="large"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <p class="tip-line">
          提示：本单剩余 <b>{{ remainingQty }}</b> 件，本次最多上架 {{ remainingQty }} 件。
        </p>
      </el-form>

      <div style="margin-top: 8px">
        <el-button @click="step = 2" plain>
          <el-icon><ArrowLeft /></el-icon>&nbsp;上一步
        </el-button>
      </div>
      <div style="margin-top: 14px">
        <el-button
          type="primary"
          class="primary-mobile-btn"
          :loading="submitting"
          @click="submitConfirm"
        >
          <el-icon><Check /></el-icon>&nbsp;确认上架 {{ form.qty || 0 }} 件
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as ordersApi from '@/api/orders'
import * as locationsApi from '@/api/locations'
import * as putawayApi from '@/api/putaway'

const route = useRoute()
const router = useRouter()

// ========= Step 状态 =========
const step = ref(1)

// ========= 单据 =========
const currentOrder = ref(null)
const pendingList = ref([])
const pendingLoading = ref(false)

// ========= 剩余数（响应式） =========
const remainingQty = computed(() => {
  if (!currentOrder.value) return 0
  return Math.max(
    0,
    (currentOrder.value.planQty || 0) - (currentOrder.value.putQty || 0)
  )
})

// ========= Step2 SKU 核对 =========
const checkSku = ref('')
const skuErrorState = ref('') // '' | 'mismatch' | 'match'

function onCheckSku(val) {
  const v = (val || '').trim()
  if (!v) {
    skuErrorState.value = ''
    return
  }
  if (!currentOrder.value) return
  if (v.toUpperCase() === currentOrder.value.sku.toUpperCase()) {
    skuErrorState.value = 'match'
  } else {
    skuErrorState.value = 'mismatch'
  }
}

// ========= Step3 库位 & 数量 =========
const locations = ref([])
const locationsLoading = ref(false)
const formRef = ref(null)
const submitting = ref(false)

// 上架确认幂等键：同一次"点击提交"在失败重试时沿用同一个值，
// 服务端据此识别重放，避免网络超时后用户再点一次导致同一批货入库两遍。
// 只有提交成功后才作废，下一次点击才是新的一次上架。
let pendingRequestId = ''
function newRequestId() {
  return `PW-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
const form = reactive({ locationId: null, qty: 1 })
const formRules = {
  locationId: [{ required: true, message: '请选择库位', trigger: 'change' }],
  qty: [{ required: true, message: '请输入上架数量', trigger: 'blur' }]
}

async function loadLocations() {
  if (locations.value.length > 0) return
  locationsLoading.value = true
  try {
    // 一次取全量库位（通常 < 500）
    const r = await locationsApi.list({ page: 0, size: 500 })
    locations.value = r.content || []
  } finally {
    locationsLoading.value = false
  }
}

// ========= Step 1 选单 =========
async function loadPending() {
  pendingLoading.value = true
  try {
    pendingList.value = await ordersApi.pending()
  } finally {
    pendingLoading.value = false
  }
}

function selectOrder(order) {
  if (!order) return
  currentOrder.value = order
  // 清空 step2/3 状态
  checkSku.value = ''
  skuErrorState.value = ''
  form.locationId = null
  form.qty = Math.min(remainingQty.value, 1) || 1
  step.value = 2
}

function backToStep1() {
  currentOrder.value = null
  step.value = 1
  loadPending()
}

// ========= 提交上架确认 =========
async function submitConfirm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (remainingQty.value <= 0) {
    ElMessage.warning('本单已无可上架数量')
    return
  }
  if (form.qty > remainingQty.value) {
    ElMessage.warning(`超过剩余数量，最多上架 ${remainingQty.value} 件`)
    return
  }

  submitting.value = true
  try {
    if (!pendingRequestId) pendingRequestId = newRequestId()
    const res = await putawayApi.confirm({
      orderId: currentOrder.value.id,
      locationId: form.locationId,
      qty: form.qty,
      requestId: pendingRequestId
    })
    // 本次上架已落库，幂等键作废：用户再点提交就是新的一次上架
    pendingRequestId = ''
    const qtyDone = form.qty
    ElMessage.success(res.idempotent ? '该次上架此前已提交成功' : `上架成功 ${qtyDone} 件`)

    // 刷新当前单据（重新调用详情，获取最新 putQty、status、remaining）
    const fresh = await ordersApi.detail(currentOrder.value.id)
    currentOrder.value = fresh

    const isDone = !!res.done || fresh.status === 'DONE'
    const remain = Math.max(0, (fresh.planQty || 0) - (fresh.putQty || 0))

    // 弹结果框
    if (isDone) {
      await ElMessageBox({
        title: '操作结果',
        message: '全部上架完成，单据已结束',
        confirmButtonText: '返回选单',
        showCancelButton: false,
        type: 'success'
      })
      // 返回选单
      currentOrder.value = null
      checkSku.value = ''
      skuErrorState.value = ''
      form.locationId = null
      form.qty = 1
      step.value = 1
      loadPending()
    } else {
      try {
        await ElMessageBox({
          title: '操作结果',
          message: `本次上架 ${qtyDone} 件，剩余 ${remain} 件`,
          confirmButtonText: '返回选单',
          cancelButtonText: '继续上架本单',
          showCancelButton: true,
          distinguishCancelAndClose: true,
          type: 'info'
        })
        // confirm → 返回选单
        currentOrder.value = null
        checkSku.value = ''
        skuErrorState.value = ''
        form.locationId = null
        form.qty = 1
        step.value = 1
        loadPending()
      } catch {
        // cancel → 继续上架本单（保持 step3，重置数量为剩余）
        checkSku.value = ''
        skuErrorState.value = ''
        form.qty = remain > 0 ? remain : 1
      }
    }
  } catch (e) {
    // 保留 pendingRequestId：用户重新点击时沿用同一个幂等键，
    // 若上一次其实已经落库，服务端会直接返回上次结果而不是再入一次库
  } finally {
    submitting.value = false
  }
}

// ========= 路由 query 带 orderId 的场景：直接跳到 step2 =========
onMounted(async () => {
  const orderId = route.query.orderId
  if (orderId) {
    // 先尝试详情
    try {
      const detail = await ordersApi.detail(orderId)
      if (detail.status === 'DONE') {
        ElMessage.info('该入库单已完成，无需继续上架')
        loadPending()
      } else {
        selectOrder(detail)
      }
    } catch (e) {
      // 详情获取失败（已被拦截器 toast），回到选单
      loadPending()
    }
  } else {
    loadPending()
  }
  // 预加载库位列表
  loadLocations()
})

// 如果路由 query 变化（如在 putaway 页再次点链接进带 orderId 的）
watch(
  () => route.query.orderId,
  (newId) => {
    if (newId && step.value === 1) {
      ordersApi.detail(newId).then((d) => {
        if (d.status !== 'DONE') selectOrder(d)
      }).catch(() => {})
    }
  }
)
</script>

<style scoped lang="scss">
.putaway-page {
  max-width: 640px;
  margin: 0 auto;
}
.order-card {
  margin-bottom: 12px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  &:hover {
    transform: translateY(-1px);
  }
}
.order-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.order-no {
  font-weight: 700;
  font-size: 15px;
  color: #303133;
  letter-spacing: 0.3px;
}
.order-card-product {
  color: #303133;
  margin-bottom: 4px;
}
.order-card-qty {
  color: #606266;
  font-size: 13px;
  .remain-num { color: #E6A23C; font-size: 15px; }
}
.info-card {
  border-radius: 10px;
  .info-line {
    padding: 6px 0;
    font-size: 14px;
    .lbl { color: #909399; display: inline-block; min-width: 72px; }
    .highlight { color: $primary; font-size: 15px; }
  }
  &.compact {
    .mini-line { padding: 3px 0; font-size: 13px; &.muted { color: #909399; } }
    .warn-text { color: #E6A23C; }
  }
}
.sku-hint {
  margin: 8px 0 0;
  font-size: 13px;
  display: flex;
  align-items: center;
  &.danger { color: $danger; }
  &.success { color: $success; }
}
.tip-line {
  margin: 4px 0 0;
  padding: 10px 12px;
  background: #ecf5ff;
  color: #409EFF;
  border-radius: 6px;
  font-size: 13px;
}
</style>
