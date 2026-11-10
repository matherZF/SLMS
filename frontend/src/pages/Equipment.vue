<template>
  <div class="page-container">
    <PageHeader>
      设备管理
      <template #desc>设备档案与运行数据；支持按区域折叠查看，双击设备行可打开详情</template>
      <template #actions>
        <el-button :loading="sync.loading" @click="openSync">
          <el-icon><Refresh /></el-icon>&nbsp;补拉设备数据
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-input v-model="keyword" placeholder="设备编码/名称" style="width:240px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <el-button v-if="auth.can('master:edit')" type="success" @click="open()">新增设备</el-button>
      <div class="flex-spacer" />
      <el-button size="small" @click="toggleAll(true)">展开全部区域</el-button>
      <el-button size="small" @click="toggleAll(false)">折叠全部区域</el-button>
    </div>

    <!-- 外层：按区域一行，默认全部折叠；点开显示该区域设备明细（参考任务详情主子表样式） -->
    <el-table
      :data="zoneGroups"
      row-key="zone"
      :expand-row-keys="expandedZones"
      border
      empty-text="暂无设备"
      @expand-change="onExpandChange"
    >
      <el-table-column type="expand" width="46">
        <template #default="{ row }">
          <div class="zone-detail">
            <el-table :data="row.devices" border size="small" class="device-table" @row-dblclick="(d) => openStats(d)">
              <el-table-column prop="code" label="设备编码" width="120" />
              <el-table-column prop="name" label="设备名称" min-width="130" />
              <el-table-column prop="model" label="型号" width="110" />
              <el-table-column prop="location" label="安装位置" min-width="130" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row: d }">
                  <el-tag :type="d.status === 'RUNNING' ? 'success' : 'info'" effect="plain">{{ statusLabel(d.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row: d }">
                  <el-button link type="primary" @click="openStats(d)">详情</el-button>
                  <el-button v-if="auth.can('master:edit')" link @click="open(d)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="区域" min-width="180">
        <template #default="{ row }">
          <span class="zone-name">{{ row.zone }}</span>
        </template>
      </el-table-column>
      <el-table-column label="设备数" width="110" align="center">
        <template #default="{ row }">{{ row.count }}</template>
      </el-table-column>
      <el-table-column label="在用 / 停用" width="140" align="center">
        <template #default="{ row }">
          <span class="zone-running">{{ row.running }}</span>
          <span class="zone-sep">/</span>
          <span :class="{ 'zone-stopped': row.count - row.running > 0 }">{{ row.count - row.running }}</span>
        </template>
      </el-table-column>
      <el-table-column label="型号" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.models }}</template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id?'编辑设备':'新增设备'" :width="isMobile ? '95%' : '860px'" top="4vh" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px" @submit.prevent>
        <el-tabs v-model="formTab">
          <el-tab-pane label="基本信息" name="basic">
            <div class="form-grid">
              <el-form-item label="设备编码" prop="code"><el-input v-model="form.code" maxlength="64" placeholder="如 AGV01" /></el-form-item>
              <el-form-item label="设备名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item>
              <el-form-item label="区域">
                <el-select v-model="form.zone" filterable clearable placeholder="数据字典-设备区域里维护" style="width:100%">
                  <el-option v-for="z in zoneOptions" :key="z.itemCode" :value="z.itemCode" :label="z.itemLabel" />
                </el-select>
              </el-form-item>
              <el-form-item label="型号"><el-input v-model="form.model" maxlength="128" /></el-form-item>
              <el-form-item label="安装位置"><el-input v-model="form.location" maxlength="128" /></el-form-item>
              <el-form-item label="状态">
                <el-select v-model="form.status" style="width:100%">
                  <el-option v-for="x in statusOptions" :key="x.id" :value="x.itemCode" :label="x.itemLabel" />
                </el-select>
              </el-form-item>
              <el-form-item label="制造商" class="full"><el-input v-model="form.manufacturer" maxlength="128" /></el-form-item>
              <el-form-item label="备注" class="full"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" show-word-limit /></el-form-item>
            </div>
          </el-tab-pane>

          <el-tab-pane name="mapping">
            <template #label>
              配置映射<span v-if="form.id" class="tab-badge">{{ (form.extRefs || []).length }}</span>
            </template>
            <div class="mapping-pane">
              <div class="mapping-toolbar">
                <span class="parts-hint">这台设备从哪些数据源取运行数据；每行勾选要取的指标，同一指标配了多行会合计</span>
                <el-button v-if="auth.can('master:edit')" type="primary" size="small" @click="addRule">+ 增加映射</el-button>
              </div>
              <div class="ext-ref-editor">
                <div v-for="(r, i) in form.extRefs" :key="i" class="ext-ref-row">
                  <span class="ext-ref-idx">{{ i + 1 }}</span>
                  <el-select v-model="r.sourceSys" style="width:96px">
                    <el-option v-for="s in extSources" :key="s" :value="s" :label="s" />
                  </el-select>
                  <el-input v-model="r.extCode" placeholder="外部编码，如 STA002" maxlength="64" style="width:180px" />
                  <el-checkbox v-model="r.takeHours">时长</el-checkbox>
                  <el-checkbox v-model="r.takeTasks">任务数</el-checkbox>
                  <el-checkbox v-model="r.takeQty">件数</el-checkbox>
                  <el-button link type="danger" @click="removeRule(i)">删除</el-button>
                </div>
                <el-empty v-if="!(form.extRefs || []).length" description="未配置映射——这台设备将不取任何运行数据" :image-size="80">
                  <el-button type="primary" size="small" @click="addRule">+ 增加映射</el-button>
                </el-empty>
                <div class="ext-ref-hint">
                  每行 = 某个数据源里的一台设备（选 DT / WMS / WCS，再填它在那套系统里的编码）；
                  勾选要从它取哪几项数据（可多选），同一指标配了多行会合计。<br>
                  <b>不配置则这台设备不取任何运行数据</b>。双向托盘线把另一端站台也加一行即可，运行数据自动按两端口径合计。
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane name="parts">
            <template #label>
              关联备件<span v-if="form.id" class="tab-badge">{{ partsTotal }}</span>
            </template>
            <template v-if="form.id">
              <div class="parts-toolbar">
                <span class="parts-hint">这台设备上已关联的备件，单机数量与更换周期用于备件寿命与补货点计算</span>
                <el-button v-if="auth.can('master:edit')" type="primary" size="small" @click="openBind()">关联备件</el-button>
              </div>
              <el-table v-loading="partsLoading" :data="partsRows" border size="small" empty-text="尚未关联备件">
                <el-table-column prop="sku" label="SKU" min-width="110" />
                <el-table-column prop="productName" label="备件" min-width="160" />
                <el-table-column prop="installedQty" label="单机数量" width="90" align="center" />
                <el-table-column prop="changeCycleDays" label="更换周期(天)" width="120" align="center" />
                <el-table-column label="操作" width="110">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openBind(row)">编辑</el-button>
                    <el-button v-if="auth.can('master:edit')" link type="danger" @click="unbind(row)">解绑</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="parts-pager">
                <el-pagination
                  v-model:current-page="partsPage.page"
                  v-model:page-size="partsPage.size"
                  :total="partsTotal"
                  :page-sizes="[10, 20, 50]"
                  layout="total, sizes, prev, pager, next"
                  small
                  @current-change="loadParts"
                  @size-change="onPartsSizeChange"
                />
              </div>
            </template>
            <el-empty v-else description="新增设备保存后，重新打开编辑即可关联备件" :image-size="90" />
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="visible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindVisible" :title="bind.partId?'修改关联备件':'关联设备备件'" width="500px">
      <el-form label-width="120px">
        <el-form-item label="备件">
          <el-select v-model="bind.productId" filterable :disabled="!!bind.partId" style="width:100%">
            <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.sku+' - '+p.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="单机数量"><el-input-number v-model="bind.installedQty" :min="1" /></el-form-item>
        <el-form-item label="更换周期(天)"><el-input-number v-model="bind.changeCycleDays" :min="0" /></el-form-item>
      </el-form>
      <div style="color:#909399;font-size:12px;line-height:1.75;padding-left:12px">更换周期 = 这台设备上该备件<b>计划多久换一次</b>（保养节奏）；备件档案里的「额定寿命」是<b>技术上能撑多久</b>，两者可以不同。<br>台账「建议更换日」取两者较早：寿命按实际消耗速度耗尽的那天，与「开始使用 + 更换周期」的那天。<br>补货点 = 备件档案的<b>安全库存</b> + ⌈备件档案的<b>采购周期</b> ÷ 这里的更换周期⌉ × 单机数量；采购周期、安全库存请到「基础资料 - 产品」维护，它们跟备件走、不跟设备走。更换周期留空或为 0 时，补货点只按安全库存算。</div>
      <template #footer>
        <el-button @click="bindVisible=false">取消</el-button>
        <el-button type="primary" @click="saveBind">保存</el-button>
      </template>
    </el-dialog>

    <!-- 设备详情：右侧抽屉，展示设备档案 + 外部系统同步来的运行数据 -->
    <el-drawer v-model="statsVisible" direction="rtl" :size="isMobile ? '92%' : '560px'" :title="statTitle" destroy-on-close>
      <div v-loading="statsLoading" class="eq-stat">
        <el-descriptions v-if="statDevice" :column="2" border size="small">
          <el-descriptions-item label="设备编码">{{ statDevice.code }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ statDevice.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ statDevice.name }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ zoneLabel(statDevice.zone) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="安装位置">{{ statDevice.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(statDevice.status) }}</el-descriptions-item>
          <el-descriptions-item label="取数映射" :span="2">
            <template v-if="(statDevice.extRules || []).length">
              <div v-for="(r, i) in statDevice.extRules" :key="i">{{ r.sourceSys }}/{{ r.extCode }}（{{ metricLabel(r) }}）</div>
            </template>
            <span v-else>未配置（不取运行数据）</span>
          </el-descriptions-item>
          <el-descriptions-item label="制造商" :span="2">{{ statDevice.manufacturer || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="eq-stat-bar">
          <span class="eq-stat-title">运行数据</span>
          <el-button-group size="small">
            <el-button :type="statDays === 7 ? 'primary' : 'default'" @click="setDays(7)">近7天</el-button>
            <el-button :type="statDays === 30 ? 'primary' : 'default'" @click="setDays(30)">近30天</el-button>
            <el-button :type="statDays === 90 ? 'primary' : 'default'" @click="setDays(90)">近90天</el-button>
          </el-button-group>
        </div>
        <div class="eq-stat-cards">
          <div class="eq-stat-card"><span>运行时长</span><b>{{ totals.hours }}</b><i>小时</i></div>
          <div class="eq-stat-card"><span>任务数</span><b>{{ totals.tasks }}</b></div>
          <div class="eq-stat-card"><span>件数</span><b>{{ totals.qty }}</b></div>
        </div>
        <el-table :data="statRows" size="small" border max-height="380" empty-text="暂无运行数据">
          <el-table-column prop="statDate" label="日期" width="98" />
          <el-table-column prop="sourceSys" label="来源" width="64" />
          <el-table-column prop="deviceType" label="类型" width="58" />
          <el-table-column label="时长(h)" width="74" align="right"><template #default="{row}">{{ row.runningHours ?? '-' }}</template></el-table-column>
          <el-table-column label="任务数" width="74" align="right"><template #default="{row}">{{ row.taskCount ?? '-' }}</template></el-table-column>
          <el-table-column label="件数" min-width="70" align="right"><template #default="{row}">{{ row.totalQty ?? '-' }}</template></el-table-column>
        </el-table>
        <div class="eq-stat-bar">
          <span class="eq-stat-title">关联备件</span>
        </div>
        <el-table v-loading="statPartsLoading" :data="statParts" size="small" border max-height="220" empty-text="尚未关联备件">
          <el-table-column prop="sku" label="SKU" width="110" />
          <el-table-column prop="productName" label="备件" min-width="140" show-overflow-tooltip />
          <el-table-column prop="installedQty" label="单机数量" width="80" align="center" />
          <el-table-column prop="changeCycleDays" label="更换周期(天)" width="106" align="center">
            <template #default="{ row }">{{ row.changeCycleDays ?? '-' }}</template>
          </el-table-column>
        </el-table>
        <p class="eq-stat-tip">数据来自外部系统（数字孪生 / WMS / WCS）每日同步。整表为空通常是该设备编码在源系统没匹配到，或源库当天还没跑批。</p>
      </div>
    </el-drawer>

    <!-- 手动补拉设备运行数据（从接口管理页迁移至此） -->
    <el-dialog v-model="sync.visible" title="补拉设备运行数据" width="620px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:14px"
        title="按天从外部系统（数字孪生 DT / WMS / WCS）拉取设备运行时长、任务数、件数。重复拉取是覆盖更新，不会产生重复数据。"
      />
      <div class="sync-filter">
        <el-date-picker
          v-model="sync.range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="isFuture"
          style="width: 280px"
        />
        <el-button size="small" @click="quickRange(1)">昨天</el-button>
        <el-button size="small" @click="quickRange(7)">最近 7 天</el-button>
        <el-button size="small" @click="quickRange(30)">最近 30 天</el-button>
      </div>
      <div v-if="sync.results.length" class="sync-result">
        <div class="sync-result-title">
          本次补拉结果（共 {{ sync.results.length }} 天，{{ totalPulled }} 条）
        </div>
        <el-table :data="sync.results" size="small" border max-height="300">
          <el-table-column prop="statDate" label="统计日期" width="110" />
          <el-table-column label="成功源" width="80" align="center">
            <template #default="{ row }">{{ row.successSources }}</template>
          </el-table-column>
          <el-table-column label="失败源" width="80" align="center">
            <template #default="{ row }">
              <span :class="{ 'sync-fail': row.failedSources > 0 }">{{ row.failedSources }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="rows" label="条数" width="70" align="center" />
          <el-table-column label="明细" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">{{ (row.details || []).join('；') }}</template>
          </el-table-column>
        </el-table>
        <div class="sync-tip">
          0 条通常是源库当天还没跑批，不是同步失败；失败源请到「接口管理 - 发送报文」看对应日期的错误信息。
        </div>
      </div>
      <template #footer>
        <el-button @click="sync.visible = false">关闭</el-button>
        <el-button type="primary" :loading="sync.loading" @click="submitSync">开始补拉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import * as api from '@/api/equipment'
import * as productsApi from '@/api/products'
import * as dictApi from '@/api/dictionaries'
import * as deviceStatsApi from '@/api/deviceStats'
import { useAuthStore } from '@/stores/auth'
import { useMobile } from '@/composables/useMobile'

const auth = useAuthStore()
const keyword = ref('')
const rows = ref([])
const visible = ref(false)
const bindVisible = ref(false)
const products = ref([])
const statusOptions = ref([])
const form = ref({})
const bind = ref(emptyBind())
const expandedZones = ref([])    // 默认全部折叠

/* ---------- 编辑弹框（标签页：基本信息 / 关联备件，样式同备件主数据弹框） ---------- */
const formRef = ref()
const formTab = ref('basic')
const submitting = ref(false)
const rules = {
  code: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}

/* ---------- 编辑弹框内的关联备件（分页） ---------- */
const partsRows = ref([])
const partsTotal = ref(0)
const partsLoading = ref(false)
const partsPage = reactive({ page: 1, size: 10 })

async function loadParts() {
  if (!form.value.id) return
  partsLoading.value = true
  try {
    const r = await api.partsPage(form.value.id, { page: partsPage.page - 1, size: partsPage.size })
    partsRows.value = r?.content || []
    partsTotal.value = r?.totalElements || 0
  } finally {
    partsLoading.value = false
  }
}

function onPartsSizeChange() {
  partsPage.page = 1
  loadParts()
}

function emptyBind() { return { partId: null, productId: null, installedQty: 1, changeCycleDays: null } }

async function load() {
  rows.value = await api.list({ keyword: keyword.value })
}

/* ---------- 按区域分组（外层一行 = 一个区域） ---------- */
// 区域在库里存的是字典编码（EQUIPMENT_ZONE 的 itemCode），历史数据也可能直接存了中文名；
// 展示一律翻译成字典名称，查不到映射的原样显示，避免把 SC-1 这类编码直接亮给用户。
function zoneLabel(v) {
  if (!v) return ''
  const hit = zoneOptions.value.find(x => x.itemCode === v)
  return hit ? hit.itemLabel : v
}

const zoneGroups = computed(() => {
  const map = new Map()
  for (const r of rows.value) {
    const z = zoneLabel(r.zone) || '未分区'
    if (!map.has(z)) map.set(z, { zone: z, devices: [] })
    map.get(z).devices.push(r)
  }
  // 区域按数据字典 EQUIPMENT_ZONE 的 sort_order 排（字典里没有的区域/未分区垫底）；
  // 区域内设备按编码排（numeric 让 STA2 排在 STA10 前面）
  const sortOrderOf = (z) => {
    const hit = zoneOptions.value.find(x => (x.itemLabel || x.itemCode) === z)
    return hit ? (hit.sortOrder ?? Number.MAX_SAFE_INTEGER) : Number.MAX_SAFE_INTEGER
  }
  const byCode = (a, b) => String(a.code || '').localeCompare(String(b.code || ''), 'zh-Hans-CN', { numeric: true })
  return [...map.values()]
    .map(g => {
      const devices = g.devices.slice().sort(byCode)
      return {
        ...g,
        devices,
        count: devices.length,
        running: devices.filter(d => d.status === 'RUNNING').length,
        models: [...new Set(devices.map(d => d.model).filter(Boolean))].join('、')
      }
    })
    .sort((a, b) => sortOrderOf(a.zone) - sortOrderOf(b.zone) || a.zone.localeCompare(b.zone, 'zh-Hans-CN'))
})

// 区域候选来自数据字典 EQUIPMENT_ZONE（系统管理 → 数据字典 里增删改，这里自动跟着变）
const zoneOptions = ref([])

async function loadZoneDict() {
  if (!zoneOptions.value.length) zoneOptions.value = await dictApi.options('EQUIPMENT_ZONE')
}

/* ---------- 取数映射（源 + 外部编码 + 要取的指标） ---------- */
// 取数只认配置：配了几行就按几行取，同一指标多行命中时合计；没配则该设备不取运行数据。
// 每行必须至少勾一个指标，保存前在前端先拦一道，后端也有同样的校验。
const extSources = ['WCS', 'WMS', 'DT']
function emptyRule() { return { sourceSys: 'WCS', extCode: '', takeHours: true, takeTasks: true, takeQty: true } }
function addRule() { form.value.extRefs = [...(form.value.extRefs || []), emptyRule()] }
function removeRule(i) { form.value.extRefs = (form.value.extRefs || []).filter((_, x) => x !== i) }
function metricLabel(r) {
  const m = []
  if (r.takeHours) m.push('时长')
  if (r.takeTasks) m.push('任务数')
  if (r.takeQty) m.push('件数')
  return m.join('、') || '无'
}

/** 保存前的规则校验：编码必填、每行至少一个指标、同源同编码不能重复 */
function validateRules() {
  const seen = new Set()
  for (const r of form.value.extRefs || []) {
    const code = String(r.extCode || '').trim()
    if (!code) { ElMessage.warning('取数映射里有未填编码的行，请填写或删除'); return false }
    if (!r.takeHours && !r.takeTasks && !r.takeQty) {
      ElMessage.warning(`映射 ${r.sourceSys}/${code} 至少要勾选一个指标`); return false
    }
    const key = r.sourceSys + '|' + code.toUpperCase()
    if (seen.has(key)) { ElMessage.warning(`映射 ${r.sourceSys}/${code} 重复了`); return false }
    seen.add(key)
  }
  return true
}

function onExpandChange(row, expandedList) {
  expandedZones.value = expandedList.map(r => r.zone)
}

function toggleAll(open) {
  expandedZones.value = open ? zoneGroups.value.map(g => g.zone) : []
}

function statusLabel(v) {
  const hit = statusOptions.value.find(x => x.itemCode === v)
  return hit ? hit.itemLabel : (v || '-')
}

/* ---------- 设备档案 CRUD / 关联备件 ---------- */
async function open(r) {
  form.value = r
    ? {
        ...r,
        // 规则行带出时补齐指标开关（老数据没有这三个字段按"全取"处理）
        extRefs: (r.extRefs || []).map(x => ({
          sourceSys: x.sourceSys || 'WCS',
          extCode: x.extCode || '',
          takeHours: x.takeHours !== false,
          takeTasks: x.takeTasks !== false,
          takeQty: x.takeQty !== false
        }))
      }
    : { status: 'RUNNING', extRefs: [] }
  formTab.value = 'basic'
  if (!statusOptions.value.length) statusOptions.value = await dictApi.options('EQUIPMENT_STATUS')
  await loadZoneDict()
  if (form.value.id) {
    // 历史数据可能直接存了中文名，编辑时尽量还原成字典编码，保存后逐步统一
    if (form.value.zone) {
      const inv = zoneOptions.value.find(x => x.itemLabel === form.value.zone)
      if (inv) form.value.zone = inv.itemCode
    }
    partsPage.page = 1; loadParts()   // 编辑时在「关联备件」页签展示
  }
  visible.value = true
}

async function save() {
  try { await formRef.value.validate() } catch { return }
  if (!validateRules()) return
  submitting.value = true
  try {
    form.value.id ? await api.update(form.value.id, form.value) : await api.create(form.value)
    ElMessage.success(form.value.id ? '设备已更新' : '设备已创建')
    visible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function openBind(row) {
  bind.value = row ? { partId: row.id, productId: row.productId, installedQty: row.installedQty, changeCycleDays: row.changeCycleDays } : emptyBind()
  if (!products.value.length) {
    const x = await productsApi.list({ page: 0, size: 500 })
    products.value = x.content || []
  }
  bindVisible.value = true
}

async function saveBind() {
  const d = bind.value
  if (d.partId) {
    await api.updatePart(form.value.id, d.partId, d)
  } else {
    if (!d.productId) { ElMessage.warning('请先选择备件'); return }
    await api.bindPart(form.value.id, d)
  }
  bindVisible.value = false
  loadParts()
  ElMessage.success('已保存')
}

async function unbind(row) {
  try {
    await ElMessageBox.confirm(`确定解除「${row.productName}」与当前设备的关联？已产生的备件寿命记录不受影响。`, '解除关联', { type: 'warning' })
  } catch { return }
  await api.unbindPart(form.value.id, row.id)
  loadParts()
  ElMessage.success('已解绑')
}

/* ---------- 设备详情抽屉 ---------- */
const { isMobile } = useMobile()
const statsVisible = ref(false), statsLoading = ref(false), statDevice = ref(null), statDays = ref(30), statRows = ref([])
const totals = ref({ hours: 0, tasks: 0, qty: 0 })
const statParts = ref([]), statPartsLoading = ref(false)
const statTitle = computed(() => statDevice.value ? `${statDevice.value.code} · ${statDevice.value.name}` : '设备运行数据')

async function openStats(row) {
  statDevice.value = row
  statDays.value = 30
  statsVisible.value = true
  statParts.value = []
  loadStatParts()
  await loadStats()
}

// 详情抽屉底部：这台设备关联的备件（不分页，一屏展示）
async function loadStatParts() {
  if (!statDevice.value?.id) return
  statPartsLoading.value = true
  try {
    const r = await api.partsPage(statDevice.value.id, { page: 0, size: 200 })
    statParts.value = r?.content || []
  } finally {
    statPartsLoading.value = false
  }
}

async function loadStats() {
  if (!statDevice.value) return
  statsLoading.value = true
  try {
    const r = await api.stats(statDevice.value.id, statDays.value)
    statRows.value = r?.rows || []
    totals.value = r?.totals || { hours: 0, tasks: 0, qty: 0 }
    // /stats 响应里带按映射规则解析好的 extRules；列表行只有 extRefs（编辑用），
    // 不合并的话抽屉永远显示"未配置"
    statDevice.value = { ...statDevice.value, extRules: r?.extRules || [] }
  } finally {
    statsLoading.value = false
  }
}

function setDays(d) {
  if (statDays.value === d) return
  statDays.value = d
  loadStats()
}

/* ---------- 手动补拉设备运行数据（从接口管理页迁移至此） ---------- */
const sync = reactive({ visible: false, loading: false, range: defaultSyncRange(7), results: [] })
const totalPulled = computed(() => sync.results.reduce((n, r) => n + (r.rows || 0), 0))

function pad2(n) { return n < 10 ? '0' + n : '' + n }
function fmtDate(d) { return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}` }

// 默认「到昨天为止的最近 N 天」：当天数据不完整，不纳入默认区间
function defaultSyncRange(days) {
  const to = new Date()
  to.setDate(to.getDate() - 1)
  const from = new Date(to)
  from.setDate(from.getDate() - (days - 1))
  return [fmtDate(from), fmtDate(to)]
}

// 今天及以后不可选：源库当天尚未跑批
function isFuture(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() >= today.getTime()
}

function openSync() {
  sync.results = []
  sync.range = defaultSyncRange(7)
  sync.visible = true
}

function quickRange(days) { sync.range = defaultSyncRange(days) }

async function submitSync() {
  if (!sync.range || sync.range.length !== 2) return ElMessage.warning('请选择日期区间')
  sync.loading = true
  try {
    const results = await deviceStatsApi.syncRange({ from: sync.range[0], to: sync.range[1] })
    sync.results = results || []
    ElMessage.success(`补拉完成，共 ${totalPulled.value} 条`)
  } catch {
    // 业务失败拦截器已统一提示，这里不再重复弹
  } finally {
    sync.loading = false
  }
}

onMounted(() => { load(); loadZoneDict() })
</script>

<style scoped lang="scss">
.flex-spacer { flex: 1; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 2px 18px; }
.form-grid .full { grid-column: 1 / -1; }
.tab-badge {
  display: inline-block; margin-left: 6px; padding: 0 6px; border-radius: 8px;
  background: #eaf1ff; color: #1e54d8; font-size: 11px; line-height: 16px;
}
.parts-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.mapping-pane { padding: 4px 2px; }
.mapping-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.ext-ref-editor { width: 100%; padding: 12px 14px; background: #f8fafc; border: 1px solid #e4e9f2; border-radius: 6px; }
.ext-ref-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 6px 0; border-bottom: 1px dashed #e8edf4; }
.ext-ref-row:last-of-type { border-bottom: none; }
.ext-ref-idx {
  width: 20px; height: 20px; border-radius: 50%; background: #eaf1ff; color: #1e54d8;
  font-size: 12px; line-height: 20px; text-align: center; flex-shrink: 0;
}
.ext-ref-hint { margin-top: 10px; font-size: 12px; line-height: 1.7; color: #909399; }
.parts-hint { font-size: 12px; color: #94a3b8; line-height: 1.5; }
.parts-pager { display: flex; justify-content: flex-end; margin-top: 10px; }
@media (max-width: 767px) {
  .form-grid { grid-template-columns: 1fr; }
  .form-grid .full { grid-column: auto; }
  :deep(.el-dialog__body) { padding: 10px 14px; }
}
.zone-name { font-weight: 700; color: #10233f; font-size: 14px; }
.zone-detail {
  padding: 6px 12px 10px 40px;
  background: #fafcff;
  // 双击行 = 打开设备详情，给个手型提示
  .device-table :deep(.el-table__row) { cursor: pointer; }
}
.zone-running { color: #22a35d; font-weight: 700; }
.zone-sep { color: #cbd5e1; margin: 0 4px; }
.zone-stopped { color: #e34d59; font-weight: 700; }

.sync-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  .el-button { margin-left: 0; }
}
.sync-result { margin-top: 16px; }
.sync-result-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 8px;
}
.sync-fail { color: #e34d59; font-weight: 700; }
.sync-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.6;
}

.eq-stat-bar{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:18px 0 10px}
.eq-stat-title{font-size:14px;font-weight:700;color:#10233f}
.eq-stat-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:12px}
.eq-stat-card{
  display:flex;flex-direction:column;gap:2px;padding:10px 12px;border-radius:10px;
  background:#f6f9ff;border:1px solid #e8eefb;
  span{font-size:12px;color:#94a3b8}
  b{font-size:20px;line-height:1.2;color:#1e54d8}
  i{font-style:normal;font-size:11px;color:#94a3b8}
}
.eq-stat-tip{margin:10px 0 0;font-size:12px;line-height:1.6;color:#94a3b8}
</style>
