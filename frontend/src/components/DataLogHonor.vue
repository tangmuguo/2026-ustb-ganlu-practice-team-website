<script setup>
import { ref, onMounted,computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {findAllWords,deleteWord} from '@/apis/fengcaiAPI'
import { userinfoStore } from '@/stores/userStore'

const loading = ref(false)
const deletingId = ref(null)
const userinfo=userinfoStore()
// 表格数据
const teams = ref([])

//分页
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalTeams = computed(() => {
  return searchQuery.value
    ? filteredTeams.value.length
    : teams.value.length
})
// 分页
const handleSizeChange = (val) => {
  pageSize.value = val
}

const handleCurrentChange = (val) => {
  currentPage.value = val
}
const filteredTeams = computed(() => {
  // 1. 获取原始数据副本
  let result = [...teams.value]
  
  // 2. 执行搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (team) =>
        team.teamname?.toLowerCase().includes(query) ||
        team.username?.toLowerCase().includes(query)
    )
  }
  
  // 3. 添加类型转换字段（保留所有原始字段）
  result = result.map(item => ({
    ...item,  // 保留所有原有属性
    typeText: convertType(item.type) // 添加转换后的字段
  }))
  
  // 4. 最后执行分页
  return result.slice(
    (currentPage.value - 1) * pageSize.value,
    currentPage.value * pageSize.value
  )
})

// type转换函数
function convertType(type) {
  const typeMap = {
    3: '团队荣誉',
    4: '团队日志'
    // 可以继续添加其他映射关系
  }
  return typeMap[type] || `未知类型(${type})`
}
// 日期格式化
const formatDate = (dateString) => {
  return new Date(dateString).toLocaleString()
}

// 加载数据
const loadData = async () => {
  try {
    loading.value = true
    const d=await findAllWords(userinfo.user.content.id)
    teams.value=d.data.content
    console.log(d.data.content)
  } catch (error) {
    ElMessage.error('加载数据失败: ' + error.message)
  } finally {
    loading.value = false
  }

}

// 刷新数据
const refreshData = () => {
  loadData()
}

// 删除数据
const handleDelete = async (id) => {
  console.log(id)
  try {
    await ElMessageBox.confirm('确定要删除这条记录吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    deletingId.value = id
    const d=await deleteWord(id)
    if(d.data.code===200){
      ElMessage.success('删除成功')
    }else{
      ElMessage.info('记录不存在')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + error.message)
    }
  } finally {
    deletingId.value = null
  }
}

// 组件挂载时加载数据
onMounted(() => {
  
  loadData()
})
</script>

<template>
    <div class="list-section">
        <div class="header">
        <h2>数据列表</h2>
        <el-button 
            type="primary" 
            size="small" 
            @click="refreshData"
            :loading="loading"
        >刷新</el-button>
        </div>
        
        <el-table 
        :data="filteredTeams" 
        style="width: 100%"
        v-loading="loading"
        >
          <el-table-column prop="typeText" label="文件类型" width="120"></el-table-column>
          <el-table-column prop="caption" label="标题"></el-table-column>
          <el-table-column prop="content" label="内容" show-overflow-tooltip></el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
              </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
              <template #default="scope">
              <el-button
                  size="small"
                  type="danger"
                  @click="handleDelete(scope.row.id)"
                  :loading="deletingId === scope.row.id"
              >删除</el-button>
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
    </div>
</template>

<style scoped>
.list-section {
  background-color: #fff;
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
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>