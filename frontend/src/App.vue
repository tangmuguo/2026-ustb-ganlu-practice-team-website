<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import EmptyLayout from '@/layouts/EmptyLayout.vue'
const route = useRoute()

const layoutComponent = computed(() => {
  // 根据路由 meta 返回对应的布局组件，默认用 DefaultLayout
  return route.meta.layout === 'EmptyLayout' 
    ? EmptyLayout 
    : route.meta.layout === 'AdminLayout'
    ? AdminLayout
    : DefaultLayout
})
</script>

<template>
  <component :is="layoutComponent">
    <RouterView />  <!-- 当前路由对应的页面内容 -->
  </component>
</template>

<style scoped>

</style>
