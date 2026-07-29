<script setup>
import { defineProps } from 'vue'
import { formatTime } from '@/utils/date'
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
const props = defineProps({
  news: {
    type: Object,
    required: true
  }
})

// 格式化日期
const formatDate = (date) => {
  return formatTime(date)
}

// 截取内容
const truncateContent = (content) => {
  if (!content) return '新闻内容将由后端添加，此处为占位文本。'
  return content.length > 50 ? content.substring(0, 50) + '...' : content
}

// 处理点击事件
const handleClick = () => {
  if (props.news.linkUrl) {
    if (props.news.linkUrl.startsWith('http')) {
      window.open(props.news.linkUrl, '_blank')
    } else {
      window.location.href = props.news.linkUrl
    }
  }
}
</script>

<template>
  <el-card class="news-card" shadow="hover" @click="handleClick">
    <div v-if="news.imageUrl" class="news-image">
      <el-image :src="`${apiBaseUrl}${news.imageUrl}`" fit="cover" class="image" />
    </div>
    <div v-else class="image-placeholder">
      <span>图片占位</span>
    </div>
    <div class="news-content">
      <div class="news-date">{{ formatDate(news.createAt) }}</div>
      <h3 class="news-title">{{ news.caption }}</h3>
      <p class="news-excerpt">{{ news.excerpt || truncateContent(news.content) }}</p>
      <el-link type="primary" class="read-more" @click.stop="handleClick">
        阅读全文
      </el-link>
    </div>
  </el-card>
</template>

<style scoped>
.news-card {
    width: 100%;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.news-card:hover {
  transform: translateY(-5px);
}

.image-placeholder,
.news-image {
  height: 280px;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 16px;
}

.news-image .image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.news-content {
  padding: 15px;
}

.news-date {
  color: #999;
  font-size: 12px;
  margin-bottom: 10px;
}

.news-title {
  font-size: 16px;
  color: #333;
  font-weight: bold;
  margin: 0 0 10px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.news-excerpt {
  font-size: 14px;
  color: #666;
  margin: 0 0 15px 0;
  line-height: 1.5;
  display: -webkit-box;
  /* -webkit-line-clamp: 2; */
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.read-more {
  font-size: 14px;
}
</style>