<script setup>
import { ref, computed, onMounted,onBeforeMount } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {AddStudent,GetAllStudents,DeleteTeam,UpdateTeam} from '@/apis/userAPI'
import {access} from '@/utils/access'
import { useRouter } from 'vue-router'

const router = useRouter()
// 表格数据
const teams = ref([])
const needPassword = ref(null)

// 搜索和分页
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const matchingTeams = computed(() => {
  let result = teams.value
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (team) =>
        String(team.realname || '').toLowerCase().includes(query) ||
        String(team.username || '').toLowerCase().includes(query)
    )
  }
  return result
})

// 过滤后的学生数据
const filteredTeams = computed(() => {
  const result = matchingTeams.value
  return result.slice(
    (currentPage.value - 1) * pageSize.value,
    currentPage.value * pageSize.value
  )
})

const totalTeams = computed(() => {
  return matchingTeams.value.length
})

// 对话框相关
const dialogVisible = ref(false)
const isEdit = ref(false)
const teamForm = ref({
  id: null,
  username: '',
  password: '',
  teamname: '',
  imageUrl: ''
})
const teamFormRef=ref(null)

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    {
      required: needPassword.value,
      message: '请输入密码',
      trigger: 'blur',      
    },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  realname: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 10, message: '长度在 2 到 10 个字符', trigger: 'blur' }
  ]
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
}

// 分页
const handleSizeChange = (val) => {
  pageSize.value = val
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}

// 添加团队
const handleAdd = () => {
  router.push('/regs')
}

// 编辑团队
const handleEdit = (row) => {
  isEdit.value = true
  needPassword.value=false
  teamForm.value = { ...row }
  dialogVisible.value = true
}

// 删除团队
const handleDelete = (id) => {
  ElMessageBox.confirm('确定要删除该学生吗?', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await DeleteTeam(id)
      ElMessage.success('删除成功')
      await loadTeams()
    })
    .catch(() => {})
}

function AddUser(){
    teamFormRef.value.validate(async (res)=>{
        if(res){
            const d=await AddStudent(teamForm.value)
            if(d.data.code==200){
                ElMessage({
                message: '添加成功',
                type: 'success',
                })                       
            }else{
                ElMessage({
                message: '添加失败',
                type: 'fail',
                })
            }
        }
    })
}

async function UpdateUser(updateForm){
  await teamFormRef.value.validate()
  const d = await UpdateTeam(updateForm)
  if (d.data.code == 200) {
    ElMessage.success('更新成功')
    await loadTeams()
    return true
  }
  ElMessage.error(d.data.message || '更新失败')
  return false
}

// 提交表单
const submitForm = async () => {
  if (!teamForm.value.username || !teamForm.value.realname) {
    ElMessage.warning('请填写完整信息')
    return
  }

  if (!isEdit.value && !teamForm.value.password) {
    ElMessage.warning('请填写密码')
    return
  }

  if (isEdit.value) {
    // 修改团队
    const index = teams.value.findIndex((t) => t.id === teamForm.value.id)
    if (index !== -1) {
      // 如果密码为空则不更新密码
      const updatedTeam = { ...teamForm.value }
      if (!updatedTeam.password) {
        delete updatedTeam.password
      }      
      if (!await UpdateUser(updatedTeam)) return
    }
  } else {
    // 添加团队
    AddUser()
  }

  dialogVisible.value = false
  //loadTeams()
}

async function loadTeams(){
    const d=await GetAllStudents()
    if(d.data.code==200){
        teams.value=d.data.content                      
    }
}

const handleImageUpload = (url) => {
  teamForm.value.imageUrl = url
}

onMounted(() => {
  // 这里可以添加从API加载数据的逻辑  
  loadTeams()
})

onBeforeMount(()=>{
  access([0, 1])
})
</script>

<template>
  <div class="team-management-container">
    <div class="team-management">
      <div class="header">
        <h2>学生管理</h2>
      </div>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索学生名或用户名"
          style="width: 300px"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
      </div>

      <!-- 团队表格 -->
      <el-table
        :data="filteredTeams"
        border
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="realname" label="真实姓名" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              size="small"
              type="danger"
              @click="handleDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalTeams"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>

      <!-- 添加/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑团队' : '添加团队'"
        width="500px"
      >
        <el-form
          ref="teamFormRef"
          :model="teamForm"
          :rules="rules"
          label-width="100px"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="teamForm.username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="teamForm.password"
              type="password"
              show-password
              :placeholder="isEdit ? '留空则不修改密码' : '请输入密码'"
            />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realname">
            <el-input v-model="teamForm.realname" />
          </el-form-item>          
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="submitForm">确认</el-button>
          </span>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<style scoped>
.team-management-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.team-management {
  background: #fff;
  padding: 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.search-bar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
