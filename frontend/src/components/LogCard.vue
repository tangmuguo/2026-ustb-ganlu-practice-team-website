<script setup>
defineProps({
  log: {
    type: Object,
    required: true
  }
})
</script>

<template>
    <div class="log-card">
        <div class="log-title">{{ log.caption }}</div>
        <div>{{ log.content }}</div>
    </div>
</template>

<style scoped>
.log-card{
  overflow: hidden; /* 确保内容不超出圆角范围 */
  padding: 0; 
  cursor: pointer;
  height: 100%; /* 填充网格高度 */
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
}
.log-title{
    font-size: 18px;
    font-weight: bold;
}
</style>