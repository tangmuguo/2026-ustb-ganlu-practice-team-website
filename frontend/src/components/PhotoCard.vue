<script setup>
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
defineProps({
  photo: {
    type: Object,
    required: true
  }
})
</script>

<template>
    <div class="photo-card">
        <div><img :src="`${apiBaseUrl}${photo.imageUrl}`"></div>
    </div>
</template>

<style scoped>
.photo-card{
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