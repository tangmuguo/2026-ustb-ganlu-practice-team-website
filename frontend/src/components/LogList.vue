<script setup>
import LogCard from '@/components/LogCard.vue';
import { ref } from 'vue'
defineProps({
  logs: {
    type: Object,
    required: true
  }
})
// const logs=ref([
//     {id:1,title:"2025年1月15日 - 第一天",content:"今天是我们甘露二队到达支教点的第一天。经过长途跋涉，我们终于来到了这个美丽的乡村。孩子们的热情让我们感到温暖，虽然有些紧张，但我们相信通过努力一定能够为孩子们带来有意义的课程。"},
//     {id:2,title:"2025年1月16日 - 第二天",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。"},
//     {id:3,title:"2025年1月17日 - 第三天",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。"},
//     {id:4,title:"2025年1月18日 - 第四天",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。"},
//     {id:5,title:"2025年1月19日 - 第五天",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。"},
// ])
</script>

<template>
    <div class="log-list">
        <LogCard 
            v-for="log in logs"
            :key="log.id"
            :log="log"
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