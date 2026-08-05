<script setup>
// 剥离 VITE_API_BASE_URL 尾斜杠（env 均为 http://host:8080/），避免与下方 /team-content/... 拼出 // 双斜杠
// （Tomcat 对路径中的 // 直接拒绝，HTTP 400）
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');
defineProps({
  member: {
    type: Object,
    required: true
  }
})
</script>

<template>
    <div class="member-card">
        <div><img :src="`${apiBaseUrl}/team-content/image/${member.id}`"></div>
        <div>{{ member.caption }}</div>
        <div>{{ member.content }}</div>
    </div>
</template>

<style scoped>
.member-card {
  display: flex;          /* 启用 Flex 布局 */
  flex-direction: column; /* 垂直排列（图片在上，文字在下） */
  align-items: center;    /* 水平居中 */
  gap: 10px;              /* 元素之间的间距 */
  padding: 15px;          /* 卡片内边距 */
  border: 1px solid #eee; /* 可选：边框 */
  border-radius: 8px;     /* 可选：圆角 */
  max-width: 300px;       /* 限制卡片宽度 */
}

.member-card img {
  width: 300px;          /* 固定图片宽度 */
  height: 200px;         /* 固定图片高度 */
  object-fit: cover;     /* 保持图片比例（不拉伸） */
  border-radius: 50%;    /* 圆形头像（可选） */
}

.member-card div {
  text-align: center;    /* 文字居中 */
  word-break: break-word; /* 长文本自动换行 */
}
</style>