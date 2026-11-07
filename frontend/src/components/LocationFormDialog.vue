<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑库位' : '新增库位'"
    width="720px"
    @closed="onClosed"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      @submit.prevent
    >
      <el-form-item label="库位编码" prop="code">
        <el-input
          v-model="form.code"
          placeholder="请输入库位编码（如 A-01-01）"
          :disabled="isEdit"
          maxlength="32"
          clearable
        />
      </el-form-item>
      <el-form-item label="仓库名称" prop="warehouseName">
        <el-select v-model="form.warehouseName" filterable style="width:100%"><el-option v-for="x in warehouses" :key="x.id" :label="x.itemLabel" :value="x.itemLabel"/></el-select>
      </el-form-item>
      <el-form-item label="存储方式" prop="storageMode">
        <el-radio-group v-model="form.storageMode">
          <el-radio-button value="RACK">货架位</el-radio-button>
          <el-radio-button value="FLOOR">地面位</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-row v-if="form.storageMode === 'RACK'" :gutter="12">
        <el-col :xs="24" :sm="8"><el-form-item label="第几排" prop="rackNo"><el-input-number v-model="form.rackNo" :min="1" :max="99" /></el-form-item></el-col>
        <el-col :xs="24" :sm="8"><el-form-item label="第几层" prop="levelNo"><el-input-number v-model="form.levelNo" :min="1" :max="99" /></el-form-item></el-col>
        <el-col :xs="24" :sm="8"><el-form-item label="第几个" prop="positionNo"><el-input-number v-model="form.positionNo" :min="1" :max="999" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="所属区域" prop="area">
        <el-select v-model="form.area" filterable clearable style="width:100%"><el-option v-for="x in areas" :key="x.id" :label="x.itemLabel" :value="x.itemLabel"/></el-select>
      </el-form-item>
      <el-form-item label="库位类型" prop="type">
        <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
          <el-option v-for="x in locationTypes" :key="x.id" :label="x.itemLabel" :value="x.itemLabel" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用状态">
        <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
      </el-form-item>
      <el-form-item label="电子标签">
        <template v-if="props.row?.esTagName">
          <el-tag type="warning" effect="plain" size="large">{{ props.row.esTagName }}</el-tag>
          <div style="font-size:12px;color:#909399;margin-top:4px">MAC：{{ props.row.deviceNo }}</div>
          <div style="font-size:12px;color:#909399;margin-top:2px">若需绑定/更换标签，请前往「电子标签管理」页面操作</div>
        </template>
        <template v-else-if="props.row?.deviceNo">
          <div style="color:#909399;font-size:13px">
            MAC：{{ props.row.deviceNo }}
            <div style="font-size:12px;color:#c0c4cc;margin-top:2px">标签未登记，请前往「电子标签管理」页面绑定</div>
          </div>
        </template>
        <span v-else style="color:#c0c4cc">未绑定（前往「电子标签管理」绑定）</span>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          placeholder="选填"
          maxlength="128"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as locationsApi from '@/api/locations'
import * as dictApi from '@/api/dictionaries'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  row: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const isEdit = computed(() => !!props.row?.id)

const formRef = ref(null)
const submitting = ref(false)
const warehouses = ref([]), areas = ref([]), locationTypes = ref([])

const emptyForm = () => ({
  id: null,
  code: '',
  warehouseName: '备品备件间',
  storageMode: 'RACK',
  rackNo: 1,
  levelNo: 1,
  positionNo: 1,
  enabled: true,
  area: '',
  type: '',
  deviceNo: '',
  remark: ''
})
const form = reactive(emptyForm())

const rules = {
  code: [{ required: true, message: '请输入库位编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择库位类型', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  async (v) => {
    if (v) {
      // 先清空再回填：字典加载放在后面。
      // 原来的顺序是"先 await 字典、再 Object.assign"，字典一旦失败，
      // 回填代码整段被跳过，弹窗里留着的还是上一个库位的数据，
      // 用户点保存就把 A 库位的信息写到 B 库位上了。
      Object.assign(form, emptyForm())
      if (props.row) {
        form.id = props.row.id
        form.code = props.row.code || ''
        form.warehouseName = props.row.warehouseName || '备品备件间'
        form.storageMode = props.row.storageMode || 'RACK'
        form.rackNo = props.row.rackNo || 1
        form.levelNo = props.row.levelNo || 1
        form.positionNo = props.row.positionNo || 1
        form.enabled = props.row.enabled !== false
        form.area = props.row.area || ''
        form.type = props.row.type || ''
        form.deviceNo = props.row.deviceNo || ''
        form.remark = props.row.remark || ''
      }
      if (!warehouses.value.length) {
        try {
          [warehouses.value, areas.value, locationTypes.value] = await Promise.all([
            dictApi.options('WAREHOUSE'),
            dictApi.options('LOCATION_AREA'),
            dictApi.options('LOCATION_TYPE')
          ])
        } catch {
          warehouses.value = []
          areas.value = []
          locationTypes.value = []
          ElMessage.warning('库位相关字典加载失败，库区/类型下拉可能为空')
        }
      }
    }
  }
)

function onClosed() {
  formRef.value?.resetFields()
  Object.assign(form, emptyForm())
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = {
      code: form.code.trim(),
      warehouseName: form.warehouseName?.trim() || '备品备件间',
      storageMode: form.storageMode,
      rackNo: form.storageMode === 'RACK' ? form.rackNo : null,
      levelNo: form.storageMode === 'RACK' ? form.levelNo : null,
      positionNo: form.storageMode === 'RACK' ? form.positionNo : null,
      enabled: form.enabled,
      area: form.area?.trim() || null,
      type: form.type || null,
      // deviceNo 不再从库位表单提交，绑定/解绑统一在「电子标签管理」页面通过 EsTagService 双向对齐
      remark: form.remark?.trim() || null
    }
    if (isEdit.value) {
      await locationsApi.update(form.id, payload)
      ElMessage.success('编辑库位成功')
    } else {
      await locationsApi.create(payload)
      ElMessage.success('新增库位成功')
    }
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
