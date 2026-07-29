<script setup>
import {ref, computed, onMounted } from 'vue';
import MemberList from '@/components/MemberList.vue';
import LogList from '@/components/LogList.vue';
import HonorList from '@/components/HonorList.vue';
import PhotoList from '@/components/PhotoList.vue';
import { useRouter } from 'vue-router';
import { useRoute } from 'vue-router';
import {findAllImages,findAllWords} from '@/apis/fengcaiAPI'

const imagelists=ref([])
const wordlists=ref([])
const memberlist=computed(()=>{
    return imagelists.value.filter(item => item.type === 1);
})
const honorlist=computed(()=>{
    return wordlists.value.filter(item => item.type === 3);
})
const photolist=computed(()=>{
    return imagelists.value.filter(item => item.type === 2);
})
const loglist=computed(()=>{
    return wordlists.value.filter(item => item.type === 4);
})
const router = useRouter()
const route=useRoute()
const id = ref(route.params.id) // 获取动态参数 id
const name=ref(route.params.name)
const returnBack=()=>{
    router.push('/fengcai')
}

const getAllImage=async ()=>{
    const d=await findAllImages(id.value)
    if(d.data.code===200){
        imagelists.value=d.data.content
    }
}

const getAllWord=async ()=>{
    const d=await findAllWords(id.value)
    if(d.data.code===200){
        wordlists.value=d.data.content
    }
}

onMounted(() => {
  getAllImage()
  getAllWord()
})

</script>

<template>
    <nav class="navbar">
        <a class="back-btn" v-on:click="returnBack">← 返回团队风采</a>
        <h1 class="page-title">{{name}}详细介绍</h1>
        <div></div>
    </nav>
    <div class="main-container">
        <section class="team-section">
            <h2 class="section-title">队员介绍</h2>
            <div class="team-members">
                <MemberList 
                :members="memberlist"
                />
            </div>
        </section>
    </div>
    <div class="main-container1">
        <section class="team-section">
            <h2 class="section-title">团队荣誉</h2>
            <div class="team-members">
                <HonorList 
                :honors="honorlist"
                />
            </div>
        </section>
    </div>
    <div class="main-container1">
        <section class="team-section">
            <h2 class="section-title">支教照片</h2>
            <div class="team-members">
                <PhotoList 
                :photos="photolist"
                />
            </div>
        </section>
    </div>
    <div class="main-container1">
        <section class="team-section">
            <h2 class="section-title">支教日志</h2>
            <div class="team-members">
                <LogList 
                :logs="loglist"
                />
            </div>
        </section>
    </div>
    <div style="height: 50px;"></div>
</template>

<style scoped>
.navbar {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
    padding: 15px 30px;
    box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
    z-index: 1000;
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.back-btn {
    background: linear-gradient(135deg, #1976d2 0%, #1565c0 100%);
    color: white;
    border: none;
    padding: 10px 20px;
    border-radius: 25px;
    cursor: pointer;
    font-size: 16px;
    font-weight: 500;
    transition: all 0.3s ease;
    text-decoration: none;
    display: inline-block;
}
.back-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 15px rgba(25, 118, 210, 0.3);
}
.page-title {
    font-size: 24px;
    font-weight: bold;
    color: #1976d2;
}
.main-container{
    margin-top: 70px;
    padding-top: 40px;
    padding-bottom: 0px;
    padding-left: 20px;
    padding-right: 20px;
    max-width: 1200px;
    margin-left: auto;
    margin-right: auto;
}
.main-container1{
    margin-top: 0px;
    padding: 0px 20px;
    max-width: 1200px;
    margin-left: auto;
    margin-right: auto;
}
.team-section{
    background: white;
    border-radius: 20px;
    padding: 30px;
    margin-bottom: 30px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}
.section-title{
    font-size: 28px;
    font-weight: bold;
    color: #1976d2;
    margin-bottom: 25px;
    text-align: center;
    border-bottom: 3px solid #1976d2;
    padding-bottom: 15px;
}
</style>