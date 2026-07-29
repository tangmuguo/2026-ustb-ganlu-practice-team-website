<script setup>
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
defineProps({
  team: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])
</script>

<template>
    <!-- <div class="team-card" @click="$emit('click')">
        <div class="team-name">{{ team.name }}</div>
        <div class="team-short-name"></div>
    </div> -->
    <div class="team-card" @click="$emit('click')">
        <div class="team-name">{{ team.teamname }}</div>
        <div class="team-content">
            <img :src="`${apiBaseUrl}${team.imageUrl}`" :alt="team.name" class="team-image" />
            <!-- <div class="team-description">{{ team.description }}</div> -->
        </div>
    </div>    
</template>

<style scoped>
.team-card {
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  overflow: hidden; /* 确保内容不超出圆角范围 */
  padding: 0; 
  cursor: pointer;
  height: 100%; /* 填充网格高度 */
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
}

.team-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-4px);
}

.team-name {
  font-size: 18px;
  font-weight: bold;
  color: white;
  line-height: 4.0;  
  text-align: center;
  background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
}

.team-short-name {
  color: #666;
  margin-bottom: 16px;
  font-size: 14px;
}

.team-content {
  display: flex;
  gap: 16px;
  height: 250px;
  flex-grow: 1; /* 确保描述部分填充剩余空间 */
}

.team-image {
  width: 100%;
  height:100%;
  object-fit: fill;  /*保持图片比例 */
  border-radius: 4px;
  align-self: flex-start; /* 图片顶部对齐 */
}

.team-description {
  flex: 1;
  font-size: 14px;
  line-height: 1.6;
  color: #555;
}
</style>