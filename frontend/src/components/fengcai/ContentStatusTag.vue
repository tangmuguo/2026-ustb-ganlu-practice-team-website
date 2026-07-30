<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true }
})

const tagMeta = computed(() => {
  switch (props.status) {
    case 'PENDING':
      return { text: '待审核', type: 'warning' }
    case 'PUBLISHED':
      return { text: '已发布', type: 'success' }
    case 'REJECTED':
      return { text: '已驳回', type: 'danger' }
    case 'ARCHIVED':
      return { text: '已归档', type: 'info' }
    default:
      return { text: props.status || '未知', type: 'info' }
  }
})
</script>

<template>
  <el-tag :type="tagMeta.type" size="small">{{ tagMeta.text }}</el-tag>
</template>
