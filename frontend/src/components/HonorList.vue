<script setup>
import HonorCard from '@/components/HonorCard.vue';
import { ref } from 'vue'
defineProps({
  honors: {
    type: Object,
    required: true
  }
})
// const honors=ref([
//     {id:1,title:"2025年优秀支教团队",content:"在2025年春季支教活动中表现突出，获得校级优秀团队称号"},
//     {id:2,title:"最佳教学创新奖",content:"在教学方法和课程设计方面具有创新性，获得教学创新奖"},
//     {id:3,title:"学生满意度最高奖",content:"在学生满意度调查中获得最高评分，深受学生喜爱"},
//     {id:4,title:"社区贡献奖",content:"在促进学校与社区关系方面做出重要贡献"},
// ])
</script>

<template>
    <div class="log-list">
        <HonorCard 
            v-for="honor in honors"
            :key="honor.id"
            :honor="honor"
        />
    </div>
</template>

<style scoped>
.log-list{
    display: flex;
    flex-direction: column;
    margin: 0 auto;
    max-width: 1200px;
    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
    gap: 20px;
    align-items: start; /* 防止卡片拉伸对齐 */
}
</style>