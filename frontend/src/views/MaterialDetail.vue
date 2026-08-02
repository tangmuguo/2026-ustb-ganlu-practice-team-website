<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import FilePreview from '@/components/material/FilePreview.vue'
import { downloadMaterial, getCourseDetail, saveDownload } from '@/apis/materialsAPI'
import { userinfoStore } from '@/stores/userStore'

const route = useRoute()
const router = useRouter()
const userStore = userinfoStore()
const loading = ref(true)
const downloading = ref(false)
const material = ref()
const subject = computed(() => material.value?.courseType === 1
  ? material.value?.courseName
  : material.value?.customSubject)
const sizeLabel = computed(() => {
  const size = material.value?.fileSize
  if (!size) return '未知大小'
  const megabytes = size / 1024 / 1024
  return megabytes >= 1 ? `${megabytes.toFixed(1)} MB` : `${Math.ceil(size / 1024)} KB`
})

const load = async () => {
  loading.value = true
  try {
    const response = await getCourseDetail(route.params.id)
    material.value = response.data.content
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '课件详情加载失败')
  } finally {
    loading.value = false
  }
}

const download = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后可下载原文件')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  downloading.value = true
  try {
    const response = await downloadMaterial(material.value.id)
    saveDownload(response.data, material.value.originalFilename)
    ElMessage.success('下载已开始')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main v-loading="loading" class="detail-page">
    <template v-if="material">
      <header class="detail-header">
        <div>
          <div class="tags">
            <el-tag>{{ material.courseType === 1 ? '通识课程' : '特色课程' }}</el-tag>
            <el-tag type="info">{{ (material.fileExtension || '文件').toUpperCase() }}</el-tag>
          </div>
          <h1>{{ material.title }}</h1>
          <p>{{ subject }} · {{ material.year }} 年 · {{ material.uploaderName }}</p>
          <p>{{ material.originalFilename }} · {{ sizeLabel }}</p>
        </div>
        <el-button type="primary" size="large" :loading="downloading" @click="download">
          <el-icon><Download /></el-icon>
          {{ userStore.isLoggedIn ? '下载原文件' : '登录后下载' }}
        </el-button>
      </header>
      <FilePreview :material="material" />
    </template>
    <el-empty v-else-if="!loading" description="课件不存在或已删除" />
  </main>
</template>

<style scoped>
.detail-page { max-width: 1200px; min-height: 70vh; margin: 0 auto; padding: 32px 20px 56px; }
.detail-header { display: flex; justify-content: space-between; align-items: end; gap: 24px; margin-bottom: 24px; padding-bottom: 20px; border-bottom: 1px solid #ebeef5; }
.detail-header h1 { margin: 10px 0; font-size: clamp(26px, 4vw, 38px); color: #303133; }
.detail-header p { margin: 5px 0; color: #606266; }
.tags { display: flex; gap: 8px; }
@media (max-width: 640px) { .detail-header { align-items: stretch; flex-direction: column; } }
</style>
