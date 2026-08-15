<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AddStudent, DeleteStudents, GetAllStudents, GetAllTeams, UpdateStudent } from '@/apis/userAPI'
import { access } from '@/utils/access'
import { userinfoStore } from '@/stores/userStore'

const userStore = userinfoStore()
const students = ref([])
const teams = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = ref(emptyForm())

const isAdministrator = computed(() => Number(userStore.currentUser?.level) === 0)

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 30, message: '长度为 3～30 个字符', trigger: 'blur' }],
  realname: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }, { min: 2, max: 30, message: '姓名长度为 2～30 个字符', trigger: 'blur' }],
  belongschool: [{ required: true, message: '请输入所属小学', trigger: 'blur' }, { max: 100, message: '学校名称不能超过 100 个字符', trigger: 'blur' }],
  grade: [{ required: true, message: '请输入年级', trigger: 'blur' }, { max: 30, message: '年级不能超过 30 个字符', trigger: 'blur' }],
  phone: [{ required: true, message: '创建时必须录入联系电话', trigger: 'blur' }, { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' }],
  password: [{ min: 8, max: 72, message: '密码长度为 8～72 个字符', trigger: 'blur' }],
}

function emptyForm() {
  return {
    id: null,
    username: '',
    realname: '',
    displayName: '',
    belongschool: '',
    grade: '',
    phone: '',
    password: '',
    confirmPassword: '',
    teamId: null,
  }
}

async function loadStudents() {
  loading.value = true
  try {
    const { data } = await GetAllStudents()
    students.value = data?.code === 200 ? (data.content || []) : []
    if (data?.code !== 200) throw new Error(data?.message || '加载失败')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '学生列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTeams() {
  if (!isAdministrator.value) return
  try {
    const { data } = await GetAllTeams()
    teams.value = data?.code === 200 ? (data.content || []) : []
  } catch {
    ElMessage.warning('团队列表加载失败，暂不能由管理员新建学生账号')
  }
}

function openCreate() {
  isEdit.value = false
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(student) {
  isEdit.value = true
  // The list intentionally omits phone numbers. Leaving phone blank retains it on the server.
  form.value = { ...emptyForm(), ...student, password: '', confirmPassword: '' }
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!isEdit.value && form.value.password !== form.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!isEdit.value && !form.value.password) {
    ElMessage.warning('请设置初始密码')
    return
  }
  if (!isEdit.value && isAdministrator.value && !form.value.teamId) {
    ElMessage.warning('请选择学生所属团队')
    return
  }

  try {
    if (isEdit.value) {
      const payload = { ...form.value }
      delete payload.id
      delete payload.teamId
      delete payload.confirmPassword
      if (!payload.password) delete payload.password
      if (!payload.phone) delete payload.phone
      await UpdateStudent(form.value.id, payload)
      ElMessage.success('学生账号已更新')
    } else {
      await AddStudent({ ...form.value })
      ElMessage.success('学生账号已创建，尚不能互动或发布内容，需先完成线下核验和监护人授权')
    }
    dialogVisible.value = false
    await loadStudents()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '保存失败')
  }
}

async function remove(student) {
  try {
    await ElMessageBox.confirm(
      `确认删除学生账号“${student.displayName || student.username}”吗？此操作不会替代依法留存的审计或投诉记录。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await DeleteStudents(student.id)
    ElMessage.success('学生账号已删除')
    await loadStudents()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || error.message || '删除失败')
    }
  }
}

onMounted(async () => {
  if (!access([0, 1])) return
  await Promise.all([loadStudents(), loadTeams()])
})
</script>

<template>
  <main class="student-management">
    <header class="page-header">
      <div>
        <p>STUDENT SAFETY</p>
        <h1>学生账号管理</h1>
        <span>团队只能查看和维护已归属给本团队的学生；联系电话不会出现在列表中。</span>
      </div>
      <el-button type="primary" @click="openCreate">创建学生账号</el-button>
    </header>

    <section class="notice">
      新建账号默认处于“待核验/待监护人授权”状态。请由安全负责人完成线下核验和授权留痕后，再由管理员放开互动权限。
    </section>

    <el-table :data="students" v-loading="loading" border>
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="displayName" label="展示名称" min-width="130" />
      <el-table-column prop="realname" label="真实姓名" min-width="120" />
      <el-table-column prop="belongschool" label="学校" min-width="160" />
      <el-table-column prop="grade" label="年级" min-width="100" />
      <el-table-column prop="verificationStatus" label="核验状态" min-width="110" />
      <el-table-column prop="guardianConsentStatus" label="监护人授权" min-width="120" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑学生账号' : '创建学生账号'" width="min(680px, 92vw)">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="30" /></el-form-item>
          <el-form-item label="公开展示名称" prop="displayName"><el-input v-model="form.displayName" maxlength="64" placeholder="留空时公开显示为匿名编号" /></el-form-item>
          <el-form-item label="真实姓名" prop="realname"><el-input v-model="form.realname" maxlength="30" /></el-form-item>
          <el-form-item label="所属小学" prop="belongschool"><el-input v-model="form.belongschool" maxlength="100" /></el-form-item>
          <el-form-item label="年级" prop="grade"><el-input v-model="form.grade" maxlength="30" /></el-form-item>
          <el-form-item v-if="!isEdit" label="联系电话（仅服务端保存）" prop="phone"><el-input v-model="form.phone" maxlength="11" inputmode="numeric" /></el-form-item>
          <el-form-item v-if="isAdministrator && !isEdit" label="所属团队" prop="teamId">
            <el-select v-model="form.teamId" placeholder="请选择团队" style="width:100%">
              <el-option v-for="team in teams" :key="team.id" :label="team.teamname || team.username" :value="team.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="isEdit ? '重设密码（留空不变）' : '初始密码'" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="72" /></el-form-item>
          <el-form-item v-if="!isEdit" label="确认初始密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password maxlength="72" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </main>
</template>

<style scoped>
.student-management { max-width: 1240px; margin: 0 auto; padding: 48px 24px; }
.page-header { display:flex; justify-content:space-between; gap:24px; align-items:end; margin-bottom:22px; }
.page-header p { margin:0 0 6px; color:#2675dd; font-size:12px; font-weight:800; letter-spacing:.12em; }
.page-header h1 { margin:0; color:#173d72; font-size:30px; }.page-header span { display:block; margin-top:8px; color:#687d96; font-size:14px; }
.notice { margin-bottom:18px; padding:14px 16px; color:#735514; background:#fff8df; border:1px solid #f4df96; border-radius:12px; line-height:1.7; }
.form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 18px; }
@media(max-width:720px) { .page-header { align-items:start; flex-direction:column; }.form-grid { grid-template-columns:1fr; } }
</style>
