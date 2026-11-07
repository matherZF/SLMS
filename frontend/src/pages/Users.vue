<template>
  <div class="page-container">
    <PageHeader>用户管理</PageHeader>
    <div class="filter-bar">
      <el-input v-model="filters.username" placeholder="用户名" clearable style="width:200px" @keyup.enter="loadData" />
      <el-input v-model="filters.displayName" placeholder="姓名" clearable style="width:200px" @keyup.enter="loadData" />
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="success" @click="openDialog()">+ 新增用户</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe style="width:100%">
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="displayName" label="姓名" min-width="120" />
      <el-table-column label="角色" min-width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="plain">
            {{ row.roleName || row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'warning'" effect="plain">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" :formatter="formatTime" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" :disabled="row.username === authStore.user?.username" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      v-model:current-page="page.page"
      v-model:page-size="page.size"
      :page-sizes="[10, 20, 50]"
      @size-change="loadData"
      @current-change="loadData"
      style="margin-top:16px;justify-content:flex-end;display:flex"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editRow ? '编辑用户' : '新增用户'" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item v-if="!editRow" label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-64字符" clearable />
        </el-form-item>
        <el-form-item v-else label="用户名">
          <el-input :model-value="editRow.username" disabled />
        </el-form-item>
        <el-form-item v-if="!editRow" label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6-64字符" show-password />
        </el-form-item>
        <el-form-item v-else label="重置密码">
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="displayName">
          <el-input v-model="form.displayName" placeholder="显示姓名" clearable />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="role in roles" :key="role.code" :label="role.name" :value="role.code" :disabled="!role.enabled" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editRow" label="状态" prop="enabled">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import usersApi from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'
import rolesApi from '@/api/roles'

const authStore = useAuthStore()
const loading = ref(false)
const list = ref([])
const roles = ref([])
const total = ref(0)
const page = reactive({ page: 1, size: 20 })
const filters = reactive({ username: '', displayName: '' })
const dialogVisible = ref(false)
const submitting = ref(false)
const editRow = ref(null)
const formRef = ref(null)

const form = reactive({
  username: '',
  password: '',
  displayName: '',
  role: 'WAREHOUSE',
  enabled: true
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '长度3-64字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度6-64字符', trigger: 'blur' }
  ],
  displayName: [{ max: 64, message: '不超过64字符', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function formatTime(_row, _col, val) {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN', { hour12: false })
}

async function loadData() {
  loading.value = true
  try {
    const res = await usersApi.list({
      username: filters.username,
      displayName: filters.displayName,
      page: page.page - 1,
      size: page.size
    })
    list.value = res.content
    total.value = res.totalElements
  } catch { /* axios 拦截器已 toast */ }
  finally { loading.value = false }
}

function resetFilters() {
  filters.username = ''
  filters.displayName = ''
  page.page = 1
  loadData()
}

function openDialog(row) {
  editRow.value = row || null
  if (row) {
    form.username = row.username
    form.password = ''
    form.displayName = row.displayName || ''
    form.role = row.role
    form.enabled = row.enabled
  } else {
    Object.assign(form, { username: '', password: '', displayName: '', role: 'WAREHOUSE', enabled: true })
  }
  dialogVisible.value = true
}

function resetForm() {
  editRow.value = null
  formRef.value?.resetFields()
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (editRow.value) {
        // 编辑：password 为空则不传
        const payload = {
          displayName: form.displayName || null,
          role: form.role,
          enabled: form.enabled,
          password: form.password || null
        }
        await usersApi.update(editRow.value.id, payload)
        ElMessage.success('用户信息已更新')
      } else {
        // 新增
        await usersApi.create({
          username: form.username,
          password: form.password,
          displayName: form.displayName || null,
          role: form.role
        })
        ElMessage.success('用户创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch { /* axios 拦截器已 toast */ }
    finally { submitting.value = false }
  })
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户 ${row.displayName || row.username}？`,
      '删除确认',
      { type: 'warning' }
    )
    await usersApi.remove(row.id)
    ElMessage.success('用户已删除')
    loadData()
  } catch { /* 取消或失败 */ }
}

onMounted(async()=>{roles.value=await rolesApi.list();await loadData()})
</script>
