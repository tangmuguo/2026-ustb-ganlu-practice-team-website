<script setup>
import LogCard from '@/components/LogCard.vue'

defineProps({
  logs: {
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
  <div v-if="logs.length" class="log-list">
    <LogCard
      v-for="(log, index) in logs"
      :key="log.id ?? `log-${index}`"
      :log="log"
      :attachments="attachments"
    />
  </div>
  <p v-else class="empty-copy">暂无已发布的课堂日志</p>
</template>

<style scoped>
.log-list {
  display: grid;
  gap: 16px;
}

.empty-copy {
  padding: 28px 0;
  color: #7d92a4;
  text-align: center;
}
</style>
