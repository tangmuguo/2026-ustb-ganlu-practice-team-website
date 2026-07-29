<script setup>
import { ref, computed, onMounted, onBeforeMount } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { AddNews, GetAllNews, DeleteNews, UpdateNews } from '@/apis/newsAPI'
import { access } from '@/utils/access'
import UploadWidget from "@/components/UploadWidget.vue"
import { formatTime } from '@/utils/date'
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

// 表格数据
const newsList = ref([])

// 搜索和分页
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// 过滤后的新闻数据
const filteredNews = computed(() => {
  let result = newsList.value
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (news) =>
        news.title.toLowerCase().includes(query) ||
        news.author.toLowerCase().includes(query) ||
        news.content.toLowerCase().includes(query)
    )
  }
  return result.slice(
    (currentPage.value - 1) * pageSize.value,
    currentPage.value * pageSize.value
  )
})

const totalNews = computed(() => {
  return searchQuery.value
    ? filteredNews.value.length
    : newsList.value.length
})

// 对话框相关
const dialogVisible = ref(false)
const isEdit = ref(false)
const newsForm = ref({
  id: null,
  title: '',
  author: '',
  content: '',
  publishTime: '',
  imageUrl: '',
  status: 1 // 1-发布 0-草稿
})
const newsFormRef = ref(null)

// 表单验证规则
const rules = {
  caption: [
    { required: true, message: '请输入新闻标题', trigger: 'blur' }    
  ],
  content: [
    { required: true, message: '请输入新闻内容', trigger: 'blur' }
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

// 添加新闻
const handleAdd = () => {
  isEdit.value = false
  newsForm.value = {
    id: null,
    caption: '',
    content: '',
    createAt: new Date().toISOString().slice(0, 10),
    imageUrl: ''
  }
  dialogVisible.value = true
}

// 编辑新闻
const handleEdit = (row) => {
  isEdit.value = true
  newsForm.value = { ...row }
  dialogVisible.value = true
}

// 删除新闻
const handleDelete = (id) => {
  ElMessageBox.confirm('确定要删除该新闻吗?', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      await DeleteNews(id)
      ElMessage.success('删除成功')
      loadNews()
    })
    .catch(() => {})
}

// 添加新闻
async function addNews() {
  newsFormRef.value.validate(async (valid) => {
    if (valid) {
      const res = await AddNews(newsForm.value)
      if (res.data.code === 200) {
        ElMessage.success('添加成功')
        dialogVisible.value = false
        loadNews()
      } else {
        ElMessage.error(res.data.message || '添加失败')
      }
    }
  })
}

// 更新新闻
async function updateNews() {
  newsFormRef.value.validate(async (valid) => {
    if (valid) {
      const res = await UpdateNews(newsForm.value)
      if (res.data.code === 200) {
        ElMessage.success('更新成功')
        dialogVisible.value = false
        loadNews()
      } else {
        ElMessage.error(res.data.message || '更新失败')
      }
    }
  })
}

// 提交表单
const submitForm = () => {
  if (!newsForm.value.caption || !newsForm.value.content) {
    ElMessage.warning('请填写完整信息')
    return
  }

  if (!newsForm.value.imageUrl) {
    ElMessage.warning('请上传新闻封面图片')
    return
  }

  if (isEdit.value) {
    updateNews()
  } else {
    addNews()
  }
}

// 加载新闻列表
async function loadNews() {
  loading.value = true
  try {
    const res = await GetAllNews()
    if (res.data.code === 200) {
      newsList.value = res.data.content
    } else {
      ElMessage.error('加载新闻列表失败')
    }
  } catch (error) {
    ElMessage.error('网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 处理图片上传
const handleImageUpload = (url) => {
  newsForm.value.imageUrl = url
}

onMounted(() => {
  loadNews()
})

onBeforeMount(() => {
  access(0) // 需要管理员权限
})
</script>

<template>
  <div class="news-management-container">
    <div class="news-management">
      <div class="header">
        <h2>新闻管理</h2>
        <el-button type="primary" @click="handleAdd">添加新闻</el-button>
      </div>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索新闻标题、作者或内容"
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

      <!-- 新闻表格 -->
      <el-table
        :data="filteredNews"
        border
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column prop="caption" label="标题" width="180" />
        <el-table-column prop="createAt" label="发布时间" width="120">
            <template #default="{ row }">
                {{ formatTime(row.createAt) }}
            </template>
        </el-table-column>
        <el-table-column label="封面" width="120">
          <template #default="{ row }">
            <el-image
              style="width: 100px; height: 60px"
              :src="`${apiBaseUrl}${row.imageUrl}`"
              fit="cover"
              preview-teleported
            />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="详细内容" >
            <template #default="{ row }">
                <span v-if="row.content.length>30">{{ row.content.substring(0,30) }}...</span>
                <span v-else>{{ row.content }}</span>
            </template>
        </el-table-column>
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
          :total="totalNews"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>

      <!-- 添加/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '编辑新闻' : '添加新闻'"
        width="700px"
      >
        <el-form
          ref="newsFormRef"
          :model="newsForm"
          :rules="rules"
          label-width="80px"
        >
          <el-form-item label="标题" prop="caption">
            <el-input v-model="newsForm.caption" />
          </el-form-item>     
          <el-form-item label="跳转链接" prop="linkUrl">
            <el-input v-model="newsForm.linkUrl" />
          </el-form-item>
          <el-form-item label="封面图片" prop="imageUrl">
            <UploadWidget
              ref="uploadWidget"
              accept="image/*"
              tipText="请上传新闻封面图片，大小不超过5MB"
              max-size-mb="5"
              @upload="handleImageUpload"
            />
            <el-image
              v-if="newsForm.imageUrl"
              style="width: 100px; height: 60px; margin-top: 10px"
              :src="`${apiBaseUrl}${newsForm.imageUrl}`"
              fit="cover"
            />
          </el-form-item>
          <el-form-item label="内容" prop="content">
            <el-input
              v-model="newsForm.content"
              type="textarea"
              :rows="8"
              placeholder="请输入新闻内容"
            />
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
.news-management-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.news-management {
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