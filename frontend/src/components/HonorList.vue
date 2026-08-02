<script setup>
import HonorCard from '@/components/HonorCard.vue'

defineProps({
  honors: {
    type: Array,
    default: () => [],
  },
  attachments: {
    type: Array,
    default: () => [],
  },
})
</script>

<template>
  <div v-if="honors.length" class="honor-list">
    <HonorCard
      v-for="(honor, index) in honors"
      :key="honor.id ?? `honor-${index}`"
      :honor="honor"
      :attachments="attachments"
    />
  </div>
  <p v-else class="empty-copy">暂无已发布的荣誉记录</p>
</template>

<style scoped>
.honor-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr));
  gap: 18px;
}

.empty-copy {
  padding: 28px 0;
  color: #7d92a4;
  text-align: center;
}
</style>
