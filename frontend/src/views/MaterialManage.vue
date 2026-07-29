<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { findCourseList, deleteMaterial } from "@/apis/materialsAPI"
import { access } from '@/utils/access'

const router = useRouter()
const loading = ref(false)
const materials = ref([])

// 分页参数
const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 获取课件列表
const fetchMaterials = async () => {
  try {
    loading.value = true
    const res = await findCourseList({
      page: pagination.currentPage,
      size: pagination.pageSize
    })
    console.log(res.data.content)
    materials.value = res.data.content.list
    pagination.total = res.data.content.total
  } catch (error) {
    ElMessage.error('获取课件列表失败')
  } finally {
    loading.value = false
  }
}

// 编辑课件
const handleEdit = (id) => {
  router.push({ name: 'MaterialEdit', params: { id } })
}

// 删除课件
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该课件吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deleteMaterial(id)
    ElMessage.success('删除成功')
    fetchMaterials()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 分页变化
const handleCurrentChange = (val) => {
  pagination.currentPage = val
  fetchMaterials()
}

onMounted(() => {
  access([0, 1]) // 管理员和团队账号可管理课件
  fetchMaterials()
})
</script>

<template>
  <main class="flex-grow container mx-auto px-4 py-8">
    <div class="max-w-6xl mx-auto">
      <div class="mb-8 flex justify-between items-center">
        <h1 class="text-2xl font-bold text-gray-800">课件管理</h1>
        <el-button type="primary" @click="router.push({ name: 'MaterialUpload' })">
          上传新课件
        </el-button>
      </div>
      
      <div class="bg-white rounded-xl shadow-lg p-6">
        <el-table :data="materials" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="courseType" label="类型" width="120">
            <template #default="{ row }">
              {{ row.courseType === 1 ? '通识课程' : '特色课程' }}
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="所属课程" />
          <el-table-column prop="createTime" label="创建时间" width="180">
            <template #default="{ row }">
              {{ new Date(row.createTime).toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">              
              <el-button size="small" type="danger" @click="handleDelete(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="pagination.total"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </div>
  </main>
</template>
