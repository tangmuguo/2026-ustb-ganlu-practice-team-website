<script setup>
defineProps({
  honor: {
    type: Object,
    required: true
  }
})
</script>

<template>
    <div class="honor-card">
        <div class="honor-title">{{ honor.caption }}</div>
        <div>{{ honor.content }}</div>
    </div>
</template>

<style scoped>
.honor-card{
  overflow: hidden; /* 确保内容不超出圆角范围 */
  padding: 0; 
  cursor: pointer;
  height: 100%; /* 填充网格高度 */
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
}
.honor-title{
    font-size: 18px;
    font-weight: bold;
}
</style>