<script setup>
import { ref, onMounted,computed } from 'vue'
import { useRoute } from 'vue-router'
import { getCourseDetail,getAllCourseTypel } from '@/apis/materialsAPI'
import { ElMessage } from 'element-plus'
// import { VideoPlayer } from '@videojs-player/vue'
// import 'video.js/dist/video-js.css' // 引入样式
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

const route = useRoute()
const courseDetail = ref(null)
const loading = ref(true)
// 课程类型映射
const courseTypeMap = ref([])

onMounted(async () => {
  try {
    //courseTypeMap.value=await getAllCourseTypel()
    const response = await getCourseDetail(route.params.id)
    courseDetail.value = response.data.content

    const res=await getAllCourseTypel()
    courseTypeMap.value=res.data.content

    //checkVideoSupport()
  } catch (error) {
    ElMessage.error('获取课程详情失败')
    console.error(error)
  } finally {
    loading.value = false
  }
})

async function checkVideoSupport() {
  const response = await fetch('http://localhost:8080/materials/%E6%88%91%E4%BB%AC%E6%89%80%E7%9F%A5%E9%81%93%E7%9A%84%E5%8A%A8%E7%89%A9_b49e9924_9209.mp4')
    const blob = await response.blob();
    const blobUrl = URL.createObjectURL(blob);
    
    const video = document.querySelector('video');
    video.src = blobUrl;
    
    // 清理内存
    video.addEventListener('ended', () => {
      URL.revokeObjectURL(blobUrl);
    });
}
// 根据课程类型ID获取类型名称
const getCourseTypeName = (courseId) => {
  const type = courseTypeMap.value.find(item => item.id === courseId)
  return type ? type.courseName : '未知类型'
}

// 根据文件类型返回对应的展示组件
const getFileComponent = (fileUrl, fileType) => {
  const fileExtension = fileUrl.split('.').pop().toLowerCase()
  
  if (['mp4', 'webm', 'ogg'].includes(fileExtension)) {
    courseDetail.fileType='mp4'
    return 'video'
  } else if (['pdf'].includes(fileExtension)) {
    return 'pdf'
  } else if (['ppt', 'pptx'].includes(fileExtension)) {
    return 'ppt'
  } else {
    return 'unknown'
  }
}

const correctVideoType = computed(() => {
  if (!courseDetail.value?.files) return ''
  
  const ext = courseDetail.value.files.split('.').pop().toLowerCase()
  const typeMap = {
    mp4: 'video/mp4',
    webm: 'video/webm',
    ogg: 'video/ogg'
  }
  return typeMap[ext] || 'video/mp4' // 默认fallback
})


</script>

<template>
  <div class="course-detail-container" v-loading="loading">
    <div class="course-header" v-if="courseDetail">
      <h1>{{ courseDetail.title }}</h1>
      <p class="author">作者: {{ courseDetail.author }}</p>
      <p class="type">课程类型: {{ getCourseTypeName(courseDetail.courseId) }}</p>
    </div>

    <div class="file-content" v-if="courseDetail && courseDetail.files">
      <template v-if="getFileComponent(courseDetail.files, courseDetail.fileType) === 'video'">
        <video controls="controls" class="media-player">
          <source :src="`${apiBaseUrl}${courseDetail.files}`" :type="correctVideoType">
          您的浏览器不支持视频播放
        </video>
        <!--<video-player
          :src="`${apiBaseUrl}${courseDetail.files}`"
          controls
          playback-rates="[0.5, 1, 1.5, 2]"
        />-->
      </template>

      <template v-else-if="getFileComponent(courseDetail.files, courseDetail.fileType) === 'pdf'">
        <iframe 
          :src="`${apiBaseUrl}${courseDetail.files}`" 
          class="pdf-viewer"
          frameborder="0"
        ></iframe>
      </template>

      <template v-else-if="getFileComponent(courseDetail.files, courseDetail.fileType) === 'ppt'">
        <div class="ppt-notice">
          <p>PPT文件需要下载后查看</p>
          <el-button 
            type="primary" 
            :href="`${apiBaseUrl}${courseDetail.files}`"
            download
          >
            下载PPT文件
          </el-button>
        </div>
      </template>

      <template v-else>
        <div class="unknown-file">
          <p>不支持的文件类型</p>
          <el-button 
            type="primary" 
            :href="`${apiBaseUrl}${courseDetail.files}`"
            download
          >
            下载文件
          </el-button>
        </div>
      </template>
    </div>

    <div class="no-content" v-else-if="!loading">
      <p>该课程暂无内容</p>
    </div>
  </div>
</template>

<style scoped>
.course-detail-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  background-color: white;
  min-height: 80vh;
}

.course-header {
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #eee;
}

.course-header h1 {
  font-size: 24px;
  margin-bottom: 10px;
}

.author, .type {
  color: #666;
  font-size: 14px;
  margin-bottom: 5px;
}

.file-content {
  margin-top: 30px;
}

.media-player {
  width: 100%;
  max-height: 800px;
  background-color: #000;
}

.pdf-viewer {
  width: 100%;
  height: 800px;
}

.ppt-notice, .unknown-file {
  text-align: center;
  padding: 40px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.ppt-notice p, .unknown-file p {
  margin-bottom: 20px;
  color: #666;
}

.no-content {
  text-align: center;
  padding: 40px;
  color: #999;
}
</style>