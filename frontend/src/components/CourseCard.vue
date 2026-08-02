<script setup>
import { computed } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { resolveMaterialAssetUrl } from '@/apis/materialsAPI'

const props = defineProps({
  course: { type: Object, required: true }
})
const router = useRouter()
const coverUrl = computed(() => resolveMaterialAssetUrl(props.course.thumbnailUrl))
const subject = computed(() => props.course.courseType === 1
  ? props.course.courseName
  : props.course.customSubject)
const fileLabel = computed(() => (props.course.fileExtension || '文件').toUpperCase())
</script>

<template>
  <article class="course-card" tabindex="0" @click="router.push(`/mdetail/${course.id}`)" @keyup.enter="router.push(`/mdetail/${course.id}`)">
    <el-image :src="coverUrl" fit="cover" class="cover">
      <template #error>
        <div class="cover-error"><el-icon :size="36"><Picture /></el-icon></div>
      </template>
    </el-image>
    <div class="body">
      <div class="tags">
        <el-tag size="small" :type="course.courseType === 1 ? 'primary' : 'success'">
          {{ course.courseType === 1 ? '通识' : '特色' }}
        </el-tag>
        <el-tag size="small" type="info">{{ fileLabel }}</el-tag>
      </div>
      <h3>{{ course.title }}</h3>
      <p>{{ subject || '未分类' }} · {{ course.year }} 年</p>
      <p class="uploader">{{ course.uploaderName || '未知上传者' }}</p>
    </div>
  </article>
</template>

<style scoped>
.course-card {
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fff;
  transition: transform .2s ease, box-shadow .2s ease;
}
.course-card:hover, .course-card:focus { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(31, 45, 61, .12); outline: none; }
.cover { width: 100%; height: 165px; display: block; }
.cover-error { width: 100%; height: 100%; display: grid; place-items: center; color: #a8abb2; background: #f5f7fa; }
.body { padding: 14px; }
.tags { display: flex; gap: 6px; margin-bottom: 8px; }
h3 { margin: 0 0 8px; font-size: 16px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
p { margin: 4px 0; font-size: 13px; color: #606266; }
.uploader { color: #909399; }
</style>
