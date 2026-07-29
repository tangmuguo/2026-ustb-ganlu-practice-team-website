<script setup>
import PhotoCard from '@/components/PhotoCard.vue';
import { ref } from 'vue'
defineProps({
  photos: {
    type: Object,
    required: true
  }
})
// const photos=ref([
//     {id:1,img:"https://wx4.sinaimg.cn/mw690/008FcfBfgy1i0pq107y6zj30k00jkt9a.jpg"},
//     {id:2,img:"https://wx4.sinaimg.cn/mw690/006fLFOwgy1i2amknu1sxj30zo0zo44u.jpg"},
//     {id:3,img:"https://wx2.sinaimg.cn/mw690/005KPAJ7ly1hxhtgzmdutj30ig0ig0tx.jpg"},
//     {id:4,img:"https://q2.itc.cn/q_70/images03/20250627/b0fbfaaf55ea497ebbd42cb1e0288a23.jpeg"},
//     {id:5,img:"https://wx1.sinaimg.cn/mw690/008EXW6Ngy1i2t305g51kj30sg0sgtbc.jpg"},
// ])
</script>

<template>
    <div class="member-list">
        <PhotoCard 
            v-for="photo in photos"
            :key="photo.id"
            :photo="photo"
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