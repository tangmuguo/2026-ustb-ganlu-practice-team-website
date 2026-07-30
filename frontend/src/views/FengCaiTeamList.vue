<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue'
import TeamCard from '@/components/TeamCard.vue'
import { getTeamsByYear } from '@/apis/fengcaiAPI'
import { extractCollection, getErrorMessage, publishedOnly } from '@/utils/fengcai'

const route = useRoute()
const router = useRouter()
const teams = ref([])
const loading = ref(true)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = 9
const total = ref(0)
const year = computed(() => String(route.params.year || ''))

async function loadTeams() {
  if (!/^\d{4}$/.test(year.value)) {
    teams.value = []
    total.value = 0
    loading.value = false
    errorMessage.value = '年份格式不正确，请返回年份页重新选择'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getTeamsByYear(year.value, {
      page: currentPage.value,
      size: pageSize,
    })
    const collection = extractCollection(response, ['teams'])
    const publishedTeams = publishedOnly(collection.items)
    teams.value = publishedTeams
    total.value = collection.total
  } catch (error) {
    teams.value = []
    total.value = 0
    errorMessage.value = getErrorMessage(error, `${year.value} 年小队加载失败，请稍后重试`)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/fengcai')
}

function goToTeam(team) {
  const teamId = team.id ?? team.teamId
  if (teamId === null || teamId === undefined) return
  router.push(`/fengcai/team/${teamId}`)
}

watch(year, () => {
  currentPage.value = 1
  loadTeams()
}, { immediate: true })

watch(currentPage, loadTeams)
</script>

<template>
  <main class="team-list-page">
    <section class="team-list-hero">
      <button type="button" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回年份
      </button>
      <p class="eyebrow">{{ year }} GANLU TEAMS</p>
      <h1>{{ year }} 年支教小队</h1>
      <p>每一支队伍，都带着真诚奔赴远方；点击卡片，继续阅读他们的支教故事。</p>
    </section>

    <section class="team-list-content" :aria-labelledby="`team-list-${year}`">
      <div class="section-heading">
        <div>
          <p class="eyebrow">TEAMS</p>
          <h2 :id="`team-list-${year}`">小队名录</h2>
        </div>
        <span v-if="!loading && !errorMessage">共 {{ total }} 支已发布小队</span>
      </div>

      <div v-if="loading" class="team-grid" aria-label="小队列表加载中" aria-busy="true">
        <div v-for="index in 6" :key="index" class="team-skeleton">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" class="team-skeleton__image" />
              <div class="team-skeleton__body">
                <el-skeleton-item variant="h3" style="width: 58%" />
                <el-skeleton-item variant="text" style="width: 75%" />
                <el-skeleton-item variant="text" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>

      <el-result
        v-else-if="errorMessage"
        icon="error"
        title="小队列表加载失败"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" :icon="RefreshRight" @click="loadTeams">重新加载</el-button>
          <el-button @click="goBack">返回年份页</el-button>
        </template>
      </el-result>

      <el-empty v-else-if="!teams.length" :description="`${year} 年暂时没有已发布的小队`">
        <el-button type="primary" @click="goBack">查看其他年份</el-button>
      </el-empty>

      <template v-else>
        <div class="team-grid">
          <TeamCard
            v-for="team in teams"
            :key="team.id ?? team.teamId"
            :team="team"
            @click="goToTeam"
          />
        </div>

        <el-pagination
          v-if="total > pageSize"
          v-model:current-page="currentPage"
          class="pagination"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
        />
      </template>
    </section>
  </main>
</template>

<style scoped>
.team-list-page {
  min-height: 70vh;
  color: #183b58;
  background: linear-gradient(180deg, #eef7fd 0, #fff 470px);
}

.team-list-hero,
.team-list-content {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
}

.team-list-hero {
  padding: 42px 0 64px;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 48px;
  padding: 9px 14px;
  border: 1px solid #bcd8eb;
  border-radius: 999px;
  color: #176fae;
  font: inherit;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.72);
}

.eyebrow {
  color: #237eb8;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

h1 {
  margin-top: 8px;
  color: #113e63;
  font-size: clamp(38px, 6vw, 64px);
  font-weight: 780;
  line-height: 1.18;
}

.team-list-hero > p:last-child {
  max-width: 720px;
  margin-top: 18px;
  color: #617c91;
  font-size: 17px;
  line-height: 1.8;
}

.team-list-content {
  padding-bottom: 20px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}

.section-heading h2 {
  margin-top: 5px;
  color: #153f62;
  font-size: clamp(27px, 4vw, 36px);
  font-weight: 750;
}

.section-heading > span {
  color: #72889a;
}

.team-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
  align-items: start;
}

.team-skeleton {
  overflow: hidden;
  border: 1px solid #e4edf4;
  border-radius: 22px;
  background: #fff;
}

.team-skeleton__image {
  width: 100%;
  height: 220px;
}

.team-skeleton__body {
  display: grid;
  gap: 12px;
  padding: 22px;
}

.pagination {
  justify-content: center;
  margin-top: 34px;
}

@media (max-width: 960px) {
  .team-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .team-list-hero,
  .team-list-content {
    width: min(100% - 28px, 1180px);
  }

  .team-list-hero {
    padding-top: 28px;
  }

  .back-button {
    margin-bottom: 36px;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .team-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 400px) {
  .team-list-hero,
  .team-list-content {
    width: min(100% - 20px, 1180px);
  }
}
</style>
