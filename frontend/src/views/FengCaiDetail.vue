<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Document, RefreshRight, VideoCamera, Warning } from '@element-plus/icons-vue'
import MemberList from '@/components/MemberList.vue'
import HonorList from '@/components/HonorList.vue'
import PhotoList from '@/components/PhotoList.vue'
import LogList from '@/components/LogList.vue'
import ReportContentDialog from '@/components/ReportContentDialog.vue'
import TeamHero from '@/components/fengcai/TeamHero.vue'
import TeamDetailSkeleton from '@/components/fengcai/TeamDetailSkeleton.vue'
import { getPublicTeamContent, getTeamDetail } from '@/apis/fengcaiAPI'
import {
  getErrorMessage,
  isPublished,
  publishedOnly,
  resolveAttachmentUrl,
  resolveMediaUrl,
  uniqueItems,
  unwrapApiData,
} from '@/utils/fengcai'

const route = useRoute()
const router = useRouter()
const team = ref(null)
const members = ref([])
const honors = ref([])
const regionPhotos = ref([])
const teachingPhotos = ref([])
const logs = ref([])
const attachments = ref([])
const loading = ref(true)
const errorMessage = ref('')
const contentWarning = ref('')
const notFound = ref(false)
const reportDialogVisible = ref(false)

// 兼容旧路由参数名 id，但所有数据查询都按 teamId 发起。
const teamId = computed(() => String(route.params.teamId ?? route.params.id ?? ''))

const otherAttachments = computed(() => attachments.value.filter((attachment) => {
  const relatedId = attachment.relatedId
    ?? attachment.contentId
    ?? attachment.honorId
    ?? attachment.logId
  const relatedType = String(attachment.relatedType || attachment.contentType || '').toUpperCase()
  return (relatedId === null || relatedId === undefined || relatedId === '')
    && !relatedType.includes('HONOR')
    && !relatedType.includes('LOG')
}))

const reportableTargets = computed(() => {
  const groups = [
    ['队员照片', 'TEAM_IMAGE', members.value],
    ['支教地区照片', 'TEAM_IMAGE', regionPhotos.value],
    ['教学互动照片', 'TEAM_IMAGE', teachingPhotos.value],
    ['荣誉记录', 'TEAM_WORD', honors.value],
    ['课堂日志', 'TEAM_WORD', logs.value],
    ['团队附件', 'TEAM_MEDIA', attachments.value],
  ]
  const seen = new Set()
  const targets = []
  groups.forEach(([label, targetType, items]) => {
    const safeItems = Array.isArray(items) ? items : []
    safeItems.forEach((item) => {
      const numericId = Number(item?.id ?? item?.mediaId)
      if (!Number.isInteger(numericId) || numericId < 1) return
      const key = `${targetType}:${numericId}`
      if (seen.has(key)) return
      seen.add(key)
      targets.push({ key, targetType, targetId: numericId, label: `${label}（#${numericId}）` })
    })
  })
  return targets
})

function openReportDialog() {
  if (reportableTargets.value.length) reportDialogVisible.value = true
}

function itemType(item) {
  return String(item?.type ?? item?.contentType ?? item?.category ?? '').toUpperCase()
}

function arraysFrom(sources, keys) {
  return uniqueItems(sources.flatMap((source) => {
    if (!source || typeof source !== 'object') return []
    return keys.flatMap((key) => Array.isArray(source[key]) ? source[key] : [])
  }))
}

function normalizeTeam(raw) {
  const source = raw?.team && typeof raw.team === 'object'
    ? { ...raw, ...raw.team }
    : raw

  const description = source.description || source.summary || source.introduction || source.content || ''

  return {
    ...source,
    id: source.id ?? source.teamId ?? teamId.value,
    name: source.name || source.teamName || source.teamname || '甘露支教小队',
    year: source.year || source.teachingYear || '',
    region: source.region || source.teachingRegion || source.location || '',
    school: source.school || source.primarySchool || source.teachingSchool || '',
    description,
    overview: source.overview || source.story || source.fullDescription || description,
    coverUrl: source.coverUrl || source.cover || source.imageUrl || source.thumbnailUrl || '',
    pageId: source.pageId ?? source.teamPageId ?? raw?.pageId,
  }
}

function hydrateContent(detailPayload, contentPayload) {
  const nestedSources = [
    detailPayload,
    detailPayload?.content,
    detailPayload?.page,
    detailPayload?.teamPage,
    contentPayload,
    contentPayload?.content,
    contentPayload?.data,
    contentPayload?.photos,
    contentPayload?.words,
  ].filter((source) => source && typeof source === 'object')

  const genericImages = arraysFrom(nestedSources, ['images', 'imageList', 'photos'])
  const genericWords = arraysFrom(nestedSources, ['words', 'wordList', 'texts'])

  members.value = publishedOnly(uniqueItems([
    ...arraysFrom(nestedSources, ['members', 'memberList']),
    ...genericImages.filter((item) => ['MEMBER', '1'].includes(itemType(item))),
  ]))

  honors.value = publishedOnly(uniqueItems([
    ...arraysFrom(nestedSources, ['honors', 'honorList']),
    ...genericWords.filter((item) => ['HONOR', '3'].includes(itemType(item))),
  ]))

  regionPhotos.value = publishedOnly(uniqueItems([
    ...arraysFrom(nestedSources, ['regionPhotos', 'regionPhotoList', 'areaPhotos']),
    ...genericImages.filter((item) => ['REGION_PHOTO', 'REGION', '2'].includes(itemType(item))),
  ]))

  teachingPhotos.value = publishedOnly(uniqueItems([
    ...arraysFrom(nestedSources, ['teachingPhotos', 'teachingPhotoList', 'interactionPhotos']),
    ...genericImages.filter((item) => ['TEACHING_PHOTO', 'TEACHING', 'INTERACTION_PHOTO'].includes(itemType(item))),
  ]))

  logs.value = publishedOnly(uniqueItems([
    ...arraysFrom(nestedSources, ['logs', 'classLogs', 'logList']),
    ...genericWords.filter((item) => ['CLASS_LOG', 'LOG', '4'].includes(itemType(item))),
  ]))

  attachments.value = publishedOnly(arraysFrom(nestedSources, [
    'attachments',
    'attachmentList',
    'media',
    'mediaList',
    'files',
  ]))
}

async function loadDetail() {
  if (!teamId.value) {
    loading.value = false
    errorMessage.value = '团队编号缺失，请从团队列表重新进入'
    return
  }

  loading.value = true
  errorMessage.value = ''
  contentWarning.value = ''
  notFound.value = false

  const [detailResult, contentResult] = await Promise.allSettled([
    getTeamDetail(teamId.value),
    getPublicTeamContent(teamId.value),
  ])

  try {
    if (detailResult.status === 'rejected') throw detailResult.reason
    const detailPayload = unwrapApiData(detailResult.value)
    const normalizedTeam = normalizeTeam(detailPayload)
    if (!isPublished(normalizedTeam)) {
      const unpublishedError = new Error('没有找到对应的团队风采内容')
      unpublishedError.code = 404
      throw unpublishedError
    }
    team.value = normalizedTeam

    let contentPayload = null
    if (contentResult.status === 'fulfilled') {
      try {
        contentPayload = unwrapApiData(contentResult.value)
      } catch (error) {
        contentWarning.value = getErrorMessage(error, '团队内容暂时无法加载')
      }
    } else {
      contentWarning.value = getErrorMessage(contentResult.reason, '团队内容暂时无法加载')
    }

    hydrateContent(detailPayload, contentPayload)
  } catch (error) {
    team.value = null
    hydrateContent(null, null)
    notFound.value = Number(error?.response?.status ?? error?.code) === 404
    errorMessage.value = getErrorMessage(error, '团队详情加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goBack() {
  if (team.value?.year) {
    router.push(`/fengcai/${team.value.year}`)
    return
  }
  router.push('/fengcai')
}

function attachmentName(attachment) {
  return attachment.fileName || attachment.originalName || attachment.name || attachment.title || '团队附件'
}

function attachmentUrl(attachment) {
  return resolveAttachmentUrl(attachment)
}

function isVideo(attachment) {
  const mime = String(attachment.mimeType || attachment.contentType || '').toLowerCase()
  const kind = String(attachment.mediaType || attachment.type || '').toUpperCase()
  return mime.startsWith('video/') || kind === 'VIDEO'
}

function formatFileSize(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes <= 0) return ''
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

watch(teamId, loadDetail, { immediate: true })
</script>

<template>
  <main class="detail-page">
    <div class="detail-shell">
      <button type="button" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回{{ team?.year ? `${team.year} 年小队` : '团队风采' }}
      </button>

      <TeamDetailSkeleton v-if="loading" />

      <el-result
        v-else-if="errorMessage"
        :icon="notFound ? 'warning' : 'error'"
        :title="notFound ? '没有找到这支小队' : '团队详情加载失败'"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" :icon="RefreshRight" @click="loadDetail">重新加载</el-button>
          <el-button @click="router.push('/fengcai')">返回年份页</el-button>
        </template>
      </el-result>

      <template v-else-if="team">
        <TeamHero :team="team" />

        <section class="report-entry" aria-label="公开内容举报">
          <div>
            <strong>发现公开内容存在问题？</strong>
            <p>请选择具体内容提交举报。提交时无需填写电话、姓名或其他联系方式。</p>
          </div>
          <el-button
            type="warning"
            plain
            :icon="Warning"
            :disabled="!reportableTargets.length"
            @click="openReportDialog"
          >
            举报公开内容
          </el-button>
        </section>

        <el-alert
          v-if="contentWarning"
          class="content-alert"
          type="warning"
          :title="contentWarning"
          description="团队基本信息已显示；成员、照片、日志或附件可能暂时不完整。"
          show-icon
          :closable="false"
        >
          <template #default>
            <el-button size="small" @click="loadDetail">重试内容加载</el-button>
          </template>
        </el-alert>

        <section class="detail-section overview-section" aria-labelledby="overview-title">
          <div class="section-title-row">
            <span>01</span>
            <div>
              <p>ABOUT THE TEAM</p>
              <h2 id="overview-title">团队概况</h2>
            </div>
          </div>
          <p class="overview-copy">{{ team.overview || '团队概况正在整理中，敬请期待。' }}</p>
        </section>

        <section class="detail-section" aria-labelledby="members-title">
          <div class="section-title-row">
            <span>02</span>
            <div>
              <p>MEMBERS</p>
              <h2 id="members-title">队员</h2>
            </div>
          </div>
          <MemberList :members="members" />
        </section>

        <section class="detail-section" aria-labelledby="honors-title">
          <div class="section-title-row">
            <span>03</span>
            <div>
              <p>HONORS</p>
              <h2 id="honors-title">荣誉成就</h2>
            </div>
          </div>
          <HonorList :honors="honors" :attachments="attachments" />
        </section>

        <section class="detail-section" aria-labelledby="region-title">
          <div class="section-title-row">
            <span>04</span>
            <div>
              <p>THE PLACE</p>
              <h2 id="region-title">支教地区照片</h2>
            </div>
          </div>
          <PhotoList
            :photos="regionPhotos"
            category="支教地区"
            empty-text="暂无已发布的支教地区照片"
          />
        </section>

        <section class="detail-section" aria-labelledby="teaching-title">
          <div class="section-title-row">
            <span>05</span>
            <div>
              <p>IN THE CLASSROOM</p>
              <h2 id="teaching-title">教学互动照片</h2>
            </div>
          </div>
          <PhotoList
            :photos="teachingPhotos"
            category="教学互动"
            empty-text="暂无已发布的教学互动照片"
          />
        </section>

        <section class="detail-section" aria-labelledby="logs-title">
          <div class="section-title-row">
            <span>06</span>
            <div>
              <p>CLASS LOGS</p>
              <h2 id="logs-title">课堂日志</h2>
            </div>
          </div>
          <LogList :logs="logs" :attachments="attachments" />
        </section>

        <section class="detail-section" aria-labelledby="attachments-title">
          <div class="section-title-row">
            <span>07</span>
            <div>
              <p>DOWNLOADS</p>
              <h2 id="attachments-title">其他附件</h2>
            </div>
          </div>

          <div v-if="otherAttachments.length" class="attachment-list">
            <article
              v-for="(attachment, index) in otherAttachments"
              :key="attachment.id ?? `attachment-${index}`"
              class="attachment-card"
            >
              <div class="attachment-card__icon">
                <el-icon><VideoCamera v-if="isVideo(attachment)" /><Document v-else /></el-icon>
              </div>
              <div class="attachment-card__content">
                <h3>{{ attachmentName(attachment) }}</h3>
                <p>
                  {{ isVideo(attachment) ? '视频文件 · 仅支持下载查看' : '团队资料附件' }}
                  <span v-if="formatFileSize(attachment.fileSize || attachment.size)">
                    · {{ formatFileSize(attachment.fileSize || attachment.size) }}
                  </span>
                </p>
              </div>
              <a
                v-if="attachmentUrl(attachment)"
                class="download-button"
                :href="attachmentUrl(attachment)"
                target="_blank"
                rel="noopener"
                download
              >
                <el-icon><Download /></el-icon>
                下载
              </a>
            </article>
          </div>
          <p v-else class="empty-copy">暂无已发布的其他附件</p>
        </section>

        <ReportContentDialog
          v-model="reportDialogVisible"
          :targets="reportableTargets"
        />
      </template>
    </div>
  </main>
</template>

<style scoped>
.detail-page {
  min-height: 70vh;
  padding: 30px 0 20px;
  color: #183b58;
  background: linear-gradient(180deg, #f1f8fc 0, #fff 620px);
}

.detail-shell {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
}

.back-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 24px;
  padding: 9px 14px;
  border: 1px solid #bcd8eb;
  border-radius: 999px;
  color: #176fae;
  font: inherit;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.78);
}

.content-alert {
  margin-top: 24px;
}

.report-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 24px;
  padding: 16px 18px;
  border: 1px solid #f0dfba;
  border-radius: 16px;
  color: #6d5426;
  background: #fffaf0;
}

.report-entry strong {
  color: #60491f;
  font-size: 14px;
}

.report-entry p {
  margin-top: 4px;
  color: #8a7449;
  font-size: 13px;
  line-height: 1.6;
}

.detail-section {
  margin-top: 26px;
  padding: clamp(24px, 4vw, 38px);
  border: 1px solid #e3edf5;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 34px rgba(30, 78, 119, 0.07);
}

.section-title-row {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 26px;
}

.section-title-row > span {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  place-items: center;
  border-radius: 14px;
  color: #1470ad;
  font-size: 13px;
  font-weight: 750;
  background: #e8f5fd;
}

.section-title-row p {
  color: #3f8ab8;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.section-title-row h2 {
  margin-top: 2px;
  color: #153f62;
  font-size: clamp(24px, 3vw, 31px);
  font-weight: 750;
}

.overview-copy {
  color: #5f778a;
  font-size: 16px;
  line-height: 2;
  white-space: pre-wrap;
}

.attachment-list {
  display: grid;
  gap: 12px;
}

.attachment-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 16px 18px;
  border: 1px solid #e2edf5;
  border-radius: 16px;
  background: #f9fcfe;
}

.attachment-card__icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  border-radius: 12px;
  color: #1672ae;
  font-size: 22px;
  background: #e7f4fd;
}

.attachment-card__content {
  min-width: 0;
  flex: 1;
}

.attachment-card h3 {
  overflow: hidden;
  margin: 0;
  color: #173f61;
  font-size: 16px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-card p {
  margin-top: 4px;
  color: #758b9c;
  font-size: 13px;
}

.download-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding: 8px 13px;
  border-radius: 10px;
  color: #fff;
  text-decoration: none;
  background: #1675b5;
}

.empty-copy {
  padding: 28px 0;
  color: #7d92a4;
  text-align: center;
}

@media (max-width: 600px) {
  .detail-page {
    padding-top: 20px;
  }

  .detail-shell {
    width: min(100% - 24px, 1180px);
  }

  .detail-section {
    margin-top: 18px;
    border-radius: 20px;
  }

  .attachment-card {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .report-entry {
    align-items: flex-start;
    flex-direction: column;
  }

  .report-entry .el-button {
    width: 100%;
  }

  .attachment-card__content {
    width: calc(100% - 58px);
    flex: auto;
  }

  .download-button {
    justify-content: center;
    width: 100%;
  }
}

@media (max-width: 400px) {
  .detail-shell {
    width: min(100% - 16px, 1180px);
  }
}
</style>
