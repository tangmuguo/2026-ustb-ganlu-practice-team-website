<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import {userinfoStore} from "@/stores/userStore"
import { ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const router=useRouter()
const activeIndex = ref(route.path)
const userInfo=userinfoStore()
const displayName = computed(() => userInfo.currentUser?.teamname
  || userInfo.currentUser?.realname
  || userInfo.currentUser?.username
  || '用户')

// 监听路由变化，更新激活菜单项
watch(
  () => route.path,
  (newPath) => {
    activeIndex.value = newPath
  }
)

const handleSelect = (index) => {
  activeIndex.value = index
}
const onLogout=()=>{
  userInfo.clearUser()
  router.replace('/')
}
const onLunBo=()=>{
  router.push('/mbanner')
}
const onTeam=()=>{
  router.push('/muser')
}
const onUpload=()=>{
  router.push('/uppt')
}
const onMember=()=>{
  router.push('/photo')
}
const onLog=()=>{
  router.push('/logh')
}
const onManage = ()=>{
  router.push('/mmanage')
}
const onStudent= ()=>{
  router.push('/mstudent')
}
const onNews = ()=>{
  router.push('/mnews')
}
</script>

<template>
  <header class="sticky top-0 z-50 bg-primary text-white shadow-md">
        <div class="container mx-auto px-4 py-3 flex justify-between items-center">
            <!-- 甘露支教图标占位区域 -->
            <div class="flex items-center">
                <!-- 替换为提供的图标 -->
                <img src="../images/甘露.png" alt="新图标" class="w-12 h-12 mr-2">
                <a href="../index.html" class="font-bold text-2xl">甘露支教</a>
            </div>
            
            <!-- 导航链接 -->
            <nav class="hidden md:flex ml-8">
                <div class="menu-container">
                    <el-menu
                    :default-active="activeIndex"
                    class="custom-menu"
                    mode="horizontal"
                    @select="handleSelect"
                    ellipsis="false"
                    router
                    >
                        <el-menu-item index="/">首页</el-menu-item>
                        <el-menu-item index="/showm">课件</el-menu-item>
                        <el-menu-item index="/fengcai">风采</el-menu-item>
                        <el-menu-item index="/messageboard">互动</el-menu-item>
                        <el-menu-item v-if="!userInfo.user.content" index="/login">登录</el-menu-item>
                        <el-menu-item v-else-if="userInfo.user.content.level===0">
                          <el-dropdown>                            
                            <span class="el-dropdown-link">
                              <el-tooltip :content="displayName" placement="right" v-if="displayName.length > 5">
                                <span>{{displayName.substring(0, 5)}}...</span>
                              </el-tooltip>
                              <span v-else>{{displayName}}</span>
                              <el-icon class="el-icon--right">
                                <arrow-down />
                              </el-icon>
                            </span>
                            <template #dropdown>
                              <el-dropdown-menu>
                                <el-dropdown-item @click="onLunBo">轮播图管理</el-dropdown-item>
                                <el-dropdown-item @click="onNews">新闻管理</el-dropdown-item>
                                <el-dropdown-item @click="onTeam">团队管理</el-dropdown-item>
                                <el-dropdown-item @click="onStudent">学生管理</el-dropdown-item>
                                <el-dropdown-item @click="onUpload">课件上传</el-dropdown-item>
                                <el-dropdown-item @click="onManage">课件管理</el-dropdown-item>
                                <el-dropdown-item @click="onLog">日志与荣誉</el-dropdown-item>
                                <el-dropdown-item @click="onMember">执教与队员</el-dropdown-item>
                                <el-dropdown-item @click="onLogout">注销</el-dropdown-item>
                              </el-dropdown-menu>
                            </template>
                          </el-dropdown>
                        </el-menu-item>
                        <el-menu-item v-else-if="userInfo.user.content.level===1">
                          <el-dropdown>                            
                            <span class="el-dropdown-link">                              
                              <el-tooltip :content="displayName" placement="right" v-if="displayName.length > 5">
                                <span>{{displayName.substring(0, 5)}}...</span>
                              </el-tooltip>
                              <span v-else>{{displayName}}</span>
                              <el-icon class="el-icon--right">
                                <arrow-down />
                              </el-icon>
                            </span>
                            <template #dropdown>
                              <el-dropdown-menu>
                                <el-dropdown-item @click="onStudent">学生管理</el-dropdown-item>
                                <el-dropdown-item @click="onUpload">课件上传</el-dropdown-item>
                                <el-dropdown-item @click="onManage">课件管理</el-dropdown-item>
                                <el-dropdown-item @click="onLog">日志与荣誉</el-dropdown-item>
                                <el-dropdown-item @click="onMember">执教与队员</el-dropdown-item>
                                <el-dropdown-item @click="onLogout">注销</el-dropdown-item>
                              </el-dropdown-menu>
                            </template>
                          </el-dropdown>
                        </el-menu-item>
                        <el-menu-item v-else>
                          <el-dropdown>                
                            <span class="el-dropdown-link">
                              <el-tooltip :content="displayName" placement="right" v-if="displayName.length > 5">
                                <span>{{displayName.substring(0, 5)}}...</span>
                              </el-tooltip>
                              <span v-else>{{displayName}}</span>
                              <el-icon class="el-icon--right">
                                <arrow-down />
                              </el-icon>
                            </span>
                            <template #dropdown>
                              <el-dropdown-menu>
                                <el-dropdown-item @click="onLogout">注销</el-dropdown-item>
                              </el-dropdown-menu>
                            </template>
                          </el-dropdown>
                        </el-menu-item>
                    </el-menu>
                </div>                
            </nav>
            
            <!-- 移动端菜单按钮 -->
            <!-- <button class="md:hidden text-white text-xl">
                <i class="fa fa-bars"></i>
            </button> -->
        </div>
    </header>
</template>

<style scoped>
.menu-container {
  padding: 0 20px;
  min-width: 600px;
  background-color: #1e88e5 ;  /*添加蓝色背景 */
}

.custom-menu {
  border-bottom: none;
  background-color: transparent; /* 使菜单背景透明，继承容器颜色 */
}

/* 登录项靠右 */
.login-item {
  margin-left: auto !important;
}

/* 菜单项样式 */
.el-menu-item {
  font-size: 16px;
  color: white !important; /* 文字改为白色 */
  height: 60px;
  line-height: 60px;
  padding: 0 20px;
}

/* 激活菜单项样式 */
.el-menu-item.is-active {
  color: white !important;
  border-bottom: 2px solid white; /* 白色下划线 */
}

/* 悬停效果 */
.el-menu-item:not(.is-active):hover {
  color: white !important;
  background-color: rgba(255, 255, 255, 0.2) !important; /* 半透明白色悬停效果 */
  border-bottom: 2px solid white;
}
.el-dropdown-link{
  cursor: pointer;
  font-size: 16px;
  color: white !important; /* 文字改为白色 */
  line-height: 60px;
  display: flex;
  align-items: center;
}
</style>
