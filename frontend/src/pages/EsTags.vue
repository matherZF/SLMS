<template>
  <div class="page-container" :class="{ 'm-page': isMobile }">
    <PageHeader>电子标签管理</PageHeader>

    <!-- 移动端：筛选 + 卡片列表 -->
    <template v-if="isMobile">
      <div class="card-m m-filter">
        <el-input v-model="keyword" placeholder="MAC 地址 / 名称" clearable @keyup.enter="load" />
        <div class="m-btns">
          <el-button type="primary" class="m-primary-btn" @click="load">查询</el-button>
          <el-button v-if="auth.can('master:edit')" type="warning" :loading="syncing" @click="handleSync">信息同步</el-button>
        </div>
        <el-button v-if="auth.can('master:edit')" type="success" class="m-add" @click="open()">+ 新增标签</el-button>
      </div>

      <div class="m-count">共 {{ rows.length }} 个标签</div>

      <div v-loading="loadingRef" class="m-list">
        <div v-for="row in rows" :key="row.id" class="card-m m-card">
          <div class="m-card-top">
            <div class="m-title-wrap">
              <span class="m-title">{{ row.mac }}</span>
              <span v-if="row.name" class="m-sub">{{ row.name }}</span>
            </div>
            <span class="m-tag" :class="row.online ? 't-green' : 't-gray'">{{ row.online ? '在线' : '离线' }}</span>
          </div>
          <div class="m-rows">
            <div class="d-item">
              <span class="l">绑定库位</span>
              <span v-if="row.locationId" class="m-tag t-blue">{{ row.locationCode }}</span>
              <span v-else class="m-dim">未绑定</span>
            </div>
            <div class="d-item">
              <span class="l">状态</span>
              <span class="m-tag" :class="row.status === 'ACTIVE' ? 't-green' : 't-gray'">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</span>
            </div>
            <div class="d-item"><span class="l">固件版本</span><span>{{ row.firmwareVersion || '-' }}</span></div>
            <div class="d-item"><span class="l">最后通信</span><span>{{ row.lastSeenAt ? formatTime(row.lastSeenAt) : '-' }}</span></div>
          </div>
          <div v-if="row.power != null" class="m-power">
            <span class="l">电量</span>
            <el-progress class="m-power-bar" :percentage="row.power" :color="powerColor(row.power)" :stroke-width="8" />
          </div>
          <div class="m-actions">
            <el-button size="small" type="success" plain @click="handleLight(row)">点亮</el-button>
            <el-button size="small" plain @click="handleLightOff(row)">熄灭</el-button>
            <el-button size="small" type="primary" plain @click="open(row)">编辑</el-button>
            <el-button v-if="auth.can('master:edit')" size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!rows.length" description="暂无电子标签" />
      </div>
    </template>

    <!-- 桌面端 -->
    <template v-else>
    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="MAC 地址 / 名称"
        style="width: 240px"
        @keyup.enter="load"
      />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button
        v-if="auth.can('master:edit')"
        type="warning"
        :loading="syncing"
        @click="handleSync"
      >
        信息同步
      </el-button>
      <el-button v-if="auth.can('master:edit')" type="success" @click="open()">
        新增标签
      </el-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="mac" label="MAC 地址" width="160">
        <template #default="{ row }">
          <b>{{ row.mac }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          {{ row.name || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="绑定库位" min-width="120">
        <template #default="{ row }">
          <template v-if="row.locationId">
            <el-tag effect="light" size="small">{{ row.locationCode }}</el-tag>
          </template>
          <span v-else style="color: #c0c4cc">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column label="在线" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.online ? 'success' : 'info'" effect="light" size="small">
            {{ row.online ? '在线' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="电量" width="100">
        <template #default="{ row }">
          <el-progress
            v-if="row.power != null"
            :percentage="row.power"
            :color="powerColor(row.power)"
            :stroke-width="10"
          />
          <span v-else style="color: #c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="firmwareVersion" label="固件版本" width="110">
        <template #default="{ row }">{{ row.firmwareVersion || '-' }}</template>
      </el-table-column>
      <el-table-column label="最后通信" width="170">
        <template #default="{ row }">
          {{ row.lastSeenAt ? formatTime(row.lastSeenAt) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button link type="success" @click="handleLight(row)">点亮</el-button>
          <el-button link type="info" @click="handleLightOff(row)">熄灭</el-button>
          <el-button
            v-if="auth.can('master:edit')"
            link
            type="danger"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    </template>

    <!-- 新增/编辑 -->
    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑电子标签' : '新增电子标签'"
      width="560px"
    >
      <el-form label-width="110px">
        <el-form-item label="MAC 地址">
          <el-input v-model="form.mac" placeholder="例：99.26.17.85" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="可选备注名" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option value="LIGHT_LED" label="带灯墨水屏标签" />
            <el-option value="RACK" label="货架读卡器" />
          </el-select>
        </el-form-item>
        <el-form-item label="固件版本">
          <el-input v-model="form.firmwareVersion" placeholder="可选，留空由服务器同步" />
        </el-form-item>
        <el-form-item label="在线">
          <el-switch v-model="form.online" />
        </el-form-item>
        <el-form-item label="电量(%)">
          <el-input-number v-model="form.power" :min="0" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="绑定库位">
          <el-select
            v-model="form.locationId"
            filterable
            clearable
            style="width: 100%"
            placeholder="留空表示不绑定库位"
          >
            <el-option
              v-for="loc in locations"
              :key="loc.id"
              :value="loc.id"
              :label="loc.code + (loc.deviceNo && loc.deviceNo !== form.mac ? '（MAC已绑：' + loc.deviceNo + '）' : '')"
            />
          </el-select>
          <div class="form-hint">保存后该库位的 deviceNo 自动对齐为标签 MAC；清空则解除绑定</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option value="ACTIVE" label="启用" />
            <el-option value="DISABLED" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 点灯设置 -->
    <el-dialog v-model="lightVisible" :title="'点亮标签 — ' + lightRow.mac" width="420px">
      <el-form label-width="80px">
        <el-form-item label="颜色">
          <el-select v-model="lightForm.rgb" style="width: 100%">
            <el-option label="🔴 红色" value="ff0000" />
            <el-option label="🟢 绿色" value="00ff00" />
            <el-option label="🔵 蓝色" value="0000ff" />
            <el-option label="🟡 黄色" value="ffff00" />
            <el-option label="⚪ 白色" value="ffffff" />
            <el-option label="🟣 紫色" value="ff00ff" />
            <el-option label="🔵 浅蓝" value="00ffff" />
          </el-select>
        </el-form-item>
        <el-form-item label="模式">
          <el-radio-group v-model="lightForm.mode">
            <el-radio-button :value="0">常亮</el-radio-button>
            <el-radio-button :value="1">快闪</el-radio-button>
            <el-radio-button :value="2">慢闪</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="超时(秒)">
          <el-input-number v-model="lightForm.timeout" :min="0" :max="3600" controls-position="right" />
          <span style="font-size:12px;color:#909399;margin-left:8px">0 = 不自动熄灭</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lightVisible = false">取消</el-button>
        <el-button type="success" :loading="lighting" @click="doLight">发光点灯</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import * as api from '@/api/esTags'
import * as locationApi from '@/api/locations'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const { isMobile } = useMobile()
const auth = useAuthStore()

const keyword = ref('')
const rows = ref([])
const loadingRef = ref(false)
const visible = ref(false)
const form = ref({ type: 'LIGHT_LED', online: false, status: 'ACTIVE' })

const locations = ref([])

const lightVisible = ref(false)
const lightRow = ref({})
const lightForm = ref({ rgb: 'ff0000', mode: 0, timeout: 30 })
const lighting = ref(false)
const syncing = ref(false)

const load = async () => {
  loadingRef.value = true
  try {
    rows.value = await api.list({ keyword: keyword.value })
  } finally {
    loadingRef.value = false
  }
}

const open = async (r) => {
  form.value = r ? { ...r } : { type: 'LIGHT_LED', online: false, status: 'ACTIVE', locationId: null }
  if (!locations.value.length) {
    const x = await locationApi.list({ page: 0, size: 500 })
    // /api/locations 返回 Page<LocationDTO>，真正数据在 .content
    locations.value = x.content || x || []
  }
  visible.value = true
}

const save = async () => {
  if (!form.value.mac) return ElMessage.warning('MAC 不能为空')
  form.value.id
    ? await api.update(form.value.id, form.value)
    : await api.create(form.value)
  visible.value = false
  ElMessage.success('已保存')
  load()
}

const powerColor = (v) => {
  if (v == null) return '#dcdfe6'
  if (v >= 60) return '#67c23a'
  if (v >= 20) return '#e6a23c'
  return '#f56c6c'
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该电子标签？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await api.remove(row.id)
  ElMessage.success('已删除')
  load()
}

const handleLight = (row) => {
  lightRow.value = row
  lightForm.value = { rgb: 'ff0000', mode: 0, timeout: 30 }
  lightVisible.value = true
}

const handleSync = async () => {
  try {
    await ElMessageBox.confirm(
      '将向墨水屏服务器查询所有已登记电子标签的运行时信息（电量/在线/固件版本），并回填到本地数据库。是否继续？',
      '信息同步',
      { type: 'info', confirmButtonText: '开始同步', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  syncing.value = true
  try {
    const r = await api.syncFromDevice()
    const total = r?.total ?? 0
    const queried = r?.queried ?? 0
    const updated = r?.updated ?? 0
    ElMessage.success(`同步完成：共 ${total} 条，墨水屏服务器返回 ${queried} 条，已更新 ${updated} 条`)
    load()
  } catch (e) {
    ElMessage.error('同步失败：' + (e?.msg || e?.message || '未知错误'))
  } finally {
    syncing.value = false
  }
}

const doLight = async () => {
  lighting.value = true
  try {
    const r = await api.lightOn(lightRow.value.mac, lightForm.value)
    if (r?.success === false) throw new Error('墨水屏服务器返回 false')
    ElMessage.success('点灯指令已发送，等待墨水屏服务器回执')
    lightVisible.value = false
  } catch (e) {
    ElMessage.error('点灯失败：' + (e?.msg || e?.message || '未知错误'))
  } finally {
    lighting.value = false
  }
}

const handleLightOff = async (row) => {
  try {
    await ElMessageBox.confirm('确定熄灭该标签 LED？', '操作确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    const r = await api.lightOff(row.mac)
    if (r?.success === false) throw new Error('墨水屏服务器返回 false')
    ElMessage.success('熄灭指令已发送')
  } catch (e) {
    ElMessage.error('熄灭失败：' + (e?.msg || e?.message || '未知错误'))
  }
}

const formatTime = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(load)
</script>

<style scoped>
.form-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.5; }
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

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
.m-btns { display: flex; gap: 10px; }
.m-btns .el-button { flex: 1; margin: 0; min-height: 40px; }
.m-primary-btn { border: 0; color: #fff; background: linear-gradient(135deg, #2e6ef2, #1e50d8); }
.m-add { width: 100%; margin: 0; min-height: 40px; }
.m-count { margin: 12px 14px 0; font-size: 12px; color: #7a8699; }
.m-list { display: flex; flex-direction: column; gap: 10px; padding: 10px 12px 0; min-height: 120px; }
.m-card { background: #fff; border-radius: 14px; padding: 12px; box-shadow: 0 2px 10px rgba(30, 60, 120, .05); }
.m-card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.m-title-wrap { min-width: 0; }
.m-title { font-size: 15px; font-weight: 600; color: #1f2d3d; word-break: break-all; }
.m-sub { display: block; margin-top: 2px; font-size: 12px; color: #7a8699; }
.m-tag { flex-shrink: 0; font-size: 11px; padding: 3px 10px; border-radius: 10px; }
.m-tag.t-blue { background: #eaf1ff; color: #2e6cf6; }
.m-tag.t-green { background: #e6f7ef; color: #12b76a; }
.m-tag.t-gray { background: #f1f5f9; color: #94a3b8; }
.m-rows { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 12px; }
.d-item { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 13px; color: #1f2d3d; }
.d-item .l { color: #98a2b3; flex-shrink: 0; }
.d-item > span:last-child { text-align: right; }
.m-power { display: flex; align-items: center; gap: 10px; margin-top: 8px; font-size: 13px; }
.m-power .l { color: #98a2b3; }
.m-power-bar { flex: 1; }
.m-actions {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.m-actions .el-button { margin: 0; }
.m-dim { color: #c0c4cc; }
</style>
