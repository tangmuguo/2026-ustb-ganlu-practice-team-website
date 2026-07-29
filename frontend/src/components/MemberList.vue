<script setup>
import MemberCard from '@/components/MemberCard.vue';
import { ref } from 'vue'
defineProps({
  members: {
    type: Object,
    required: true
  }
})
// const members=ref([
//     {id:1,name:"张伟",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。",img:"https://wx2.sinaimg.cn/mw690/005KPAJ7ly1hxhtgzmdutj30ig0ig0tx.jpg"},
//     {id:2,name:"王芳",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。",img:"https://wx2.sinaimg.cn/mw690/005KPAJ7ly1hxhtgzmdutj30ig0ig0tx.jpg"},
//     {id:3,name:"李强",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。",img:"https://wx2.sinaimg.cn/mw690/005KPAJ7ly1hxhtgzmdutj30ig0ig0tx.jpg"},
//     {id:4,name:"刘敏",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。",img:"https://wx2.sinaimg.cn/mw690/005KPAJ7ly1hxhtgzmdutj30ig0ig0tx.jpg"},
//     {id:5,name:"陈华",content:"负责团队整体协调和教学计划制定，具有丰富的支教经验和领导能力。",img:"https://wx2.sinaimg.cn/mw690/005KPAJ7ly1hxhtgzmdutj30ig0ig0tx.jpg"},
// ])
</script>

<template>
    <div class="member-list">
        <MemberCard 
            v-for="member in members"
            :key="member.id"
            :member="member"
        />
    </div>
</template>

<style scoped>
.member-list{
    display: grid;
    margin: 0 auto;
    margin-top: 20px;
    max-width: 1200px;
    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
    gap: 20px;
    align-items: start; /* 防止卡片拉伸对齐 */
}
</style>