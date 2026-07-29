<script setup>
import { ref, onMounted } from 'vue'
import BannerCard from '@/components/BannerCard.vue'
import { getBannerList } from '@/apis/bannerAPI'

const bannerList = ref([])
onMounted(async () => {
  try {
    console.log('banner页面执行')
    const { data } = await getBannerList()
    bannerList.value = data.content
  } catch (error) {
    console.error('获取轮播图失败', error)
  }
})
</script>

<template>
  <!-- 图片滚动区域 -->
    <section class="relative h-[500px] overflow-hidden">
        <div id="carousel" class="flex h-full">
            <!-- 轮播图占位区域，后期通过后端添加图片 -->
            <BannerCard 
            :data="bannerList" 
            height="500px"
            interval="3000"
            arrow="always"
            indicator-position="outside"
            />
        </div>
        <!-- 轮播控制按钮 -->
        <!-- <button id="prevBtn" class="absolute left-4 top-1/2 -translate-y-1/2 bg-white/70 hover:bg-white text-dark w-10 h-10 rounded-full flex items-center justify-center transition-colors">
            <i class="fa fa-angle-left text-xl"></i>
        </button>
        <button id="nextBtn" class="absolute right-4 top-1/2 -translate-y-1/2 bg-white/70 hover:bg-white text-dark w-10 h-10 rounded-full flex items-center justify-center transition-colors">
            <i class="fa fa-angle-right text-xl"></i>
        </button> -->
        <!-- 轮播指示器 -->
        <!-- <div class="absolute bottom-4 left-1/2 -translate-x-1/2 flex space-x-2">
            <button class="w-3 h-3 rounded-full bg-white/70 carousel-indicator active" data-index="0"></button>
        </div> -->
    </section>
</template>


<style scoped>

</style>