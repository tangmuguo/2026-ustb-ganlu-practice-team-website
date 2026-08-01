<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import TeamCard from '@/components/TeamCard.vue'
import { getPublishedYears, getPublishedTeamsByYear } from '@/apis/fengcaiAPI'

const router = useRouter()

// 团队数据和分页相关变量
const allTeams = ref([]) // 当前页的团队（真实 TeamEntity）
const totalCount = ref(0) // 后端返回的总数（用于分页，不再用本地长度）
const years = ref([]) // 已发布团队的年份列表
const currentYear = ref('')
const currentPage = ref(1) // 当前页码
const pageSize = ref(6) // 每页显示数量，默认6个卡片

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
    // 服务端分页：每次翻页都重新拉取对应页数据，不再本地一次性截断
    const d = await getPublishedTeamsByYear(currentYear.value, currentPage.value, pageSize.value)
    if (d.data.code === 200) {
      const c = d.data.content
      // 后端返回 Map：{ items, page, size, total, totalPages }
      // content 也可能是数组（兼容直接返回列表的情况）
      allTeams.value = Array.isArray(c) ? c : (c?.items || [])
      totalCount.value = Array.isArray(c) ? c.length : (c?.total ?? 0)
    } else {
      ElMessage.error(d.data.message || '加载团队失败')
    }
  } catch (e) {
    ElMessage.error('加载团队失败: ' + e.message)
  }
}

// 分页变化处理函数：触发服务端重新拉取
const handleCurrentChange = (val) => {
  currentPage.value = val
  loadTeams()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1 // 每页数量变化时重置到第一页
  loadTeams()
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
        v-for="team in allTeams"
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
      :total="totalCount"
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