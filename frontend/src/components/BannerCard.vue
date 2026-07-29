<script setup>
import { computed } from 'vue'
import { ElCarousel, ElCarouselItem, ElImage, ElEmpty } from 'element-plus'

const props = defineProps({
  // 轮播图数据
  data: {
    type: [Array, Object, null],
    default: () => []
  },
  // 轮播间隔(毫秒)
  interval: {
    type: Number,
    default: 5000
  },
  // 轮播图高度
  height: {
    type: String,
    default: '400px'
  },
  // 是否显示箭头
  arrow: {
    type: String,
    default: 'hover' // 'always' | 'hover' | 'never'
  },
  // 指示器位置
  indicatorPosition: {
    type: String,
    default: 'bottom' // 'none' | 'outside'
  },
  // 是否显示标题
  showTitle: {
    type: Boolean,
    default: true
  },
  // 是否只显示可见的轮播图
  onlyVisible: {
    type: Boolean,
    default: true
  }
})

// 计算属性：处理后的有效轮播图数据
const validBanners = computed(() => {
  // 处理传入的data可能不是数组的情况
  let banners = []

  if (Array.isArray(props.data)) {
    banners = [...props.data]
  } else if (props.data && typeof props.data === 'object') {
    // 如果传入的是单个对象，转换为数组
    banners = [props.data]
  }

  // 过滤掉无效数据
  banners = banners.filter(banner => {
    return banner && banner.imageUrl
  })

  // 过滤掉不可见的轮播图
  if (props.onlyVisible) {
    banners = banners.filter(banner => banner.isVisible !== 0)
  }
  
  // 按排序权重降序排列
  return banners.sort((a, b) => (b.sortOrder || 0) - (a.sortOrder || 0))
})

const getFullImageUrl = (relativePath) => {
  if (!relativePath) return ''
  // 移除相对路径开头的斜杠（如果有）
  const cleanPath = relativePath.startsWith('/') 
    ? relativePath.substring(1) 
    : relativePath
  return `${import.meta.env.VITE_API_BASE_URL}${cleanPath}`
}
</script>

<template>
  <div class="banner-container">
    <el-carousel 
      v-if="validBanners.length > 0"
      :interval="interval" 
      :height="height"
      :arrow="arrow"
      :indicator-position="indicatorPosition"
      trigger="click"
    >
      <el-carousel-item v-for="(banner, index) in validBanners" :key="index">
        <div class="banner-item">
          <a 
            v-if="banner.linkUrl" 
            :href="banner.linkUrl" 
            target="_blank"
            class="banner-link"
          >
            <el-image
              :src="getFullImageUrl(banner.imageUrl)"
              :alt="banner.title || 'banner image'"
              fit="cover"
              class="banner-image"
              :style="{ height: height }"
            />
          </a>
          <el-image
            v-else
            :src="getFullImageUrl(banner.imageUrl)"
            :alt="banner.title || 'banner image'"
            fit="cover"
            class="banner-image"
            :style="{ height: height }"
          />
          
          <!-- 标题显示 -->
          <div v-if="showTitle && banner.title" class="banner-title">
            {{ banner.title }}
          </div>
        </div>
      </el-carousel-item>
    </el-carousel>
    
    <!-- 无数据时的占位 -->
    <div v-if="validBanners.length === 0" class="empty-banner">
      <el-empty description="暂无轮播图数据" />
    </div>
  </div>
</template>

<style scoped>
.banner-container {
  width: 100%;
  position: relative;
}

.banner-item {
  position: relative;
  width: 100%;
  height: 100%;
}

.banner-link {
  display: block;
  width: 100%;
  height: 100%;
}

.banner-image {
  width: 100%;
  display: block;
}

.banner-title {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: white;
  font-size: 18px;
  text-align: center;
}

.empty-banner {
  width: 100%;
  padding: 40px 0;
  background-color: #f5f7fa;
  border-radius: 4px;
}

/* 自定义指示器样式 */
:deep(.el-carousel__indicator) {
  padding: 12px 4px;
}

:deep(.el-carousel__button) {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.5);
  transition: all 0.3s;
}

:deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 16px;
  border-radius: 8px;
  background-color: #fff;
}
.banner-container {
  width: 100%;
  position: relative;
}

.banner-item {
  position: relative;
  width: 100%;
  height: 100%;
}

.banner-link {
  display: block;
  width: 100%;
  height: 100%;
}

.banner-image {
  width: 100%;
  display: block;
}

.banner-title {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: white;
  font-size: 18px;
  text-align: center;
}

.empty-banner {
  width: 100%;
  padding: 40px 0;
  background-color: #f5f7fa;
  border-radius: 4px;
}

/* 自定义指示器样式 */
:deep(.el-carousel__indicator) {
  padding: 12px 4px;
}

:deep(.el-carousel__button) {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.5);
  transition: all 0.3s;
}

:deep(.el-carousel__indicator.is-active .el-carousel__button) {
  width: 16px;
  border-radius: 8px;
  background-color: #fff;
}
</style>