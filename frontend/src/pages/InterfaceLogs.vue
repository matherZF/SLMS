<template>
  <div class="page-container">
    <PageHeader>
      接口管理
      <template #desc>记录本系统与外部系统的收发报文；设备运行数据的补拉入口已移至「设备管理」</template>
    </PageHeader>
    <el-alert title="记录本系统与外部系统的收发报文。列表仅展示摘要，点击“详情”查看完整报文。" type="info" :closable="false" show-icon style="margin-bottom:16px" />

    <el-tabs v-model="filters.direction" @tab-change="search">
      <el-tab-pane label="接收报文" name="INBOUND" />
      <el-tab-pane label="发送报文" name="OUTBOUND" />
    </el-tabs>

    <div class="filter-bar">
      <el-input v-model="filters.interfaceCode" clearable placeholder="接口编码" style="width:200px" @keyup.enter="search" />
      <el-select v-model="filters.success" clearable placeholder="处理结果" style="width:140px">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-input v-model="filters.keyword" clearable placeholder="接口名称/对端/追踪号" style="width:240px" @keyup.enter="search" />
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>&nbsp;查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>&nbsp;重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无接口报文">
      <el-table-column label="交互时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="interfaceCode" label="接口编码" width="190" show-overflow-tooltip />
      <el-table-column label="接口名称" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.interfaceName || '-' }}</template>
      </el-table-column>
      <el-table-column label="通道" width="90">
        <template #default="{ row }">{{ row.channel || '-' }}</template>
      </el-table-column>
      <el-table-column label="对端系统" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.peer || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" effect="plain">{{ row.success ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}</template>
      </el-table-column>
      <el-table-column label="追踪号" width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.traceId || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top:16px;display:flex;justify-content:flex-end">
      <el-pagination v-model:current-page="page.page" v-model:page-size="page.size" :total="total"
        :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next"
        @current-change="load" @size-change="load" />
    </div>

    <el-drawer v-model="detailVisible" title="报文详情" size="800px">
      <template v-if="detail.id">
        <div class="payload-title">请求报文</div>
        <pre class="payload">{{ pretty(detail.requestBody) }}</pre>
        <div class="payload-title">响应报文</div>
        <pre class="payload">{{ pretty(detail.responseBody) }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as interfaceLogApi from '@/api/interfaceLogs'

const filters = reactive({ direction: 'INBOUND', interfaceCode: '', success: '', keyword: '' })
const page = reactive({ page: 1, size: 20 })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref({})

async function load() {
  loading.value = true
  try {
    const success = filters.success === '' || filters.success === null ? undefined : filters.success
    const r = await interfaceLogApi.page({
      direction: filters.direction,
      interfaceCode: filters.interfaceCode.trim() || undefined,
      success,
      keyword: filters.keyword.trim() || undefined,
      page: page.page - 1,
      size: page.size
    })
    rows.value = r.content || []
    total.value = r.totalElements || 0
  } finally {
    loading.value = false
  }
}

function search() { page.page = 1; load() }

function reset() {
  filters.interfaceCode = ''
  filters.success = ''
  filters.keyword = ''
  search()
}

async function openDetail(id) {
  detail.value = await interfaceLogApi.detail(id)
  detailVisible.value = true
}

function formatTime(v) {
  if (!v) return '-'
  return new Date(v).toLocaleString('zh-CN', { hour12: false })
}

function pretty(v) {
  if (!v) return '-'
  try { return JSON.stringify(JSON.parse(v), null, 2) } catch { return v }
}

onMounted(load)
</script>

<style scoped lang="scss">
.payload-title {
  margin: 18px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.payload {
  margin: 0;
  padding: 12px 14px;
  max-height: 320px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
