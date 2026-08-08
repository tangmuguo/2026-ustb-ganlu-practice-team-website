<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import {
  ArrowRight,
  Calendar,
  Document,
  Link,
  Reading,
  RefreshRight,
  Search,
} from '@element-plus/icons-vue'
import { GetAllNews } from '@/apis/newsAPI'
import { formatTime } from '@/utils/date'

const PAGE_SIZE = 6
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/'

const newsItems = ref([])
const loading = ref(true)
const loadError = ref('')
const keyword = ref('')
const currentPage = ref(1)
const selectedNews = ref(null)
const readerVisible = ref(false)

const normalizedKeyword = computed(() => keyword.value.trim().toLocaleLowerCase())
const filteredNews = computed(() => {
  if (!normalizedKeyword.value) return newsItems.value

  return newsItems.value.filter((item) => {
    const searchable = `${item.caption} ${item.content}`.toLocaleLowerCase()
    return searchable.includes(normalizedKeyword.value)
  })
})
const featuredNews = computed(() => filteredNews.value[0] || null)
const headlineNews = computed(() => filteredNews.value.slice(1, 5))
const remainingNews = computed(() => filteredNews.value.slice(5))
const paginatedNews = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return remainingNews.value.slice(start, start + PAGE_SIZE)
})
const totalPages = computed(() => Math.max(1, Math.ceil(remainingNews.value.length / PAGE_SIZE)))

watch(keyword, () => {
  currentPage.value = 1
})

watch(totalPages, (pageCount) => {
  if (currentPage.value > pageCount) currentPage.value = pageCount
})

function normalizeNews(item, index) {
  return {
    id: item?.id ?? `news-${index}`,
    caption: String(item?.caption || item?.title || '未命名新闻'),
    content: String(item?.content || '暂未提供新闻正文。'),
    createAt: item?.createAt || null,
    imageUrl: item?.imageUrl || '',
    linkUrl: item?.linkUrl || '',
  }
}

function responseItems(response) {
  const data = response?.data || {}
  if (Number(data.code) === 201) return []
  if (Number(data.code) !== 200) {
    throw new Error(data.message || '新闻列表加载失败，请稍后重试')
  }
  return Array.isArray(data.content) ? data.content : []
}

async function loadNews() {
  loading.value = true
  loadError.value = ''

  try {
    const response = await GetAllNews()
    newsItems.value = responseItems(response).map(normalizeNews)
  } catch (error) {
    newsItems.value = []
    loadError.value = error?.response?.data?.message
      || error?.message
      || '新闻列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function getImageUrl(imageUrl) {
  if (!imageUrl) return ''
  if (/^https?:\/\//i.test(imageUrl)) return imageUrl

  const prefix = apiBaseUrl.endsWith('/') ? apiBaseUrl : `${apiBaseUrl}/`
  return `${prefix}${imageUrl.replace(/^\/+/, '')}`
}

function formatNewsDate(value) {
  return formatTime(value) === '时间未知' ? '发布时间待确认' : formatTime(value)
}

function truncateContent(content, length = 72) {
  return content.length > length ? `${content.slice(0, length)}…` : content
}

function getSafeLink(linkUrl) {
  const value = String(linkUrl || '').trim()
  if (!value) return ''
  if (value.startsWith('/') && !value.startsWith('//')) return value

  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : ''
  } catch {
    return ''
  }
}

function openReader(item) {
  selectedNews.value = item
  readerVisible.value = true
}

function followLink(item) {
  const safeLink = getSafeLink(item?.linkUrl)
  if (!safeLink) return

  if (safeLink.startsWith('/')) {
    window.location.assign(safeLink)
    return
  }

  window.open(safeLink, '_blank', 'noopener,noreferrer')
}

onMounted(loadNews)
</script>

<template>
  <main class="news-page">
    <section class="news-hero" aria-labelledby="news-title">
      <div class="news-hero__orb news-hero__orb--one" aria-hidden="true"></div>
      <div class="news-hero__orb news-hero__orb--two" aria-hidden="true"></div>
      <div class="news-hero__content">
        <p class="news-hero__eyebrow"><el-icon><Reading /></el-icon> GANLU NEWSROOM</p>
        <h1 id="news-title">新闻</h1>
        <p>记录课堂、团队与服务现场的每一次新消息。</p>
      </div>
      <div class="news-hero__summary" aria-label="新闻统计">
        <strong>{{ newsItems.length }}</strong>
        <span>条公开动态</span>
      </div>
    </section>

    <section class="news-content" aria-label="新闻列表">
      <div class="news-toolbar">
        <div>
          <p class="section-kicker">LATEST UPDATES</p>
          <h2>最新动态</h2>
        </div>
        <el-input
          v-model="keyword"
          class="news-search"
          :prefix-icon="Search"
          clearable
          placeholder="搜索新闻标题或内容"
          aria-label="搜索新闻"
        />
      </div>

      <section v-if="loading" class="news-overview news-overview--loading" aria-busy="true">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" class="featured-skeleton" />
            <div class="headline-skeletons">
              <el-skeleton-item v-for="index in 4" :key="index" variant="text" class="headline-skeleton" />
            </div>
          </template>
        </el-skeleton>
      </section>

      <section v-else-if="loadError" class="state-card" role="alert">
        <el-icon><Document /></el-icon>
        <h3>新闻暂时无法加载</h3>
        <p>{{ loadError }}</p>
        <el-button type="primary" :icon="RefreshRight" @click="loadNews">重新加载</el-button>
      </section>

      <section v-else-if="!filteredNews.length" class="state-card">
        <el-icon><Document /></el-icon>
        <h3>{{ newsItems.length ? '没有匹配的新闻' : '暂时还没有新闻' }}</h3>
        <p>{{ newsItems.length ? '试试更换搜索关键词。' : '新闻发布后会在这里按时间展示。' }}</p>
        <el-button v-if="keyword" plain @click="keyword = ''">清除搜索</el-button>
      </section>

      <template v-else>
        <section class="news-overview" aria-label="新闻精选">
          <button class="featured-news" type="button" @click="openReader(featuredNews)">
            <div class="featured-news__media">
              <el-image
                v-if="featuredNews.imageUrl"
                :src="getImageUrl(featuredNews.imageUrl)"
                :alt="featuredNews.caption"
                fit="cover"
              >
                <template #error>
                  <div class="image-fallback"><el-icon><Document /></el-icon></div>
                </template>
              </el-image>
              <div v-else class="image-fallback"><el-icon><Document /></el-icon></div>
              <span class="featured-news__badge">焦点新闻</span>
            </div>
            <div class="featured-news__copy">
              <p class="news-date"><el-icon><Calendar /></el-icon>{{ formatNewsDate(featuredNews.createAt) }}</p>
              <h3>{{ featuredNews.caption }}</h3>
              <p>{{ truncateContent(featuredNews.content, 120) }}</p>
              <span class="read-link">阅读详情 <el-icon><ArrowRight /></el-icon></span>
            </div>
          </button>

          <aside class="headline-panel" aria-label="新闻速递">
            <div class="headline-panel__heading">
              <span>NEWS BRIEF</span>
              <p>新闻速递</p>
            </div>
            <button
              v-for="item in headlineNews"
              :key="item.id"
              class="headline-item"
              type="button"
              @click="openReader(item)"
            >
              <span class="headline-item__date">{{ formatNewsDate(item.createAt).slice(0, 10) }}</span>
              <strong>{{ item.caption }}</strong>
              <el-icon><ArrowRight /></el-icon>
            </button>
            <p v-if="!headlineNews.length" class="headline-panel__empty">更多新闻将在发布后显示。</p>
          </aside>
        </section>

        <section v-if="remainingNews.length" class="more-news" aria-labelledby="more-news-title">
          <div class="more-news__heading">
            <div>
              <p class="section-kicker">ALL NEWS</p>
              <h2 id="more-news-title">全部新闻</h2>
            </div>
            <span>共 {{ filteredNews.length }} 条</span>
          </div>

          <div class="news-grid">
            <button
              v-for="item in paginatedNews"
              :key="item.id"
              class="news-card"
              type="button"
              @click="openReader(item)"
            >
              <div class="news-card__media">
                <el-image
                  v-if="item.imageUrl"
                  :src="getImageUrl(item.imageUrl)"
                  :alt="item.caption"
                  fit="cover"
                >
                  <template #error>
                    <div class="image-fallback"><el-icon><Document /></el-icon></div>
                  </template>
                </el-image>
                <div v-else class="image-fallback"><el-icon><Document /></el-icon></div>
              </div>
              <div class="news-card__copy">
                <p class="news-date"><el-icon><Calendar /></el-icon>{{ formatNewsDate(item.createAt) }}</p>
                <h3>{{ item.caption }}</h3>
                <p>{{ truncateContent(item.content) }}</p>
                <span class="read-link">查看详情 <el-icon><ArrowRight /></el-icon></span>
              </div>
            </button>
          </div>

          <el-pagination
            v-if="remainingNews.length > PAGE_SIZE"
            v-model:current-page="currentPage"
            class="news-pagination"
            background
            layout="prev, pager, next"
            :page-size="PAGE_SIZE"
            :total="remainingNews.length"
          />
        </section>
      </template>
    </section>

    <el-dialog
      v-model="readerVisible"
      class="news-reader"
      width="min(760px, calc(100% - 28px))"
      destroy-on-close
      @closed="selectedNews = null"
    >
      <template v-if="selectedNews" #header>
        <p class="reader-kicker"><el-icon><Reading /></el-icon> GANLU NEWS</p>
        <h2>{{ selectedNews.caption }}</h2>
        <p class="news-date"><el-icon><Calendar /></el-icon>{{ formatNewsDate(selectedNews.createAt) }}</p>
      </template>

      <article v-if="selectedNews" class="reader-content">
        <el-image
          v-if="selectedNews.imageUrl"
          :src="getImageUrl(selectedNews.imageUrl)"
          :alt="selectedNews.caption"
          fit="cover"
          class="reader-content__image"
        >
          <template #error>
            <div class="image-fallback"><el-icon><Document /></el-icon></div>
          </template>
        </el-image>
        <p>{{ selectedNews.content }}</p>
      </article>

      <template #footer>
        <el-button @click="readerVisible = false">关闭</el-button>
        <el-button
          v-if="getSafeLink(selectedNews?.linkUrl)"
          type="primary"
          :icon="Link"
          @click="followLink(selectedNews)"
        >
          打开新闻链接
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.news-page {
  min-height: 70vh;
  color: #183b58;
  background:
    radial-gradient(circle at 0 20%, rgba(132, 192, 255, 0.16), transparent 24rem),
    linear-gradient(180deg, #f3f9ff 0, #fff 520px);
}

.news-hero,
.news-content {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
}

.news-hero {
  position: relative;
  display: flex;
  min-height: 270px;
  align-items: center;
  justify-content: space-between;
  gap: 30px;
  overflow: hidden;
  padding: 48px clamp(30px, 6vw, 76px);
  border-radius: 0 0 32px 32px;
  color: #fff;
  background:
    radial-gradient(circle at 79% 0%, rgba(143, 221, 255, 0.76), transparent 26%),
    linear-gradient(133deg, #083c70, #0d6eae 62%, #1592cc);
  box-shadow: 0 20px 48px rgba(18, 82, 131, 0.2);
}

.news-hero__content {
  position: relative;
  z-index: 1;
}

.news-hero__eyebrow,
.section-kicker,
.reader-kicker {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: #2582be;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.16em;
}

.news-hero__eyebrow {
  color: #c3ecff;
}

.news-hero h1 {
  margin: 10px 0 10px;
  font-size: clamp(42px, 6vw, 68px);
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.news-hero__content > p:last-child {
  max-width: 520px;
  margin: 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: 17px;
  line-height: 1.75;
}

.news-hero__summary {
  position: relative;
  z-index: 1;
  display: grid;
  min-width: 136px;
  padding: 22px 24px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.14);
  text-align: center;
  backdrop-filter: blur(8px);
}

.news-hero__summary strong {
  font-size: 34px;
  line-height: 1;
}

.news-hero__summary span {
  margin-top: 8px;
  color: #d9f1ff;
  font-size: 12px;
}

.news-hero__orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.news-hero__orb--one {
  top: -130px;
  right: 14%;
  width: 340px;
  height: 340px;
}

.news-hero__orb--two {
  bottom: -210px;
  right: -80px;
  width: 380px;
  height: 380px;
}

.news-content {
  padding: 64px 0 76px;
}

.news-toolbar,
.more-news__heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 26px;
}

.news-toolbar {
  margin-bottom: 28px;
}

.news-toolbar h2,
.more-news h2 {
  margin: 6px 0 0;
  color: #153f62;
  font-size: clamp(28px, 4vw, 36px);
  font-weight: 760;
}

.news-search {
  width: min(340px, 100%);
}

.news-search :deep(.el-input__wrapper) {
  min-height: 42px;
  padding: 1px 14px;
  border: 1px solid #d6e6f9;
  border-radius: 13px;
  box-shadow: 0 8px 18px rgba(42, 103, 168, 0.06);
}

.news-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.8fr);
  gap: 24px;
}

.featured-news,
.headline-panel,
.news-card,
.state-card {
  border: 1px solid #dceaf8;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 16px 38px rgba(35, 91, 166, 0.08);
}

.featured-news,
.news-card {
  padding: 0;
  overflow: hidden;
  border-radius: 23px;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.featured-news:hover,
.featured-news:focus-visible,
.news-card:hover,
.news-card:focus-visible {
  border-color: #a9d0fb;
  box-shadow: 0 22px 46px rgba(35, 91, 166, 0.15);
  outline: none;
  transform: translateY(-4px);
}

.featured-news__media {
  position: relative;
  height: 296px;
  overflow: hidden;
  background: linear-gradient(140deg, #e6f3ff, #b4dbfc);
}

.featured-news__media :deep(.el-image),
.news-card__media :deep(.el-image) {
  display: block;
  width: 100%;
  height: 100%;
}

.featured-news__media :deep(img),
.news-card__media :deep(img) {
  transition: transform 0.5s ease;
}

.featured-news:hover .featured-news__media :deep(img),
.news-card:hover .news-card__media :deep(img) {
  transform: scale(1.04);
}

.featured-news__badge {
  position: absolute;
  top: 17px;
  left: 17px;
  padding: 7px 11px;
  border-radius: 999px;
  color: #135b9f;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 18px rgba(18, 68, 123, 0.12);
  font-size: 12px;
  font-weight: 750;
}

.featured-news__copy,
.news-card__copy {
  padding: 23px 25px 25px;
}

.news-date {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #7590ad;
  font-size: 12px;
  line-height: 1.4;
}

.featured-news h3,
.news-card h3 {
  margin: 10px 0 9px;
  color: #17436e;
  font-size: 22px;
  font-weight: 760;
  line-height: 1.38;
}

.featured-news__copy > p:not(.news-date),
.news-card__copy > p:not(.news-date) {
  margin: 0;
  color: #6b849d;
  font-size: 14px;
  line-height: 1.75;
}

.read-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 18px;
  color: #1f74e4;
  font-size: 13px;
  font-weight: 750;
}

.headline-panel {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  overflow: hidden;
  border-radius: 23px;
}

.headline-panel__heading {
  padding: 21px 22px 16px;
  border-bottom: 1px solid #e7eff8;
}

.headline-panel__heading span {
  color: #2b84c2;
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.15em;
}

.headline-panel__heading p {
  margin: 5px 0 0;
  color: #244d73;
  font-size: 20px;
  font-weight: 760;
}

.headline-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px 12px;
  width: 100%;
  padding: 17px 21px;
  border: 0;
  border-bottom: 1px solid #edf3fa;
  color: inherit;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.2s ease;
}

.headline-item:hover,
.headline-item:focus-visible {
  background: #f4f9ff;
  outline: none;
}

.headline-item__date {
  color: #7e98b3;
  font-size: 11px;
}

.headline-item strong {
  overflow: hidden;
  color: #315a80;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.headline-item .el-icon {
  grid-row: span 2;
  align-self: center;
  color: #76a4d9;
}

.headline-panel__empty {
  margin: auto 0;
  padding: 22px;
  color: #7c93a8;
  font-size: 13px;
  text-align: center;
}

.image-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: #4e91d3;
  background:
    radial-gradient(circle at 70% 26%, rgba(255, 255, 255, 0.72), transparent 18%),
    linear-gradient(135deg, #e6f3ff, #c2e0fb);
  font-size: 38px;
}

.more-news {
  margin-top: 54px;
}

.more-news__heading {
  margin-bottom: 22px;
}

.more-news__heading > span {
  color: #7690aa;
  font-size: 13px;
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.news-card__media {
  height: 178px;
  overflow: hidden;
  background: #e8f4ff;
}

.news-card__copy {
  padding: 19px 20px 20px;
}

.news-card h3 {
  display: -webkit-box;
  min-height: 2.76em;
  overflow: hidden;
  font-size: 17px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.news-card__copy > p:not(.news-date) {
  display: -webkit-box;
  min-height: 3.5em;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.news-card .read-link {
  margin-top: 13px;
}

.news-pagination {
  justify-content: center;
  margin-top: 32px;
}

.state-card {
  padding: 54px 24px;
  border-style: dashed;
  border-radius: 22px;
  text-align: center;
}

.state-card > .el-icon {
  display: inline-grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 16px;
  color: #2d7fe4;
  background: #e7f2ff;
  font-size: 25px;
}

.state-card h3 {
  margin: 16px 0 6px;
  color: #234c72;
  font-size: 20px;
}

.state-card p {
  margin: 0 auto 20px;
  color: #6f879f;
  font-size: 14px;
}

.news-overview--loading {
  align-items: stretch;
}

.featured-skeleton {
  width: 100%;
  height: 410px;
  border-radius: 23px;
}

.headline-skeletons {
  display: grid;
  gap: 18px;
  padding: 28px 22px;
  border: 1px solid #dceaf8;
  border-radius: 23px;
  background: #fff;
}

.headline-skeleton {
  height: 48px;
}

.reader-kicker {
  color: #2e81c2;
}

.news-reader :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 22px;
}

.news-reader h2 {
  margin: 8px 28px 8px 0;
  color: #16436d;
  font-size: 25px;
  font-weight: 760;
  line-height: 1.4;
}

.reader-content__image {
  display: block;
  width: 100%;
  max-height: 380px;
  overflow: hidden;
  border-radius: 14px;
  background: #eaf4fd;
}

.reader-content > p {
  margin: 22px 2px 0;
  color: #45627d;
  font-size: 15px;
  line-height: 2;
  white-space: pre-wrap;
}

@media (max-width: 880px) {
  .news-overview {
    grid-template-columns: 1fr;
  }

  .headline-panel {
    min-height: auto;
  }

  .headline-item {
    padding: 15px 18px;
  }

  .news-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .news-hero,
  .news-content {
    width: min(100% - 24px, 1180px);
  }

  .news-hero {
    min-height: 250px;
    align-items: flex-start;
    flex-direction: column;
    padding: 38px 24px;
    border-radius: 0 0 24px 24px;
  }

  .news-hero__summary {
    min-width: 0;
    padding: 13px 18px;
    text-align: left;
  }

  .news-hero__summary strong,
  .news-hero__summary span {
    display: inline;
  }

  .news-hero__summary span {
    margin-left: 7px;
  }

  .news-content {
    padding: 42px 0 56px;
  }

  .news-toolbar,
  .more-news__heading {
    align-items: stretch;
    flex-direction: column;
    gap: 17px;
  }

  .news-search {
    width: 100%;
  }

  .featured-news__media {
    height: 220px;
  }

  .featured-news__copy,
  .news-card__copy {
    padding: 19px;
  }

  .featured-news h3 {
    font-size: 20px;
  }

  .more-news {
    margin-top: 42px;
  }

  .news-grid {
    grid-template-columns: 1fr;
  }
}
</style>
