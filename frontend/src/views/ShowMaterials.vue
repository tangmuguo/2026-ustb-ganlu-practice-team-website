<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CourseList from '@/components/CourseList.vue'
import MaterialFilters from '@/components/material/MaterialFilters.vue'
import MaterialUploadDialog from '@/components/material/MaterialUploadDialog.vue'
import { getMaterialCategories, searchMaterials } from '@/apis/materialsAPI'
import { userinfoStore } from '@/stores/userStore'

const userStore = userinfoStore()
const loading = ref(false)
const uploadVisible = ref(false)
const courses = ref([])
const categories = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(12)
const filters = ref({ keyword: '', courseType: null, courseId: null, year: null })
const canManage = computed(() => [0, 1].includes(userStore.currentUser?.level))

const loadCategories = async () => {
  const response = await getMaterialCategories()
  categories.value = response.data.content || []
}

const loadMaterials = async () => {
  loading.value = true
  try {
    const response = await searchMaterials({
      ...filters.value,
      page: page.value,
      pageSize: pageSize.value
    })
    courses.value = response.data.content?.list || []
    total.value = response.data.content?.total || 0
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '课件列表加载失败')
  } finally {
    loading.value = false
  }
}

const applyFilters = (next) => {
  filters.value = next
  page.value = 1
  loadMaterials()
}

const changePageSize = (size) => {
  pageSize.value = size
  page.value = 1
  loadMaterials()
}

onMounted(async () => {
  try {
    await Promise.all([loadCategories(), loadMaterials()])
  } catch (error) {
    ElMessage.error('课件筛选项加载失败')
  }
})
</script>

<template>
  <main class="materials-page">
    <section class="heading">
      <div>
        <p class="eyebrow">甘露课件共享</p>
        <h1>教学资料中心</h1>
        <p>浏览最近十年的通识与特色课程，PDF、图片和演示文稿均可在线预览。</p>
      </div>
      <el-button v-if="canManage" type="primary" size="large" @click="uploadVisible = true">上传课件</el-button>
    </section>

    <MaterialFilters v-model="filters" :categories="categories" @search="applyFilters" @reset="applyFilters" />

    <section class="results">
      <div class="result-header">
        <h2>课件列表</h2>
        <span>共 {{ total }} 条</span>
      </div>
      <CourseList :courses="courses" :loading="loading" />
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[8, 12, 20, 40]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @current-change="loadMaterials"
        @size-change="changePageSize"
      />
    </section>

    <MaterialUploadDialog
      v-if="canManage"
      v-model="uploadVisible"
      :categories="categories"
      @uploaded="loadMaterials"
    />
  </main>
</template>

<style scoped>
.materials-page { max-width: 1200px; margin: 0 auto; padding: 32px 20px 56px; }
.heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
.heading h1 { margin: 4px 0 8px; font-size: clamp(28px, 4vw, 42px); color: #303133; }
.heading p { margin: 0; color: #606266; }
.eyebrow { color: #409eff !important; font-weight: 700; letter-spacing: .08em; }
.results { margin-top: 26px; }
.result-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.result-header h2 { margin: 0; font-size: 22px; }
.result-header span { color: #909399; }
.pagination { justify-content: flex-end; margin-top: 26px; }
@media (max-width: 640px) {
  .heading { align-items: stretch; flex-direction: column; }
  .pagination { overflow-x: auto; justify-content: flex-start; }
}
</style>
