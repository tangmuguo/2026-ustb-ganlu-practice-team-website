<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshRight } from '@element-plus/icons-vue'
import YearCard from '@/components/fengcai/YearCard.vue'
import { getTeamYears } from '@/apis/fengcaiAPI'
import { extractCollection, getErrorMessage, publishedOnly } from '@/utils/fengcai'

const router = useRouter()
const years = ref([])
const loading = ref(true)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = 6

const paginatedYears = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return years.value.slice(start, start + pageSize)
})

function normalizeYear(item) {
  if (typeof item === 'string' || typeof item === 'number') {
    return { year: String(item), teamCount: 0, coverUrl: '' }
  }

  return {
    ...item,
    year: String(item.year ?? item.teachingYear ?? ''),
    teamCount: Number(item.teamCount ?? item.publishedTeamCount ?? item.count ?? 0),
    coverUrl: item.coverUrl || item.cover || item.imageUrl || item.thumbnailUrl || '',
  }
}

async function loadYears() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getTeamYears()
    const { items } = extractCollection(response, ['years'])
    years.value = publishedOnly(items)
      .map(normalizeYear)
      .filter((item) => /^\d{4}$/.test(item.year))
      .sort((a, b) => Number(b.year) - Number(a.year))
    currentPage.value = 1
  } catch (error) {
    years.value = []
    errorMessage.value = getErrorMessage(error, '年份列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goToYear(year) {
  router.push(`/fengcai/${year}`)
}

onMounted(loadYears)
</script>

<template>
  <main class="fengcai-page">
    <section class="fengcai-intro">
      <div class="fengcai-intro__content">
        <p class="eyebrow">GANLU TEAM ARCHIVE</p>
        <h1>循着年份，遇见每一程甘露</h1>
        <p class="fengcai-intro__description">
          从一间课堂到一片乡土，回看甘露支教小队留下的足迹、成长与温暖故事。
        </p>
      </div>
      <div class="fengcai-intro__year" aria-hidden="true">{{ years[0]?.year || '甘露' }}</div>
    </section>

    <section class="year-section" aria-labelledby="year-section-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">YEARS</p>
          <h2 id="year-section-title">选择一个年份</h2>
        </div>
        <p>按年份倒序展示，仅包含已发布的小队风采。</p>
      </div>

      <div v-if="loading" class="year-grid" aria-label="年份列表加载中" aria-busy="true">
        <div v-for="index in 4" :key="index" class="year-skeleton">
          <el-skeleton animated>
            <template #template>
              <el-skeleton-item variant="image" class="year-skeleton__image" />
            </template>
          </el-skeleton>
        </div>
      </div>

      <el-result
        v-else-if="errorMessage"
        icon="error"
        title="年份加载失败"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" :icon="RefreshRight" @click="loadYears">重新加载</el-button>
        </template>
      </el-result>

      <el-empty v-else-if="!years.length" description="暂时没有已发布的团队年份" />

      <template v-else>
        <div class="year-grid">
          <YearCard
            v-for="item in paginatedYears"
            :key="item.year"
            :year="item"
            @select="goToYear"
          />
        </div>

        <el-pagination
          v-if="years.length > pageSize"
          v-model:current-page="currentPage"
          class="pagination"
          :page-size="pageSize"
          :total="years.length"
          layout="prev, pager, next"
          background
        />
      </template>
    </section>
  </main>
</template>

<style scoped>
.fengcai-page {
  min-height: 70vh;
  color: #183b58;
  background: linear-gradient(180deg, #f3f9fd 0, #fff 500px);
}

.fengcai-intro,
.year-section {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
}

.fengcai-intro {
  position: relative;
  display: flex;
  min-height: 330px;
  align-items: center;
  overflow: hidden;
  padding: 58px clamp(28px, 6vw, 76px);
  border-radius: 0 0 32px 32px;
  color: #fff;
  background:
    radial-gradient(circle at 80% 10%, rgba(91, 196, 255, 0.7), transparent 28%),
    radial-gradient(circle at 100% 100%, rgba(74, 155, 221, 0.6), transparent 34%),
    linear-gradient(135deg, #083c70 0%, #0c69aa 60%, #1189c8 100%);
  box-shadow: 0 20px 48px rgba(18, 82, 131, 0.2);
}

.fengcai-intro__content {
  position: relative;
  z-index: 1;
  max-width: 680px;
}

.eyebrow {
  color: #2582be;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.fengcai-intro .eyebrow {
  color: #bde8ff;
}

h1 {
  margin-top: 10px;
  font-size: clamp(36px, 5vw, 58px);
  font-weight: 780;
  line-height: 1.18;
  letter-spacing: -0.02em;
}

.fengcai-intro__description {
  max-width: 600px;
  margin-top: 22px;
  color: rgba(255, 255, 255, 0.84);
  font-size: 17px;
  line-height: 1.8;
}

.fengcai-intro__year {
  position: absolute;
  right: -18px;
  bottom: -58px;
  color: rgba(255, 255, 255, 0.09);
  font-size: clamp(110px, 18vw, 220px);
  font-weight: 800;
  line-height: 1;
}

.year-section {
  padding: 72px 0 20px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 28px;
}

.section-heading h2 {
  margin-top: 5px;
  color: #153f62;
  font-size: clamp(28px, 4vw, 38px);
  font-weight: 750;
}

.section-heading > p {
  max-width: 420px;
  color: #6d8497;
  text-align: right;
}

.year-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.year-skeleton {
  overflow: hidden;
  border-radius: 24px;
}

.year-skeleton__image {
  width: 100%;
  height: 310px;
}

.pagination {
  justify-content: center;
  margin-top: 34px;
}

@media (max-width: 720px) {
  .fengcai-intro,
  .year-section {
    width: min(100% - 28px, 1180px);
  }

  .fengcai-intro {
    min-height: 310px;
    padding: 42px 24px;
  }

  .year-section {
    padding-top: 52px;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .section-heading > p {
    text-align: left;
  }

  .year-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 400px) {
  .fengcai-intro,
  .year-section {
    width: min(100% - 20px, 1180px);
  }
}
</style>
