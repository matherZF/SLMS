<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader>备件主数据</PageHeader>

    <!-- 移动端：筛选 + 卡片列表 -->
    <template v-if="isMobile">
      <div class="card-m m-filter">
        <el-input v-model="filters.sku" placeholder="SKU" clearable @keyup.enter="loadData" />
        <el-input v-model="filters.name" placeholder="货品名称" clearable @keyup.enter="loadData" />
        <div class="m-btns">
          <el-button type="primary" class="m-primary-btn" @click="loadData">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
        <el-button v-if="auth.can('master:edit')" type="success" class="m-add" @click="openDialog()">+ 新增备件</el-button>
      </div>

      <div class="m-count">共 {{ total }} 个备件</div>

      <div v-loading="loading" class="m-list">
        <div v-for="row in list" :key="row.id" class="card-m m-card">
          <div class="m-card-top">
            <div class="m-title-wrap">
              <span class="m-title">{{ row.name }}</span>
              <span class="m-sub">{{ row.sku }}</span>
            </div>
            <span class="m-tag" :class="row.enabled ? 't-green' : 't-gray'">{{ row.enabled ? '启用' : '停用' }}</span>
          </div>
          <div class="m-rows">
            <div class="d-item"><span class="l">分类</span><span>{{ row.category || '-' }}</span></div>
            <div class="d-item"><span class="l">单位</span><span>{{ row.unit || '-' }}</span></div>
            <div class="d-item"><span class="l">品牌/型号</span><span>{{ [row.brand, row.model].filter(Boolean).join(' / ') || '-' }}</span></div>
            <div class="d-item"><span class="l">规格</span><span>{{ row.spec || '-' }}</span></div>
            <div class="d-item">
              <span class="l">参考单价</span>
              <b class="m-price">{{ row.referenceUnitPrice == null ? '-' : `¥${Number(row.referenceUnitPrice).toFixed(2)}` }}</b>
            </div>
            <div class="d-item">
              <span class="l">关键程度</span>
              <span class="m-tag" :class="criticalityTone(row.criticality)">{{ criticalityText(row.criticality) }}</span>
            </div>
            <div class="d-item"><span class="l">安全库存</span><span>{{ row.safetyStock ?? '-' }}</span></div>
          </div>
          <div v-if="auth.can('master:edit')" class="m-actions">
            <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无备件" />
      </div>

      <div class="m-pager">
        <el-pagination
          v-model:current-page="page.page"
          :page-size="page.size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadData"
        />
      </div>
    </template>

    <!-- 桌面端 -->
    <template v-else>
    <!-- 过滤栏 -->
    <div class="filter-bar">
      <el-input
        v-model="filters.sku"
        placeholder="SKU"
        clearable
        style="width: 200px"
        @keyup.enter="loadData"
      />
      <el-input
        v-model="filters.name"
        placeholder="货品名称"
        clearable
        style="width: 200px"
        @keyup.enter="loadData"
      />
      <el-button type="primary" @click="loadData">
        <el-icon><Search /></el-icon>&nbsp;查询
      </el-button>
      <el-button @click="resetFilters">
        <el-icon><RefreshLeft /></el-icon>&nbsp;重置
      </el-button>
      <el-button v-if="auth.can('master:edit')" type="success" @click="openDialog()">
        <el-icon><Plus /></el-icon>&nbsp;新增备件
      </el-button>
    </div>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="list"
      stripe
      border
      style="width: 100%"
    >
      <el-table-column prop="sku" label="SKU编码" min-width="140" />
      <el-table-column prop="name" label="货品名称" min-width="180" />
      <el-table-column prop="category" label="分类" min-width="110"><template #default="{row}">{{row.category||'-'}}</template></el-table-column>
      <el-table-column label="品牌/型号" min-width="150"><template #default="{row}">{{[row.brand,row.model].filter(Boolean).join(' / ')||'-'}}</template></el-table-column>
      <el-table-column prop="spec" label="规格" min-width="120" />
      <el-table-column prop="unit" label="单位" width="90" />
      <el-table-column label="参考单价" width="110" align="right"><template #default="{row}">{{row.referenceUnitPrice==null?'-':`¥${Number(row.referenceUnitPrice).toFixed(2)}`}}</template></el-table-column>
      <el-table-column label="关键程度" width="100" align="center"><template #default="{row}"><el-tag :type="criticalityType(row.criticality)">{{criticalityText(row.criticality)}}</el-tag></template></el-table-column>
      <el-table-column prop="safetyStock" label="安全库存" width="100" align="center" />
      <el-table-column label="状态" width="85" align="center"><template #default="{row}"><el-tag :type="row.enabled?'success':'info'">{{row.enabled?'启用':'停用'}}</el-tag></template></el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column v-if="auth.can('master:edit')" label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">
            <el-icon><Edit /></el-icon>&nbsp;编辑
          </el-button>
          <el-button
            size="small"
            type="danger"
            @click="handleDelete(row)"
          >
            <el-icon><Delete /></el-icon>&nbsp;删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div style="margin-top: 16px; display: flex; justify-content: flex-end">
      <el-pagination
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        v-model:current-page="page.page"
        v-model:page-size="page.size"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>
    </template>

    <!-- 新增/编辑 Dialog -->
    <ProductFormDialog
      v-model="dialogVisible"
      :row="currentRow"
      @success="loadData"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as productsApi from '@/api/products'
import PageHeader from '@/components/PageHeader.vue'
import ProductFormDialog from '@/components/ProductFormDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const { isMobile } = useMobile()

// 过滤条件
const filters = reactive({ sku: '', name: '' })
const auth = useAuthStore()
// 分页
const page = reactive({ page: 1, size: 20 })
// 数据
const list = ref([])
const total = ref(0)
const loading = ref(false)

// 弹窗状态
const dialogVisible = ref(false)
const currentRow = ref(null)

// 加载列表
async function loadData() {
  loading.value = true
  try {
    const r = await productsApi.list({
      sku: filters.sku?.trim() || undefined,
      name: filters.name?.trim() || undefined,
      page: page.page - 1, // 后端 Spring Page 从 0 开始
      size: page.size
    })
    list.value = r.content || []
    total.value = r.totalElements || 0
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.sku = ''
  filters.name = ''
  page.page = 1
  loadData()
}

function openDialog(row = null) {
  currentRow.value = row
  dialogVisible.value = true
}

function criticalityText(value) { return { LOW: '一般', NORMAL: '普通', HIGH: '重要', CRITICAL: '关键' }[value] || '普通' }
function criticalityType(value) { return { LOW: 'info', NORMAL: '', HIGH: 'warning', CRITICAL: 'danger' }[value] || '' }
function criticalityTone(value) { return { LOW: 't-gray', NORMAL: 't-blue', HIGH: 't-amber', CRITICAL: 't-red' }[value] || 't-blue' }

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除货品 ${row.name}（SKU: ${row.sku}）？若该货品已被入库单引用将无法删除。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  await productsApi.remove(row.id)
  ElMessage.success('删除货品成功')
  // 删除后若当前页空了，回退一页
  if (list.value.length === 1 && page.page > 1) {
    page.page -= 1
  }
  loadData()
}

// 时间格式化工具
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

onMounted(loadData)
</script>

<style scoped lang="scss">
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
.m-btns {
  display: flex;
  gap: 10px;
  :deep(.el-button) { flex: 1; margin: 0; min-height: 40px; }
}
.m-primary-btn { border: 0; color: #fff; background: linear-gradient(135deg, #2e6ef2, #1e50d8); &:active { opacity: .9; } }
.m-add { width: 100%; margin: 0; min-height: 40px; }
.m-count { margin: 12px 14px 0; font-size: 12px; color: #7a8699; }
.m-list { display: flex; flex-direction: column; gap: 10px; padding: 10px 12px 0; min-height: 120px; }
.m-card {
  background: #fff;
  border-radius: 14px;
  padding: 12px;
  box-shadow: 0 2px 10px rgba(30, 60, 120, .05);
}
.m-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.m-title-wrap { min-width: 0; }
.m-title { font-size: 15px; font-weight: 600; color: #1f2d3d; }
.m-sub { display: block; margin-top: 2px; font-size: 12px; color: #7a8699; }
.m-tag { flex-shrink: 0; font-size: 11px; padding: 3px 10px; border-radius: 10px;
  &.t-blue { background: #eaf1ff; color: #2e6cf6; }
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-green { background: #e6f7ef; color: #12b76a; }
  &.t-red { background: #fdeaea; color: #e34d59; }
  &.t-gray { background: #f1f5f9; color: #94a3b8; }
}
.m-rows { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px; }
.d-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #1f2d3d;
  .l { color: #98a2b3; flex-shrink: 0; }
  > :last-child { text-align: right; }
}
.m-price { color: #e34d59; font-weight: 600; }
.m-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  :deep(.el-button) { margin: 0; }
}
.m-pager { display: flex; justify-content: center; padding: 12px 0 4px; }
</style>
