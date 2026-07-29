<script setup>
import Top from '@/components/Top.vue';
import Banner from '@/components/Banner.vue';
import Foot from '@/components/Foot.vue';
import { useRoute } from 'vue-router';
</script>

<template>
  <Top></Top>
  <Banner v-if="!useRoute().meta.hideBanner"/>
  <main>
      <slot></slot>  <!-- 页面内容插入点 -->
  </main>
  <Foot></Foot>
</template>

<style scoped>

</style>