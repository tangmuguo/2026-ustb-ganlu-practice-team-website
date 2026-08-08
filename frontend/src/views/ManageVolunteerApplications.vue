<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVolunteerApplications, updateVolunteerApplicationStatus } from '@/apis/applicationAPI'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, status: '' })
const statusOptions = [
  { label: '待联系', value: 'PENDING' },
  { label: '已联系', value: 'CONTACTED' },
  { label: '已接受', value: 'ACCEPTED' },
  { label: '未接受', value: 'REJECTED' },
]

function statusLabel(status) {
  return statusOptions.find((item) => item.value === status)?.label || status
}

async function load() {
  loading.value = true
  try {
    const { data } = await getVolunteerApplications(query)
    const content = data?.content || {}
    records.value = content.items || content.records || []
    total.value = Number(content.total || 0)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '报名列表加载失败')
  } finally {
    loading.value = false
  }
}

async function changeStatus(row, status) {
  await ElMessageBox.confirm(`确定将“${row.name}”的状态修改为“${statusLabel(status)}”吗？`, '确认修改')
  try {
    await updateVolunteerApplicationStatus(row.id, status)
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.response?.data?.message || '更新失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="manage-page">
    <div class="manage-heading">
      <div><span>ADMIN</span><h1>志愿者报名管理</h1></div>
      <el-select v-model="query.status" clearable placeholder="全部状态" @change="query.page = 1; load()">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </div>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="organization" label="学校 / 单位" min-width="150" show-overflow-tooltip />
      <el-table-column prop="gradeOrMajor" label="年级 / 专业" min-width="120" show-overflow-tooltip />
      <el-table-column prop="preferredRegion" label="意向地区" min-width="120" show-overflow-tooltip />
      <el-table-column prop="skills" label="擅长方向" min-width="150" show-overflow-tooltip />
      <el-table-column prop="introduction" label="自我介绍" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100"><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-dropdown @command="(status) => changeStatus(scope.row, status)">
            <el-button size="small">修改状态<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in statusOptions" :key="item.value" :command="item.value" :disabled="item.value === scope.row.status">{{ item.label }}</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.pageSize" class="pagination" layout="total, prev, pager, next" :total="total" @current-change="load" />
  </div>
</template>

<style scoped>
.manage-page { max-width:1280px;margin:0 auto;padding:48px 24px; }
.manage-heading { display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:28px; }
.manage-heading span { color:#0f7c8f;font-size:12px;font-weight:750;letter-spacing:.14em; }
.manage-heading h1 { margin:8px 0 0;color:#173646;font-size:32px;font-weight:780; }
.pagination { justify-content:flex-end;margin-top:24px; }
@media(max-width:640px){.manage-heading{align-items:stretch;flex-direction:column}.manage-page{padding:36px 16px}}
</style>
