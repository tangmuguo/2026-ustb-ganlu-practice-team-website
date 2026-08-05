<script setup>
// 剥离 VITE_API_BASE_URL 尾斜杠（env 均为 http://host:8080/），避免与下方 /team-content/... 拼出 // 双斜杠
// （Tomcat 对路径中的 // 直接拒绝，HTTP 400）
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');
defineProps({
  photo: {
    type: Object,
    required: true
  }
})
</script>

<template>
    <div class="photo-card">
        <div><img :src="`${apiBaseUrl}/team-content/image/${photo.id}`"></div>
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