<template>
  <div class="admin-layout">
    <Top />
    <main class="admin-main">
      <slot />
    </main>
    <Foot />
  </div>
</template>

<script setup>
import Top from '@/components/Top.vue'
import Foot from '@/components/Foot.vue'
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: #f2f5f9;
}

.admin-main {
  flex: 1;
  min-width: 0;
}
</style>
