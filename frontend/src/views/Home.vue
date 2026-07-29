<script setup>
import {testUser} from "@/apis/networkAPI"
import NewsList from "@/components/NewsList.vue";
import { onMounted } from "vue";
onMounted(()=>{
})
</script>

<template >
  
    <main class="container mx-auto px-4 py-12">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-8 mb-12">
            <div class="bg-white rounded-lg shadow-md p-6 hover-scale">
                <div class="w-14 h-14 bg-primary/10 rounded-full flex items-center justify-center mb-4">
                    <i class="fa fa-book text-primary text-2xl"></i>
                </div>
                <h3 class="text-xl font-bold mb-3">优质课件资源</h3>
                <p class="text-gray-600">我们提供丰富多样的教学课件，覆盖多个学科，助力教师提升教学质量。</p>
                <a href="/showm" class="inline-block mt-4 text-primary font-medium hover:underline">查看课件库</a>
            </div>
            <div class="bg-white rounded-lg shadow-md p-6 hover-scale">
                <div class="w-14 h-14 bg-secondary/10 rounded-full flex items-center justify-center mb-4">
                    <i class="fa fa-users text-secondary text-2xl"></i>
                </div>
                <h3 class="text-xl font-bold mb-3">支教风采</h3>
                <p class="text-gray-600">记录支教生活的点点滴滴，分享支教故事和经验，展现志愿者的风采。</p>
                <a href="/fengcai" class="inline-block mt-4 text-secondary font-medium hover:underline">浏览风采</a>
            </div>
            <div class="bg-white rounded-lg shadow-md p-6 hover-scale">
                <div class="w-14 h-14 bg-accent/10 rounded-full flex items-center justify-center mb-4">
                    <i class="fa fa-comments text-accent text-2xl"></i>
                </div>
                <h3 class="text-xl font-bold mb-3">互动交流</h3>
                <p class="text-gray-600">搭建教师、志愿者和学生之间的交流平台，促进知识共享和情感沟通。</p>
                <a href="/messageboard" class="inline-block mt-4 text-accent font-medium hover:underline">参与互动</a>
            </div>
        </div>

        <!-- 最新新闻（后端动态添加区域） -->
        <section class="mb-12">
            <h2 class="text-2xl font-bold mb-6 flex items-center">
                <i class="fa fa-newspaper-o text-primary mr-2"></i>
                最新动态
            </h2>
            <div>
                <NewsList />
            </div>
            <!-- <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
               
                <article class="bg-white rounded-lg shadow-md overflow-hidden hover-scale">
                    <div class="h-48 bg-gray-200 flex items-center justify-center">
                        <span class="text-gray-500">图片占位</span>
                    </div>
                    <div class="p-5">
                        <div class="flex items-center text-sm text-gray-500 mb-3">
                            <i class="fa fa-calendar-o mr-2"></i>
                            <span>日期将由后期添加</span>
                        </div>
                        <h3 class="text-xl font-bold mb-2">新闻标题将由后期添加</h3>
                        <p class="text-gray-600 mb-4 line-clamp-2">新闻内容将由后端添加，此处为占位文本。</p>
                        <a href="#" class="text-primary font-medium hover:underline">阅读全文</a>
                    </div>
                </article>
                <article class="bg-white rounded-lg shadow-md overflow-hidden hover-scale">
                    <div class="h-48 bg-gray-200 flex items-center justify-center">
                        <span class="text-gray-500">图片占位</span>
                    </div>
                    <div class="p-5">
                        <div class="flex items-center text-sm text-gray-500 mb-3">
                            <i class="fa fa-calendar-o mr-2"></i>
                            <span>日期将由后期添加</span>
                        </div>
                        <h3 class="text-xl font-bold mb-2">新闻标题将由后期添加</h3>
                        <p class="text-gray-600 mb-4 line-clamp-2">新闻内容将由后期添加，此处为占位文本。</p>
                        <a href="#" class="text-primary font-medium hover:underline">阅读全文</a>
                    </div>
                </article>
                <article class="bg-white rounded-lg shadow-md overflow-hidden hover-scale">
                    <div class="h-48 bg-gray-200 flex items-center justify-center">
                        <span class="text-gray-500">图片占位</span>
                    </div>
                    <div class="p-5">
                        <div class="flex items-center text-sm text-gray-500 mb-3">
                            <i class="fa fa-calendar-o mr-2"></i>
                            <span>日期将由后期添加</span>
                        </div>
                        <h3 class="text-xl font-bold mb-2">新闻标题将由后期添加</h3>
                        <p class="text-gray-600 mb-4 line-clamp-2">新闻内容将由后期添加，此处为占位文本。</p>
                        <a href="#" class="text-primary font-medium hover:underline">阅读全文</a>
                    </div>
                </article>
            </div> -->
        </section>
    </main>
 
</template>

<style scoped>

</style>
