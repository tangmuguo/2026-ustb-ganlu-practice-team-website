<script setup>
import { Picture } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

const props = defineProps({
  course: {
    type: Object,
    required: true
  }
})

const router = useRouter()

const navigateToDetail = () => {
  //window.open(`/mdetail/${props.course.id}`, '_blank')

  // 使用 router.resolve 获取完整路由路径
  const route = router.resolve({
    name: 'mdetail',
    params: { id: props.course.id }
  })
  // 在新窗口打开
  window.open(route.href, '_blank')
}
</script>

<template>
  <div class="course-card" @click="navigateToDetail">
    <div class="course-image">
      <el-image 
        :src="`${apiBaseUrl}${course.thumbnailUrl}`"
        fit="cover"
        class="image-placeholder"
      >
        <template #error>
          <div class="image-error">
            <el-icon><Picture></Picture></el-icon>
          </div>
        </template>
      </el-image>
    </div>
    <div class="course-name">{{ course.title }}</div>
  </div>
</template>

<style scoped>
.course-card {
  cursor: pointer;
  transition: all 0.3s ease;
}

.course-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.course-image {
  width: 100%;
  height: 150px;
  background-color: #f0f2f5;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.image-error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f0f2f5;
  color: #c0c4cc;
}

.course-name {
  font-size: 14px;
  text-align: center;
  color: #606266;
}
</style>