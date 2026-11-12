<template>
  <div :class="[embedded ? 'detail-embedded' : 'page-container', { 'm-page': isMobile }]">
    <!-- 返回头部：桌面端显示，移动端由 AppLayout 顶栏返回箭头承担；抽屉内不再显示 -->
    <el-page-header
      v-if="!isMobile && !embedded"
      :content="null"
      @back="$router.back()"
      style="margin-bottom: 12px"
    >
      <template #content>
        <span style="font-size: 18px; font-weight: 600">入库单详情</span>
      </template>
    </el-page-header>

    <!-- 详情卡片 -->
    <section v-loading="loading" class="card-d">
      <template v-if="detail">
        <div class="detail-top">
          <span class="order-no">{{ detail.orderNo }}</span>
          <span class="m-tag" :class="detail.status === 'DONE' ? 't-green' : 't-amber'">
            {{ detail.status === 'DONE' ? '已完成' : '待上架' }}
          </span>
        </div>

        <div class="info-grid">
          <div class="info-item"><div class="label-muted">SKU编码</div><div class="value"><b>{{ detail.sku }}</b></div></div>
          <div class="info-item"><div class="label-muted">货品名称</div><div class="value">{{ detail.productName }}</div></div>
          <div class="info-item"><div class="label-muted">计划数量</div><div class="value">{{ detail.planQty }}</div></div>
          <div class="info-item"><div class="label-muted">已上架</div><div class="value" style="color: #12b76a"><b>{{ detail.putQty }}</b></div></div>
          <div class="info-item"><div class="label-muted">剩余</div><div class="value" style="color: #f5722c"><b>{{ remainingQty }}</b></div></div>
          <div class="info-item"><div class="label-muted">创建时间</div><div class="value">{{ formatTime(detail.createdAt) }}</div></div>
        </div>

        <div class="batch-block" v-if="detail.itemKey || detail.productionDate || detail.manufacturer">
          <div class="label-muted" style="margin-bottom: 8px">批次信息</div>
          <div class="info-grid">
            <div class="info-item"><div class="label-muted">批次号</div><div class="value"><b>{{ detail.itemKey || '-' }}</b></div></div>
            <div class="info-item"><div class="label-muted">生产日期</div><div class="value">{{ detail.productionDate || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">保质期(天)</div><div class="value">{{ detail.shelfLifeDays || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">到期日</div><div class="value">{{ detail.expiryDate || '-' }}</div></div>
            <div class="info-item"><div class="label-muted">生产厂商</div><div class="value">{{ detail.manufacturer || '-' }}</div></div>
          </div>
        </div>

        <div style="margin-top: 14px">
          <div class="label-muted" style="margin-bottom: 8px">上架进度</div>
          <el-progress
            :percentage="detail.progress || 0"
            :color="detail.status === 'DONE' ? '#12b76a' : '#2e6ef2'"
          />
        </div>
      </template>
    </section>

    <!-- 已上架明细 -->
    <section class="card-d">
      <div class="card-title">已上架明细（{{ detail?.items?.length || 0 }} 条）</div>
      <div v-if="isMobile">
        <div v-for="(row, i) in detail?.items || []" :key="i" class="d-item">
          <div class="d-item-main">
            <div class="d-item-name"><b>{{ row.locationCode }}</b> <span class="d-item-sub">{{ row.area || '' }}</span></div>
            <div class="d-item-sub">{{ formatTime(row.createdAt) }}</div>
          </div>
          <div class="d-item-right">
            <b class="d-qty" style="font-size: 15px">{{ row.qty }}</b>
            <span class="d-item-sub">上架数量</span>
          </div>
        </div>
        <el-empty
          v-if="!(detail?.items || []).length"
          :description="detail?.status === 'PENDING' ? '暂未上架任何货品' : '无数据'"
          :image-size="60"
        />
      </div>
      <el-table
        v-else
        :data="detail?.items || []"
        stripe
        border
        :empty-text="detail?.status === 'PENDING' ? '暂未上架任何货品' : '无数据'"
        style="width: 100%"
      >
        <el-table-column prop="locationCode" label="库位编码" min-width="140" />
        <el-table-column prop="area" label="区域" width="120" />
        <el-table-column prop="qty" label="上架数量" width="120" align="center" />
        <el-table-column label="上架时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 底部操作按钮 -->
    <div class="footer-actions">
      <el-button
        v-if="detail?.status === 'PENDING'"
        class="m-primary-btn"
        @click="goPutaway"
      >
        <el-icon><Check /></el-icon>&nbsp;继续上架
      </el-button>
      <el-button @click="handleBack">
        <el-icon><ArrowLeft /></el-icon>&nbsp;返回列表
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as ordersApi from '@/api/orders'
import { useMobile } from '@/composables/useMobile'

const route = useRoute()
const router = useRouter()
const { isMobile } = useMobile()

/**
 * 同一个组件两用：路由页（/orders/:id）与列表页右侧抽屉。
 * 抽屉里传 orderId + embedded，隐藏页头返回条，“返回列表”改为关闭抽屉。
 */
const props = defineProps({
  orderId: { type: [String, Number], default: null },
  embedded: { type: Boolean, default: false }
})
const emit = defineEmits(['close'])

const detail = ref(null)
const loading = ref(false)

const remainingQty = computed(() => {
  if (!detail.value) return 0
  return Math.max(0, (detail.value.planQty || 0) - (detail.value.putQty || 0))
})

async function loadDetail() {
  const id = props.orderId ?? route.params.id
  if (!id) return
  loading.value = true
  try {
    detail.value = await ordersApi.detail(id)
  } finally {
    loading.value = false
  }
}

function handleBack() {
  if (props.embedded) emit('close')
  else router.back()
}

function goPutaway() {
  router.push({ path: '/putaway', query: { orderId: detail.value.id } })
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
// 抽屉内嵌：去掉页面容器的白卡与内边距，卡片改为上下排布
.detail-embedded {
  padding: 0;
  .card-d { margin: 0 0 12px; }
  .footer-actions { margin: 16px 0 0; }
}
.card-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 10px;
}
.detail-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.order-no {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.3px;
  color: #1e293b;
}
.m-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-green { background: #e6f7ef; color: #12b76a; }
}
.label-muted {
  color: #94a3b8;
  font-size: 12px;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  & + .info-grid { margin-top: 12px; }
}
.info-item .value {
  margin-top: 3px;
  font-size: 13px;
  color: #334155;
  word-break: break-all;
}
.batch-block { margin-top: 14px; }
// 明细行卡（移动端）
.d-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-of-type { border-bottom: 0; }
}
.d-item-main { min-width: 0; }
.d-item-name { font-size: 14px; color: #1e293b; b { margin-right: 6px; } }
.d-item-sub { font-size: 12px; color: #94a3b8; }
.d-item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
.d-qty { color: #1e54d8; }
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

// ---------- 桌面端 ----------
@media(min-width:768px) {
  .info-grid { grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px; }
  .info-item .value { font-size: 14px; }
  .footer-actions { flex-direction: row; justify-content: flex-start; }
  .m-page { background: rgba(255,255,255,.94); }
}
</style>
