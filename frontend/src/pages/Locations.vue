<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader>仓库与库位台账</PageHeader>

    <!-- 移动端：筛选 + 卡片列表 -->
    <template v-if="isMobile">
      <div class="card-m m-filter">
        <el-input v-model="filters.code" placeholder="库位编码" clearable @keyup.enter="loadData" />
        <el-input v-model="filters.area" placeholder="所属区域" clearable @keyup.enter="loadData" />
        <div class="m-btns">
          <el-button type="primary" class="m-primary-btn" @click="loadData">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
        <div v-if="auth.can('master:edit')" class="m-btns">
          <el-button type="success" @click="openDialog()"><el-icon><Plus /></el-icon>&nbsp;新增库位</el-button>
        </div>
      </div>

      <div class="m-count">共 {{ total }} 个库位</div>

      <div v-loading="loading" class="m-list">
        <div v-for="row in list" :key="row.id" class="card-m m-card">
          <div class="m-card-top">
            <span class="m-title">{{ row.code }}</span>
            <span class="m-tag" :class="row.enabled === false ? 't-gray' : 't-green'">{{ row.enabled === false ? '停用' : '启用' }}</span>
          </div>
          <div class="m-rows">
            <div class="d-item"><span class="l">仓库</span><span>{{ row.warehouseName || '备品备件间' }}</span></div>
            <div class="d-item"><span class="l">区域</span><span>{{ row.area || '-' }}</span></div>
            <div class="d-item">
              <span class="l">存储方式</span>
              <span class="m-tag" :class="row.storageMode === 'FLOOR' ? 't-amber' : 't-blue'">{{ row.storageMode === 'FLOOR' ? '地面位' : '货架位' }}</span>
            </div>
            <div class="d-item" v-if="row.storageMode === 'RACK'">
              <span class="l">物理位置</span><span>{{ row.rackNo }}排 / {{ row.levelNo }}层 / {{ row.positionNo }}位</span>
            </div>
            <div class="d-item" v-if="row.type"><span class="l">类型</span><span>{{ row.type }}</span></div>
            <div class="d-item">
              <span class="l">电子标签</span>
              <span v-if="row.esTagName">{{ row.esTagName }}<small v-if="row.deviceNo" class="m-sub-inline">（{{ row.deviceNo }}）</small></span>
              <span v-else-if="row.deviceNo" class="m-warn">{{ row.deviceNo }}（未登记）</span>
              <span v-else class="m-dim">未绑定</span>
            </div>
            <div class="d-item" v-if="row.remark"><span class="l">备注</span><span>{{ row.remark }}</span></div>
          </div>
          <div v-if="auth.can('master:edit')" class="m-actions">
            <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" description="暂无库位" />
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
        v-model="filters.code"
        placeholder="库位编码"
        clearable
        style="width: 200px"
        @keyup.enter="loadData"
      />
      <el-input
        v-model="filters.area"
        placeholder="所属区域"
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
        <el-icon><Plus /></el-icon>&nbsp;新增库位
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
      <el-table-column prop="code" label="库位编码" min-width="140" />
      <el-table-column prop="warehouseName" label="仓库" min-width="130"><template #default="{ row }">{{ row.warehouseName || '备品备件间' }}</template></el-table-column>
      <el-table-column prop="area" label="所属区域" min-width="120" />
      <el-table-column label="存储方式" width="100"><template #default="{ row }"><el-tag v-if="row.storageMode" :type="row.storageMode === 'FLOOR' ? 'warning' : ''">{{ row.storageMode === 'FLOOR' ? '地面位' : '货架位' }}</el-tag><span v-else>-</span></template></el-table-column>
      <el-table-column label="物理位置" min-width="150"><template #default="{ row }"><span v-if="row.storageMode === 'RACK'">{{ row.rackNo }}排 / {{ row.levelNo }}层 / {{ row.positionNo }}位</span><span v-else-if="row.storageMode === 'FLOOR'">地面区域</span><span v-else>-</span></template></el-table-column>
      <el-table-column label="类型" min-width="120">
        <template #default="{ row }">
          <el-tag v-if="row.type" :type="typeTagColor(row.type)">
            {{ row.type }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="电子标签" min-width="150">
        <template #default="{ row }">
          <template v-if="row.esTagName">
            <el-tag size="small" type="warning" effect="plain">
              {{ row.esTagName }}
            </el-tag>
            <div style="font-size:11px;color:#909399;margin-top:2px">{{ row.deviceNo }}</div>
          </template>
          <span v-else-if="row.deviceNo" style="color:#909399;font-size:12px">
            {{ row.deviceNo }}（标签未登记）
          </span>
          <span v-else style="color:#c0c4cc">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.enabled === false ? 'info' : 'success'">{{ row.enabled === false ? '停用' : '启用' }}</el-tag></template></el-table-column>
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

    <LocationFormDialog
      v-model="dialogVisible"
      :row="currentRow"
      @success="loadData"
    />
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as locationsApi from '@/api/locations'
import PageHeader from '@/components/PageHeader.vue'
import LocationFormDialog from '@/components/LocationFormDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const { isMobile } = useMobile()
const auth = useAuthStore()

const filters = reactive({ code: '', area: '' })
const page = reactive({ page: 1, size: 20 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const dialogVisible = ref(false)
const currentRow = ref(null)

async function loadData() {
  loading.value = true
  try {
    const r = await locationsApi.list({
      code: filters.code?.trim() || undefined,
      area: filters.area?.trim() || undefined,
      page: page.page - 1,
      size: page.size
    })
    list.value = r.content || []
    total.value = r.totalElements || 0
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.code = ''
  filters.area = ''
  page.page = 1
  loadData()
}

function openDialog(row = null) {
  currentRow.value = row
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除库位 ${row.code}？库位存在库存将无法删除。`,
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
  await locationsApi.remove(row.id)
  ElMessage.success('删除库位成功')
  if (list.value.length === 1 && page.page > 1) {
    page.page -= 1
  }
  loadData()
}

// 类型 → 标签色
function typeTagColor(type) {
  switch (type) {
    case '常温': return ''
    case '冷藏': return 'info'
    case '大件': return 'warning'
    case '危险品': return 'danger'
    default: return 'success'
  }
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.m-title { font-size: 15px; font-weight: 600; color: #1f2d3d; }
.m-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 10px;
  &.t-blue { background: #eaf1ff; color: #2e6cf6; }
  &.t-amber { background: #fbf3da; color: #d9a400; }
  &.t-green { background: #e6f7ef; color: #12b76a; }
  &.t-gray { background: #f1f5f9; color: #94a3b8; }
}
.m-rows {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
}
.d-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #1f2d3d;
  .l { color: #98a2b3; flex-shrink: 0; }
  > span:last-child { text-align: right; }
}
.m-sub-inline { color: #98a2b3; font-size: 11px; }
.m-warn { color: #d9a400; font-size: 12px; }
.m-dim { color: #c0c4cc; }
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
