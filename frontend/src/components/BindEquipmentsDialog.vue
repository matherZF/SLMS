<template>
  <el-dialog
    :model-value="modelValue"
    :title="`批量关联设备 · ${product?.name || ''}`"
    width="760px"
    destroy-on-close
    @update:model-value="v => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div v-if="product" class="be-product-line">
      <span class="be-sku">{{ product.sku }}</span>
      <span v-if="product.spec" class="be-dim"> · {{ product.spec }}</span>
    </div>

    <!-- 筛选 -->
    <div class="be-filter">
      <el-input v-model="keyword" placeholder="设备编码 / 名称" clearable style="width: 200px" @input="applySelection" />
      <el-select v-model="zone" placeholder="全部区域" clearable style="width: 160px" @change="applySelection">
        <el-option v-for="z in zoneOptions" :key="z.itemCode" :value="z.itemCode" :label="z.itemLabel" />
      </el-select>
      <el-button @click="selectAllFiltered">勾选当前筛选结果</el-button>
      <span class="be-dim">已选 {{ selectedIds.size }} 台</span>
    </div>

    <!-- 设备多选表 -->
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="filtered"
      height="300"
      size="small"
      border
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column prop="code" label="设备编码" width="130" />
      <el-table-column prop="name" label="设备名称" min-width="150" show-overflow-tooltip />
      <el-table-column label="区域" width="120"><template #default="{row}">{{ zoneLabel(row.zone) || '-' }}</template></el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{row}"><el-tag size="small" :type="row.status === 'RUNNING' ? 'success' : 'info'">{{ statusText(row.status) }}</el-tag></template>
      </el-table-column>
    </el-table>

    <!-- 粘贴编码批量勾选 -->
    <el-collapse class="be-paste-wrap">
      <el-collapse-item title="按编码粘贴勾选（一行一个，或用逗号/空格分隔）">
        <el-input v-model="pasteText" type="textarea" :rows="4" placeholder="SC01&#10;SC02&#10;RB01 ..." />
        <div style="margin-top: 8px">
          <el-button type="primary" plain size="small" @click="parsePaste">解析并勾选</el-button>
          <span v-if="pasteReport" class="be-report">{{ pasteReport }}</span>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- 统一参数 -->
    <div class="be-params">
      <span>单机数量</span>
      <el-input-number v-model="installedQty" :min="1" :max="9999" style="width: 120px" />
      <span class="be-gap">更换周期（天）</span>
      <el-input-number v-model="changeCycleDays" :min="1" :max="9999" style="width: 120px" placeholder="选填" />
      <span class="be-dim">选填；不填则补货点只按安全库存计算。本次参数将应用到所有勾选的设备，已有关联的设备会被覆盖更新。</span>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedIds.size" @click="submit">
        关联到 {{ selectedIds.size }} 台设备
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as productsApi from '@/api/products'
import * as equipmentApi from '@/api/equipment'
import * as dictApi from '@/api/dictionaries'

const props = defineProps({
  modelValue: Boolean,
  product: Object
})
const emit = defineEmits(['update:modelValue', 'success'])

const loading = ref(false), submitting = ref(false)
const devices = ref([]), zoneOptions = ref([])
const keyword = ref(''), zone = ref('')
const tableRef = ref(null)
const selectedIds = ref(new Set())
const installedQty = ref(1)
const changeCycleDays = ref(null)
const pasteText = ref(''), pasteReport = ref('')

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return devices.value.filter(d => {
    if (zone.value && d.zone !== zone.value) return false
    if (kw && !(`${d.code || ''}`.toLowerCase().includes(kw) || `${d.name || ''}`.toLowerCase().includes(kw))) return false
    return true
  })
})

async function onOpen() {
  pasteText.value = ''; pasteReport.value = ''
  selectedIds.value = new Set()
  installedQty.value = 1; changeCycleDays.value = null
  keyword.value = ''; zone.value = ''
  loading.value = true
  try {
    if (!zoneOptions.value.length) zoneOptions.value = await dictApi.options('EQUIPMENT_ZONE')
    devices.value = await equipmentApi.list()
  } finally {
    loading.value = false
  }
}

function zoneLabel(v) {
  if (!v) return ''
  const hit = zoneOptions.value.find(x => x.itemCode === v)
  return hit ? hit.itemLabel : v
}

function statusText(v) { return { RUNNING: '运行', IDLE: '闲置', MAINTENANCE: '维修', RETIRED: '停用' }[v] || v || '-' }

// 勾选状态与 selectedIds 双向同步：跨筛选保留勾选
function onSelectionChange(rows) {
  const now = new Set(rows.map(r => r.id))
  const next = new Set(selectedIds.value)
  for (const r of filtered.value) {
    if (now.has(r.id)) next.add(r.id)
    else next.delete(r.id)
  }
  selectedIds.value = next
}

// 筛选变化 / 数据刷新后，把 selectedIds 里命中的行重新勾上
function applySelection() {
  requestAnimationFrame(() => {
    const table = tableRef.value
    if (!table) return
    for (const r of filtered.value) table.toggleRowSelection(r, selectedIds.value.has(r.id))
  })
}

function selectAllFiltered() {
  const table = tableRef.value
  if (!table) return
  for (const r of filtered.value) if (!selectedIds.value.has(r.id)) table.toggleRowSelection(r, true)
}

// 粘贴解析：拆分 → 匹配设备编码（忽略大小写）→ 勾选命中的行
function parsePaste() {
  const codes = pasteText.value.split(/[\s,，;；、]+/).map(s => s.trim()).filter(Boolean)
  if (!codes.length) { pasteReport.value = ''; return }
  const index = new Map(devices.value.map(d => [`${d.code}`.trim().toUpperCase(), d]))
  const matched = [], missing = []
  const seen = new Set()
  for (const c of codes) {
    const key = c.toUpperCase()
    if (seen.has(key)) continue
    seen.add(key)
    const d = index.get(key)
    if (d) matched.push(d); else missing.push(c)
  }
  for (const d of matched) if (!selectedIds.value.has(d.id)) selectedIds.value = new Set([...selectedIds.value, d.id])
  applySelection()
  pasteReport.value = `匹配 ${matched.length} 台` + (missing.length ? `；未找到：${missing.slice(0, 10).join('、')}${missing.length > 10 ? ' 等' : ''}` : '')
  if (missing.length) ElMessage.warning(`${missing.length} 个编码未匹配到设备，请检查`)
}

async function submit() {
  if (!props.product) return
  if (changeCycleDays.value != null && changeCycleDays.value <= 0) { ElMessage.warning('更换周期必须大于 0 天'); return }
  submitting.value = true
  try {
    const r = await productsApi.bindEquipments(props.product.id, {
      equipmentIds: [...selectedIds.value],
      installedQty: installedQty.value,
      changeCycleDays: changeCycleDays.value
    })
    ElMessage.success(`关联完成：新增 ${r.created} 台，更新 ${r.updated} 台`)
    emit('update:modelValue', false)
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.be-product-line { margin-bottom: 10px; font-size: 14px; font-weight: 600; color: #1f2d3d; }
.be-sku { color: #2e6cf6; font-family: Consolas, monospace; }
.be-dim { color: #98a2b3; font-size: 12px; }
.be-filter { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.be-paste-wrap { margin: 10px 0; }
.be-report { margin-left: 10px; font-size: 12px; color: #7a8699; }
.be-params { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 10px; font-size: 13px; color: #475467; }
.be-gap { margin-left: 12px; }
</style>
