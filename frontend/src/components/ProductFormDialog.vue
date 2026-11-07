<template>
  <el-dialog v-model="visible" :title="isEdit?'编辑备件主数据':'新增备件主数据'" :width="dialogWidth" top="4vh" destroy-on-close @closed="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="112px" @submit.prevent>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <div class="form-grid">
            <el-form-item label="SKU编码" prop="sku"><el-input v-model="form.sku" :disabled="isEdit" maxlength="64" placeholder="如 SP-001" /></el-form-item>
            <el-form-item label="备件名称" prop="name"><el-input v-model="form.name" maxlength="128" /></el-form-item>
            <el-form-item label="备件分类"><el-select v-model="form.category" filterable clearable style="width:100%"><el-option v-for="x in categoryOptions" :key="x.id" :label="x.itemLabel" :value="x.itemLabel"/></el-select></el-form-item>
            <el-form-item label="计量单位"><el-select v-model="form.unit" filterable clearable style="width:100%"><el-option v-for="x in unitOptions" :key="x.id" :label="x.itemLabel" :value="x.itemLabel"/></el-select></el-form-item>
            <el-form-item label="规格"><el-input v-model="form.spec" maxlength="255" /></el-form-item>
            <el-form-item label="型号"><el-input v-model="form.model" maxlength="128" /></el-form-item>
            <el-form-item label="品牌"><el-input v-model="form.brand" maxlength="64" /></el-form-item>
            <el-form-item label="图号/物料号"><el-input v-model="form.drawingNo" maxlength="64" /></el-form-item>
            <el-form-item label="生产厂家" class="full"><el-input v-model="form.manufacturer" maxlength="128" /></el-form-item>
            <el-form-item label="备注" class="full"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
          </div>
        </el-tab-pane>
        <el-tab-pane label="管理属性" name="management">
          <div class="form-grid">
            <el-form-item label="关键程度"><el-select v-model="form.criticality" style="width:100%"><el-option label="一般" value="LOW"/><el-option label="普通" value="NORMAL"/><el-option label="重要" value="HIGH"/><el-option label="关键" value="CRITICAL"/></el-select></el-form-item>
            <el-form-item label="默认保质期"><el-input-number v-model="form.defaultShelfLifeDays" :min="0" :max="36500" controls-position="right"/><span class="suffix">天</span></el-form-item>
            <el-form-item label="最高库存" prop="maxStock"><el-input-number v-model="form.maxStock" :min="0" controls-position="right" /></el-form-item>
            <el-form-item label="安全库存"><el-input-number v-model="form.safetyStock" :min="0" controls-position="right" /></el-form-item>
            <el-form-item label="参考单价"><el-input-number v-model="form.referenceUnitPrice" :min="0" :precision="2" controls-position="right" /><span class="suffix">元</span></el-form-item>
            <el-form-item label="采购周期"><el-input-number v-model="form.procurementLeadDays" :min="0" :max="3650" controls-position="right"/><span class="suffix">天</span></el-form-item>
            <el-form-item label="寿命计量" class="full">
              <div class="life-rules">
                <div v-for="(r,i) in form.lifeMetricRules" :key="i" class="life-rule">
                  <el-select v-model="r.metric" style="width:150px" @change="onMetricChange(i)">
                    <el-option v-if="i===0" label="不计算" value="NONE" />
                    <el-option v-for="m in metricOptions(i)" :key="m.value" :label="m.label" :value="m.value" />
                  </el-select>
                  <template v-if="r.metric!=='NONE'">
                    <el-input-number v-model="r.rated" :min="0.01" :precision="2" controls-position="right" style="width:160px" />
                    <span class="suffix">{{ metricUnit(r.metric) }}</span>
                    <el-tag v-if="i===0" size="small" type="primary" effect="plain">主口径</el-tag>
                    <el-tag v-else size="small" type="info" effect="plain">辅助</el-tag>
                  </template>
                  <el-button v-if="i>0" link type="danger" @click="form.lifeMetricRules.splice(i,1)">删除</el-button>
                </div>
                <div class="life-actions">
                  <el-button v-if="form.lifeMetricRules[0]?.metric!=='NONE' && metricOptions(form.lifeMetricRules.length).length"
                             link type="primary" @click="addRule">+ 增加辅助口径</el-button>
                  <span v-if="form.lifeMetricRules.length>1" class="life-hint">多个口径同时参与计算，系统取"最先到期"的那个口径判定寿命状态</span>
                </div>
              </div>
            </el-form-item>
            <el-form-item v-if="lifeEnabled" label="寿命预警提前"><el-input-number v-model="form.lifeWarningDays" :min="0" :max="180" controls-position="right"/><span class="suffix">天</span></el-form-item>
            <el-form-item v-if="lifeEnabled" class="full"><el-alert title="填 0 表示跟随系统配置里的统一值；只对个别备件需要不同提前量时才填具体天数。" type="info" :closable="false" /></el-form-item>
            <el-form-item v-if="lifeEnabled" class="full"><el-alert title="领用单确认出库时自动建立使用台账，并以该时刻作为开始使用时间；多口径规则会一并快照到台账。" type="info" :closable="false" show-icon /></el-form-item>
            <el-form-item label="易损件"><el-switch v-model="form.vulnerable" /></el-form-item>
            <el-form-item label="批次管理"><el-switch v-model="form.batchManaged" /></el-form-item>
            <el-form-item label="序列号管理"><el-switch v-model="form.serialManaged" /></el-form-item>
            <el-form-item label="启用状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-form>
    <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useMobile } from '@/composables/useMobile'
import * as api from '@/api/products'
import * as dictApi from '@/api/dictionaries'
const props=defineProps({modelValue:Boolean,row:{type:Object,default:null}}),emit=defineEmits(['update:modelValue','success'])
const {isMobile}=useMobile(),dialogWidth=computed(()=>isMobile.value?'95%':'860px'),visible=computed({get:()=>props.modelValue,set:v=>emit('update:modelValue',v)}),isEdit=computed(()=>!!props.row?.id)
const formRef=ref(),submitting=ref(false),activeTab=ref('basic')
const categoryOptions=ref([]),unitOptions=ref([])
const empty=()=>({id:null,sku:'',name:'',spec:'',unit:'',category:'',brand:'',model:'',drawingNo:'',manufacturer:'',criticality:'NORMAL',vulnerable:false,batchManaged:true,serialManaged:false,defaultShelfLifeDays:null,minStock:0,maxStock:null,safetyStock:0,procurementLeadDays:0,referenceUnitPrice:null,lifeMetric:'NONE',ratedLifeValue:null,lifeWarningDays:0,lifeMetricRules:[{metric:'NONE',rated:null}],enabled:true,remark:''})
const form=reactive(empty())
const ALL_METRICS=[{value:'HOURS',label:'设备运行小时',unit:'小时'},{value:'TASKS',label:'设备任务次数',unit:'次'},{value:'QTY',label:'设备处理件数',unit:'件'},{value:'DAYS',label:'自然使用天数',unit:'天'}]
const metricUnit=m=>ALL_METRICS.find(x=>x.value===m)?.unit||''
/** 当前行可选的口径：排除已经用过的，避免同一口径配两条 */
const metricOptions=i=>{const used=form.lifeMetricRules.filter((r,j)=>j!==i&&r.metric!=='NONE').map(r=>r.metric);return ALL_METRICS.filter(m=>!used.includes(m.value))}
const lifeEnabled=computed(()=>form.lifeMetricRules.some(r=>r.metric&&r.metric!=='NONE'))
function addRule(){const next=metricOptions(form.lifeMetricRules.length)[0];if(next)form.lifeMetricRules.push({metric:next.value,rated:null})}
function onMetricChange(i){if(form.lifeMetricRules[i].metric==='NONE')form.lifeMetricRules.splice(i+1)}
const rules={sku:[{required:true,message:'请输入SKU编码',trigger:'blur'}],name:[{required:true,message:'请输入备件名称',trigger:'blur'}],maxStock:[{validator:(_r,v,done)=>{if(v!=null&&(v<form.minStock||v<form.safetyStock))done(new Error('最高库存不能小于最低库存或安全库存'));else done()},trigger:'change'}]}
/** 行数据 → 规则列表：优先用新字段，老数据按单口径折算 */
function normalizeRules(row){
  const list=Array.isArray(row?.lifeMetricRules)?row.lifeMetricRules:[]
  const mapped=list.filter(x=>x&&x.metric&&x.metric!=='NONE'&&Number(x.rated)>0).map(x=>({metric:x.metric,rated:Number(x.rated)}))
  if(mapped.length)return mapped
  if(row?.lifeMetric&&row.lifeMetric!=='NONE'&&Number(row.ratedLifeValue)>0)return [{metric:row.lifeMetric,rated:Number(row.ratedLifeValue)}]
  return [{metric:'NONE',rated:null}]
}
watch(()=>props.modelValue,async v=>{if(!v)return;Object.assign(form,empty(),props.row||{});form.lifeMetricRules=normalizeRules(props.row);activeTab.value='basic';if(!categoryOptions.value.length){
  // 字典加载失败不能让异常冒出去：一旦冒出去，watch 回调中断，
  // 用户看到的就是一个"填了一半、分不清是哪个备件"的弹窗，保存下去直接写坏主数据
  try{[categoryOptions.value,unitOptions.value]=await Promise.all([dictApi.options('PRODUCT_CATEGORY'),dictApi.options('UNIT')])}
  catch{categoryOptions.value=[];unitOptions.value=[];ElMessage.warning('分类/单位字典加载失败，可先保存其它字段')}
}})
function reset(){formRef.value?.resetFields();Object.assign(form,empty())}
function text(v){return typeof v==='string'?(v.trim()||null):v}
async function submit(){
  try{await formRef.value.validate()}catch{return}
  const active=form.lifeMetricRules.filter(r=>r.metric&&r.metric!=='NONE')
  for(const r of active){if(!(Number(r.rated)>0)){ElMessage.warning('额定寿命必须大于 0');return}}
  submitting.value=true
  try{
    const payload={...form}
    for(const key of ['spec','unit','category','brand','model','drawingNo','manufacturer','remark'])payload[key]=text(payload[key])
    payload.lifeMetricRules=active.map(r=>({metric:r.metric,rated:Number(r.rated)}))
    payload.lifeMetric=active.length?active[0].metric:'NONE'
    payload.ratedLifeValue=active.length?Number(active[0].rated):null
    if(isEdit.value)await api.update(form.id,payload);else await api.create(payload)
    ElMessage.success(isEdit.value?'备件主数据已更新':'备件主数据已创建');visible.value=false;emit('success')
  }finally{submitting.value=false}
}
</script>

<style scoped lang="scss">
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px 18px}.full{grid-column:1/-1}.suffix{margin-left:8px;color:#909399}
.life-rules{display:flex;flex-direction:column;gap:8px;width:100%}
.life-rule{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.life-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.life-hint{color:#909399;font-size:12px;line-height:1.5}
@media(max-width:767px){.form-grid{grid-template-columns:1fr}:deep(.el-dialog__body){padding:10px 14px}.full{grid-column:auto}.life-rule :deep(.el-select),.life-rule :deep(.el-input-number){width:130px!important}}
</style>
