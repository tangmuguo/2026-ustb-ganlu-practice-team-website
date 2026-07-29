<script setup>
import { ref ,onMounted,computed} from 'vue'
import { useRouter } from 'vue-router'
import TeamCard from '@/components/TeamCard.vue'
import {GetAllTeams} from '@/apis/userAPI'

const router = useRouter()

// 团队数据和分页相关变量
const allTeams = ref([]) // 存储所有团队数据
const currentPage = ref(1) // 当前页码
const pageSize = ref(6) // 每页显示数量，默认6个卡片

// 计算当前页显示的团队
const paginatedTeams = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return allTeams.value.slice(start, end)
})

const goToDetail = (teamId,teamname) => {
  //router.push({ path: `/fengcaidetail/${teamId}` })
  router.push({
    name:'fengcaidetail',
    params: { 
      id: teamId,
      name:teamname
     }
  })
}

// 分页变化处理函数
const handleCurrentChange = (val) => {
  currentPage.value = val
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1 // 每页数量变化时重置到第一页
}

onMounted(async ()=>{
  const d=await GetAllTeams()
  if(d.data.code==200){
    allTeams.value=d.data.content
  }
})
</script>

<template>
    <div class="team-container">
    <div class="team-list">
      <TeamCard 
        v-for="team in paginatedTeams" 
        :key="team.id"
        :team="team"
        @click="goToDetail(team.id, team.teamname)"
      />
    </div>
    
    <!-- Element Plus 分页控件 -->
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[6, 12, 24, 48]"
      :total="allTeams.length"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
      class="pagination"
    />
  </div>
</template>

<style scoped>
.team-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.team-list {
  display: grid;
  margin: 0 auto;
  margin-top: 20px;
  max-width: 1200px;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
  align-items: start; /* 防止卡片拉伸对齐 */
}

.pagination {
  margin-top: 30px;
  justify-content: center;
  display: flex;
}
</style>