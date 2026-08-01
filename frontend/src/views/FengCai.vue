<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import TeamCard from '@/components/TeamCard.vue'
import { getPublishedYears, getPublishedTeamsByYear } from '@/apis/fengcaiAPI'

const router = useRouter()

// 团队数据和分页相关变量
const allTeams = ref([]) // 当前年份下已加载的团队（真实 TeamEntity）
const years = ref([]) // 已发布团队的年份列表
const currentYear = ref('')
const currentPage = ref(1) // 当前页码
const pageSize = ref(6) // 每页显示数量，默认6个卡片

// 计算当前页显示的团队
const paginatedTeams = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return allTeams.value.slice(start, end)
})

const goToDetail = (team) => {
  // 使用真实 TeamEntity.id（团队主键），不再使用团队账号的 user.id
  router.push({
    name: 'fengcaidetail',
    params: {
      id: team.id,
      name: team.name
    }
  })
}

// 切换年份
async function handleYearChange(year) {
  currentYear.value = year
  currentPage.value = 1
  await loadTeams()
}

async function loadTeams() {
  if (!currentYear.value) return
  try {
    const d = await getPublishedTeamsByYear(currentYear.value, 1, 100)
    if (d.data.code === 200) {
      // 接口返回分页结构，兼容 content.records / content / content.list
      const c = d.data.content
      allTeams.value = Array.isArray(c) ? c : (c?.records || c?.list || [])
    } else {
      ElMessage.error(d.data.message || '加载团队失败')
    }
  } catch (e) {
    ElMessage.error('加载团队失败: ' + e.message)
  }
}

// 分页变化处理函数
const handleCurrentChange = (val) => {
  currentPage.value = val
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1 // 每页数量变化时重置到第一页
}

onMounted(async () => {
  try {
    const d = await getPublishedYears()
    if (d.data.code === 200) {
      years.value = d.data.content || []
      if (years.value.length > 0) {
        // 默认选最近年份（按 year 降序）
        const sorted = [...years.value].sort((a, b) => String(b.year).localeCompare(String(a.year)))
        await handleYearChange(sorted[0].year)
      }
    }
  } catch (e) {
    ElMessage.error('加载年份失败: ' + e.message)
  }
})
</script>

<template>
    <div class="team-container">
    <!-- 年份选择器 -->
    <div class="year-filter" v-if="years.length">
      <el-radio-group v-model="currentYear" @change="handleYearChange">
        <el-radio-button
          v-for="y in [...years].sort((a,b) => String(b.year).localeCompare(String(a.year)))"
          :key="y.year"
          :label="y.year"
        >{{ y.year }}</el-radio-button>
      </el-radio-group>
    </div>

    <div class="team-list">
      <TeamCard
        v-for="team in paginatedTeams"
        :key="team.id"
        :team="team"
        @click="goToDetail(team)"
      />
    </div>

    <el-empty v-if="allTeams.length === 0" description="该年份暂无已发布团队" />
    
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
.year-filter {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
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