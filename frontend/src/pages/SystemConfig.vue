<template>
  <div class="page-container">
    <PageHeader>系统配置</PageHeader>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"
      title="这里改的是备件预警与需求预测的计算参数，保存后立即生效，下一次预警计算就按新值算。改坏了可以点「恢复默认」。" />
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column label="配置项" min-width="200">
        <template #default="{row}">
          <div>{{ row.label }}</div>
          <div style="color:#909399;font-size:12px">{{ row.configKey }}</div>
        </template>
      </el-table-column>
      <el-table-column label="当前值" width="180">
        <template #default="{row}">
          <el-input-number v-if="row.valueType==='INT'" v-model="row.configValueNum" :min="1"
            :max="row.configKey==='spare-part.forecast.production-window-days'||row.configKey==='spare-part.alert.expiry-ahead-days'?365:180"
            controls-position="right" size="small" style="width:130px" />
          <span v-else>{{ row.configValue }}</span>
          <span v-if="row.unit" style="color:#909399;font-size:12px;margin-left:4px">{{ row.unit }}</span>
        </template>
      </el-table-column>
      <el-table-column label="默认值" width="90" align="center">
        <template #default="{row}">{{ row.defaultValue }}{{ row.unit||'' }}</template>
      </el-table-column>
      <el-table-column label="说明" min-width="280">
        <template #default="{row}"><span style="color:#606266;font-size:12px">{{ row.remark }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" @click="save(row)">保存</el-button>
          <el-button link @click="reset(row)">恢复默认</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as api from '@/api/systemConfig'

const rows = ref([]), loading = ref(false)

async function load() {
  loading.value = true
  try {
    const list = await api.list() || []
    rows.value = list.map(x => ({ ...x, configValueNum: Number(x.configValue) || 1 }))
  } finally { loading.value = false }
}

async function save(row) {
  await api.update(row.id, String(row.configValueNum))
  ElMessage.success(row.label + ' 已保存')
  load()
}

async function reset(row) {
  await ElMessageBox.confirm('把「' + row.label + '」恢复为出厂默认值 ' + row.defaultValue + '？', '恢复默认', { type: 'warning' })
  await api.reset(row.id)
  ElMessage.success('已恢复默认')
  load()
}

onMounted(load)
</script>
