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
  background:
    radial-gradient(circle at 8% 8%, rgba(131, 187, 255, 0.12), transparent 26rem),
    radial-gradient(circle at 98% 28%, rgba(169, 208, 255, 0.18), transparent 30rem),
    #f7fbff;
}

.site-main {
  flex: 1;
  min-width: 0;
}
</style>
