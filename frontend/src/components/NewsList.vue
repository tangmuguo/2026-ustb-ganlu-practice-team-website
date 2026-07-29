<script setup>
import { ref, onMounted } from 'vue'
import { GetLimitNews } from '@/apis/newsAPI'
import NewsCard from './NewsCard.vue'

const newsData = ref([])

// 获取最新新闻
const fetchNews = async () => {
  try {
    const res = await GetLimitNews()
    if (res.data.code === 200) {
        console.log(newsData.value)
      newsData.value = res.data.content
    } else {
      // 如果后端没有数据，使用默认占位数据
      newsData.value = generatePlaceholderData()
    }
  } catch (error) {
    console.error('获取新闻失败:', error)
    newsData.value = generatePlaceholderData()
  }
}

// 生成占位数据
const generatePlaceholderData = () => {
  return [
    {
      id: 1,
      title: '新闻标题将由后期添加',
      content: '新闻内容将由后端添加，此处为占位文本。',
      createAt: null,
      imageUrl: null,
      linkUrl: '#'
    },
    {
      id: 2,
      title: '新闻标题将由后期添加',
      content: '新闻内容将由后端添加，此处为占位文本。',
      createAt: null,
      imageUrl: null,
      linkUrl: '#'
    },
    {
      id: 3,
      title: '新闻标题将由后期添加',
      content: '新闻内容将由后端添加，此处为占位文本。',
      createAt: null,
      imageUrl: null,
      linkUrl: '#'
    }
  ]
}

onMounted(() => {
  fetchNews()
})
</script>

<template>
  <div class="news-list-container">
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <NewsCard 
        v-for="(news, index) in newsData" 
        :key="index" 
        :news="news" 
        class="news-item"
      />
    </div>
  </div>
</template>

<style scoped>

</style>