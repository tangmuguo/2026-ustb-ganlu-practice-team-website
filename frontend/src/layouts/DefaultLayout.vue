<script setup>
import Top from '@/components/Top.vue'
import Banner from '@/components/Banner.vue'
import Foot from '@/components/Foot.vue'
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="site-shell">
    <Top />
    <Banner v-if="!route.meta.hideBanner" />
    <main class="site-main">
      <slot />
    </main>
    <Foot />
  </div>
</template>

<style scoped>
.site-shell {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: #f6f8fb;
}

.site-main {
  flex: 1;
  min-width: 0;
}
</style>
