<template>
  <div class="empty-layout">
    <slot />
  </div>
</template>

<style scoped>
.empty-layout {
  min-height: 100vh;
  width: 100%;
  background:
    radial-gradient(circle at 10% 10%, rgba(135, 190, 255, 0.2), transparent 28rem),
    linear-gradient(135deg, #f5f9ff, #edf6ff);
}
</style>
