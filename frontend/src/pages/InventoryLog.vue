<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader>库存日志</PageHeader>

    <!-- 移动端：筛选 + 卡片列表 -->
    <template v-if="isMobile">
      <div class="card-m m-filter">
        <el-input
          v-model="s.sku"
          placeholder="SKU / 商品名称"
          clearable
          @keyup.enter="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input
          v-model="s.locationCode"
          placeholder="库位编码"
          clearable
          @keyup.enter="onSearch"
        />
        <el-select v-model="s.changeType" placeholder="变动类型" clearable>
          <el-option label="入库" value="INBOUND" />
          <el-option label="出库" value="OUTBOUND" />
          <el-option label="退库" value="RETURN" />
          <el-option label="报废" value="SCRAP" />
          <el-option label="盘点调整" value="ADJUST" />
          <el-option label="移库转出" value="TRANSFER_OUT" />
          <el-option label="移库转入" value="TRANSFER_IN" />
        </el-select>
        <div class="m-filter-btns">
          <el-button type="primary" class="m-primary-btn" @click="onSearch">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </div>

      <div class="card-m m-count">共 {{ s.total }} 条变动记录</div>

      <div v-loading="s.loading" class="m-list">
        <div v-for="(row, i) in list" :key="row.id ?? i" class="card-m m-log-card">
          <div class="m-card-top">
            <span class="m-time">{{ formatTime(row.createdAt) }}</span>
            <span class="m-tag" :class="mTone(row.changeType)">
              {{ changeTypeLabel(row.changeType) }}
            </span>
          </div>
          <div class="m-title">{{ row.productName || row.sku }}</div>
          <div class="m-sub">{{ row.sku }}<template v-if="row.locationCode"> · 库位 {{ row.locationCode }}</template></div>
          <div class="m-rows">
            <div class="d-item">
              <span class="l">变动数量</span>
              <span :class="isIncrease(row) ? 'qty-in' : 'qty-out'">
                {{ isIncrease(row) ? '+' : '-' }}{{ row.changeQty }}
              </span>
            </div>
            <div class="d-item">
              <span class="l">变动后</span>
              <b class="qty-num">{{ row.afterQty }}</b>
            </div>
            <div class="d-item" v-if="row.itemKey">
              <span class="l">批次号</span><span>{{ row.itemKey }}</span>
            </div>
            <div class="d-item" v-if="row.expiryDate">
              <span class="l">到期日</span>
              <span :class="{ 'expire-soon': isExpiringSoon(row.expiryDate) }">{{ row.expiryDate }}</span>
            </div>
            <div class="d-item" v-if="row.refType">
              <span class="l">关联单据</span>
              <span>{{ refTypeLabel(row.refType) }} · {{ row.refNo }}</span>
            </div>
            <div class="d-item" v-if="row.operator">
              <span class="l">操作人</span><span>{{ row.operator }}</span>
            </div>
          </div>
        </div>
        <el-empty v-if="!s.loading && !list.length" description="暂无库存变动记录" />
      </div>

      <div class="m-pager">
        <el-pagination
          v-model:current-page="s.page"
          :page-size="s.size"
          :total="s.total"
          layout="prev, pager, next"
          background
          @current-change="load"
        />
      </div>
    </template>

    <!-- 桌面端：筛选 + 表格 -->
    <el-card v-else class="log-card" shadow="never">
      <!-- 筛选条件 -->
      <div class="filter-bar">
        <el-input
          v-model="s.sku"
          placeholder="SKU / 商品名称"
          clearable
          style="width: 220px"
          @keyup.enter="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input
          v-model="s.locationCode"
          placeholder="库位编码"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
        />
        <el-select
          v-model="s.changeType"
          placeholder="变动类型"
          clearable
          style="width: 150px"
        >
          <el-option label="入库" value="INBOUND" />
          <el-option label="出库" value="OUTBOUND" />
          <el-option label="退库" value="RETURN" />
          <el-option label="报废" value="SCRAP" />
          <el-option label="盘点调整" value="ADJUST" />
          <el-option label="移库转出" value="TRANSFER_OUT" />
          <el-option label="移库转入" value="TRANSFER_IN" />
        </el-select>
        <el-button type="primary" @click="onSearch">
          <el-icon><Search /></el-icon>&nbsp;查询
        </el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <!-- 日志表格 -->
      <el-table
        v-loading="s.loading"
        :data="list"
        stripe
        border
        style="width: 100%; margin-top: 16px"
        :empty-text="'暂无库存变动记录'"
      >
        <el-table-column label="时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="变动类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="changeTypeTag(row.changeType)"
              effect="light"
            >
              {{ changeTypeLabel(row.changeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sku" label="SKU" min-width="120" />
        <el-table-column prop="productName" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="locationCode" label="库位" width="120" />
        <el-table-column label="变动数量" width="110" align="center">
          <template #default="{ row }">
            <span :class="isIncrease(row) ? 'qty-in' : 'qty-out'">
              {{ isIncrease(row) ? '+' : '-' }}{{ row.changeQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前" width="100" align="center">
          <template #default="{ row }">{{ row.beforeQty }}</template>
        </el-table-column>
        <el-table-column label="变动后" width="100" align="center">
          <template #default="{ row }">
            <b class="qty-num">{{ row.afterQty }}</b>
          </template>
        </el-table-column>
        <el-table-column label="关联单据" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" v-if="row.refType">
              {{ refTypeLabel(row.refType) }} · {{ row.refNo }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="批次号" min-width="130">
          <template #default="{ row }">
            <b v-if="row.itemKey">{{ row.itemKey }}</b>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" width="110" align="center">
          <template #default="{ row }">{{ row.productionDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="到期日" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.expiryDate" :class="{ 'expire-soon': isExpiringSoon(row.expiryDate) }">
              {{ row.expiryDate }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="生产厂商" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.manufacturer || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" align="center">
          <template #default="{ row }">{{ row.operator || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="s.page"
          v-model:page-size="s.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="s.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { useMobile } from '@/composables/useMobile'
import * as inventoryLogApi from '@/api/inventoryLog'

const { isMobile } = useMobile()

const s = reactive({
  sku: '',
  locationCode: '',
  changeType: '',
  page: 1,
  size: 20,
  total: 0,
  loading: false
})

const list = ref([])

async function load() {
  s.loading = true
  try {
    // 后端 Page 的页码从 0 开始，前端从 1 开始
    const params = {
      page: s.page - 1,
      size: s.size
    }
    if (s.sku?.trim()) params.sku = s.sku.trim()
    if (s.locationCode?.trim()) params.locationCode = s.locationCode.trim()
    if (s.changeType) params.changeType = s.changeType

    const res = await inventoryLogApi.page(params)
    list.value = (res && res.content) || []
    s.total = (res && res.totalElements) || 0
  } finally {
    s.loading = false
  }
}

function onSearch() {
  s.page = 1
  load()
}

function onReset() {
  s.sku = ''
  s.locationCode = ''
  s.changeType = ''
  s.page = 1
  load()
}

function refTypeLabel(type) {
  if (type === 'PUTAWAY') return '上架单'
  if (type === 'OUTBOUND') return '出库单'
  if (type === 'RETURN') return '退库单'
  if (type === 'OUTBOUND_RETURN') return '原领用单'
  if (type === 'STOCKTAKE') return '盘点单'
  if (type === 'TRANSFER') return '移库单'
  if (type === 'SCRAP') return '报废单'
  return type
}

function changeTypeLabel(type) {
  return { INBOUND: '入库', OUTBOUND: '出库', RETURN: '退库', SCRAP: '报废', ADJUST: '盘点调整', TRANSFER_OUT: '移库转出', TRANSFER_IN: '移库转入' }[type] || type
}

function changeTypeTag(type) {
  return { INBOUND: 'success', OUTBOUND: 'warning', RETURN: 'primary', SCRAP: 'danger', ADJUST: 'info', TRANSFER_OUT: 'warning', TRANSFER_IN: 'success' }[type] || 'info'
}

function mTone(type) {
  return { INBOUND: 't-green', OUTBOUND: 't-orange', RETURN: 't-blue', SCRAP: 't-red', ADJUST: 't-amber', TRANSFER_OUT: 't-orange', TRANSFER_IN: 't-green' }[type] || 't-gray'
}

function isIncrease(row) {
  return row.changeType === 'INBOUND' || row.changeType === 'RETURN' || row.changeType === 'TRANSFER_IN' || row.afterQty > row.beforeQty
}

function isExpiringSoon(expiryDate) {
  if (!expiryDate) return false
  const days = Math.ceil((new Date(expiryDate) - new Date()) / 86400000)
  return days <= 30
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
    pad(d.getMinutes()) +
    ':' +
    pad(d.getSeconds())
  )
}

onMounted(() => {
  load()
})
</script>

<style scoped lang="scss">
.log-card {
  border-radius: 6px;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.qty-num {
  color: $primary;
}
.qty-in {
  color: #67c23a;
  font-weight: 600;
}
.qty-out {
  color: #e6a23c;
  font-weight: 600;
}
.expire-soon {
  color: #e6a23c;
  font-weight: 600;
}

/* ---------- 移动端 ---------- */
.m-filter {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}
.m-filter-btns {
  display: flex;
  gap: 10px;
  :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }
}
.m-count {
  padding: 10px 12px;
  font-size: 12px;
  color: #7a8699;
}
.m-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}
.m-log-card {
  padding: 12px;
}
.m-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.m-time {
  font-size: 12px;
  color: #98a2b3;
}
.m-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}
.m-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #7a8699;
}
.m-rows {
  margin-top: 8px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
}
.d-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  .l {
    color: #98a2b3;
  }
}
.m-pager {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}
</style>
