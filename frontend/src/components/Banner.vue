<script setup>
import { computed, ref, onMounted } from 'vue'
import BannerCard from '@/components/BannerCard.vue'
import { getBannerList } from '@/apis/bannerAPI'

const bannerList = ref([])
const hasVisibleBanners = computed(() => Array.isArray(bannerList.value) && bannerList.value.some((banner) => (
  banner?.imageUrl && banner.isVisible !== 0
)))

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
  <section v-if="hasVisibleBanners" class="banner-section" aria-label="甘露支教最新动态">
    <BannerCard
      :data="bannerList"
      height="clamp(260px, 36vw, 430px)"
      interval="4000"
      arrow="hover"
      indicator-position="none"
    />
  </section>
</template>


<style scoped>

.banner-section {
  width: min(1200px, calc(100% - 40px));
  height: clamp(260px, 36vw, 430px);
  margin: 24px auto 0;
  overflow: hidden;
  background: #eaf3ff;
  border: 1px solid #d9e9fc;
  border-radius: 26px;
  box-shadow: 0 18px 48px rgba(39, 97, 171, 0.12);
}

@media (max-width: 640px) {
  .banner-section {
    width: min(100% - 24px, 1200px);
    margin-top: 14px;
    border-radius: 18px;
  }
}
</style>
