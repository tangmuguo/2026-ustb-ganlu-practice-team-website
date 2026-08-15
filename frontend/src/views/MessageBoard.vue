<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Loading,
  Lock,
  RefreshRight,
  UserFilled
} from '@element-plus/icons-vue'
import {
  addMessage,
  addReply,
  deleteMessage,
  deleteReply,
  getMessages
} from '@/apis/messageAPI'
import { createContentReport } from '@/apis/contentSafetyAPI'
import MessageComposer from '@/components/message/MessageComposer.vue'
import MessageItem from '@/components/message/MessageItem.vue'
import MessageListSkeleton from '@/components/message/MessageListSkeleton.vue'
import {
  createLatestRequestGuard,
  finishPending,
  startPending
} from '@/utils/messageState'
import { userinfoStore } from '@/stores/userStore'

const PAGE_SIZE = 10
const MESSAGE_MAX_LENGTH = 500
const REPLY_MAX_LENGTH = 300

const router = useRouter()
const userStore = userinfoStore()
const listAnchor = ref(null)

const messages = ref([])
const newMessage = ref('')
const replyDrafts = ref({})
const currentPage = ref(1)
const total = ref(0)
const loading = ref(true)
const refreshing = ref(false)
const hasLoaded = ref(false)
const loadError = ref('')
const submittingMessage = ref(false)
const submittingReplyIds = ref(new Set())
const deletingMessageId = ref(null)
const deletingReplyId = ref(null)
const fetchGuard = createLatestRequestGuard()

const isLoggedIn = computed(() => Boolean(userStore.isLoggedIn))
const userLevel = computed(() => {
  const parsed = Number(userStore.currentUser?.level)
  return Number.isInteger(parsed) ? parsed : null
})
const canPublish = computed(() => (
  isLoggedIn.value && (
    [0, 1].includes(userLevel.value)
    || (userLevel.value === 2 && userStore.currentUser?.interactiveContentEnabled === true)
  )
))
const canDelete = computed(() => isLoggedIn.value)
const isModerator = computed(() => userLevel.value === 0)
const currentUserId = computed(() => Number(userStore.currentUser?.id) || null)
const currentDisplayName = computed(() => (
  userStore.currentUser?.teamname
  || userStore.currentUser?.displayName
  || userStore.currentUser?.username
  || '注册用户'
))
const currentRoleName = computed(() => {
  if (userLevel.value === 0) return '系统管理员'
  if (userLevel.value === 1) return '甘露团队'
  if (userLevel.value === 2) return '学生账号'
  return '注册用户'
})
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const listSummary = computed(() => {
  if (!total.value) return '还没有留言'
  return `共 ${total.value} 条留言 · 第 ${currentPage.value}/${totalPages.value} 页`
})

function getTextLength(value) {
  return Array.from(value || '').length
}

function getErrorStatus(error) {
  return Number(error?.response?.status || error?.businessCode || 0)
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message
    || error?.businessMessage
    || error?.message
    || fallback
}

function ensureSuccessfulResponse(response, fallbackMessage) {
  const code = response?.data?.code
  if (code !== undefined && Number(code) !== 200) {
    const error = new Error(response?.data?.message || fallbackMessage)
    error.businessCode = Number(code)
    error.businessMessage = response?.data?.message || fallbackMessage
    throw error
  }
  return response?.data
}

function normalizeListResponse(response) {
  const data = ensureSuccessfulResponse(response, '获取留言失败')
  const content = data?.content || {}
  const items = Array.isArray(content.messages) ? content.messages : []
  const parsedTotal = Number(content.total)
  return {
    items,
    total: Number.isFinite(parsedTotal) && parsedTotal >= 0 ? parsedTotal : 0
  }
}

function showActionError(error, fallbackMessage) {
  const status = getErrorStatus(error)
  if (status === 401 || status === 403) {
    return
  }
  ElMessage.error(getErrorMessage(error, fallbackMessage))
}

async function requestMessagePage(page) {
  return normalizeListResponse(await getMessages(page, PAGE_SIZE))
}

async function fetchMessages({ correctPage = true, scroll = false } = {}) {
  const fetchId = fetchGuard.begin()
  const firstLoad = !hasLoaded.value
  if (firstLoad) {
    loading.value = true
  } else {
    refreshing.value = true
  }
  loadError.value = ''

  try {
    const requestedPage = currentPage.value
    let result = await requestMessagePage(requestedPage)
    if (!fetchGuard.isLatest(fetchId)) return

    if (
      correctPage
      && requestedPage > 1
      && result.items.length === 0
      && result.total > 0
    ) {
      currentPage.value = Math.max(1, Math.ceil(result.total / PAGE_SIZE))
      result = await requestMessagePage(currentPage.value)
      if (!fetchGuard.isLatest(fetchId)) return
    }

    messages.value = result.items
    total.value = result.total

    if (scroll) {
      await nextTick()
      if (fetchGuard.isLatest(fetchId)) {
        listAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    }
  } catch (error) {
    if (fetchGuard.isLatest(fetchId)) {
      loadError.value = getErrorMessage(error, '暂时无法加载留言，请稍后重试')
      if (firstLoad) {
        messages.value = []
        total.value = 0
      }
    }
  } finally {
    if (fetchGuard.isLatest(fetchId)) {
      hasLoaded.value = true
      loading.value = false
      refreshing.value = false
    }
  }
}

function goToLogin() {
  router.push({
    path: '/login',
    query: { redirect: '/messageboard' }
  })
}

async function submitMessage() {
  if (submittingMessage.value) return

  if (!canPublish.value) {
    ElMessage.warning('登录后才能发布留言')
    goToLogin()
    return
  }

  const content = newMessage.value.trim()
  const length = getTextLength(content)
  if (!length) {
    ElMessage.warning('请输入留言内容')
    return
  }
  if (length > MESSAGE_MAX_LENGTH) {
    ElMessage.warning(`留言不能超过${MESSAGE_MAX_LENGTH}字`)
    return
  }

  submittingMessage.value = true
  try {
    ensureSuccessfulResponse(await addMessage(content), '留言发布失败')
    newMessage.value = ''
    ElMessage.success('留言已提交，审核通过后会公开显示')
  } catch (error) {
    showActionError(error, '留言发布失败，请稍后重试')
  } finally {
    submittingMessage.value = false
  }
}

function updateReplyDraft(messageId, value) {
  replyDrafts.value[messageId] = value
}

async function submitReply(messageId) {
  if (submittingReplyIds.value.has(messageId)) return

  if (!canPublish.value) {
    ElMessage.warning('登录后才能回复')
    goToLogin()
    return
  }

  const content = (replyDrafts.value[messageId] || '').trim()
  const length = getTextLength(content)
  if (!length) {
    ElMessage.warning('请输入回复内容')
    return
  }
  if (length > REPLY_MAX_LENGTH) {
    ElMessage.warning(`回复不能超过${REPLY_MAX_LENGTH}字`)
    return
  }

  if (!startPending(submittingReplyIds.value, messageId)) return
  try {
    ensureSuccessfulResponse(await addReply(messageId, content), '回复发布失败')
    replyDrafts.value[messageId] = ''
    ElMessage.success('回复已提交，审核通过后会公开显示')
  } catch (error) {
    showActionError(error, '回复发布失败，请稍后重试')
  } finally {
    finishPending(submittingReplyIds.value, messageId)
  }
}

async function confirmDelete(title, message) {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      closeOnClickModal: false,
      distinguishCancelAndClose: true
    })
    return true
  } catch {
    return false
  }
}

async function removalReasonFor(id, collection, title) {
  const item = collection.find((entry) => Number(entry.id) === Number(id))
  if (!isModerator.value || Number(item?.userId) === currentUserId.value) return null
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写处置原因代码（例如 HARMFUL_CONTENT 或 PRIVACY_RISK）。原文和处置记录会被保留。',
      title,
      {
        inputPattern: /^[A-Z][A-Z0-9_]{1,63}$/,
        inputErrorMessage: '请输入大写英文、数字或下划线组成的原因代码',
        confirmButtonText: '确认处置',
        cancelButtonText: '取消',
      },
    )
    return value
  } catch {
    return undefined
  }
}

async function removeMessage(id) {
  const confirmed = await confirmDelete(
    '删除留言',
    '删除后该留言及其回复将不再展示，确认继续吗？'
  )
  if (!confirmed) return
  const reasonCode = await removalReasonFor(id, messages.value, '管理员处置留言')
  if (reasonCode === undefined) return

  deletingMessageId.value = id
  try {
    ensureSuccessfulResponse(await deleteMessage(id, reasonCode), '删除留言失败')
    ElMessage.success('留言已删除')
    await fetchMessages({ correctPage: true })
  } catch (error) {
    showActionError(error, '删除留言失败，请稍后重试')
  } finally {
    deletingMessageId.value = null
  }
}

async function removeReply(id) {
  const confirmed = await confirmDelete(
    '删除回复',
    '删除后这条回复将不再展示，确认继续吗？'
  )
  if (!confirmed) return
  const replies = messages.value.flatMap((message) => message.replies || [])
  const reasonCode = await removalReasonFor(id, replies, '管理员处置回复')
  if (reasonCode === undefined) return

  deletingReplyId.value = id
  try {
    ensureSuccessfulResponse(await deleteReply(id, reasonCode), '删除回复失败')
    ElMessage.success('回复已删除')
    await fetchMessages()
  } catch (error) {
    showActionError(error, '删除回复失败，请稍后重试')
  } finally {
    deletingReplyId.value = null
  }
}

async function reportContent(targetType, targetId) {
  if (!isLoggedIn.value) {
    ElMessage.warning('登录后才能提交举报')
    goToLogin()
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      '请简要描述问题。举报工单仅供管理员处理，不会公开举报人资料。',
      '举报内容',
      { inputPlaceholder: '例如：疑似泄露个人信息', inputType: 'textarea', confirmButtonText: '提交举报', cancelButtonText: '取消' },
    )
    const response = await createContentReport({ targetType, targetId, category: 'OTHER', description: value || '' })
    const ticketId = response?.data?.content?.ticketId
    ElMessage.success(ticketId ? `举报已受理，工单编号：${ticketId}` : '举报已受理')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') showActionError(error, '举报提交失败，请稍后重试')
  }
}

function handlePageChange(page) {
  currentPage.value = page
  fetchMessages({ scroll: true })
}

onMounted(() => {
  fetchMessages()
})
</script>

<template>
  <div class="message-page">
    <section class="message-hero">
      <div class="hero-glow hero-glow-one" aria-hidden="true"></div>
      <div class="hero-glow hero-glow-two" aria-hidden="true"></div>
      <div class="hero-inner">
        <div class="hero-copy">
          <div class="hero-eyebrow">
            <el-icon><ChatDotRound /></el-icon>
            <span>甘露支教 · 互动社区</span>
          </div>
          <h1>
            <span class="headline-accent">让每一个被听见的问题</span>
            <span>都有温暖的回应</span>
          </h1>
          <p>
            在这里分享支教见闻、提出学习疑问，也可以留下一句鼓励。
            我们相信，认真倾听本身，就是教育发生的开始。
          </p>
          <div class="hero-points">
            <span>学生提问</span>
            <span>团队回应</span>
            <span>经验分享</span>
          </div>
        </div>
        <figure class="hero-visual">
          <img class="community-photo" src="/message-board-hero.jpg" alt="支教活动中的孩子们" />
        </figure>
      </div>
    </section>

    <main class="message-container">
      <MessageComposer
        v-if="canPublish"
        v-model="newMessage"
        :loading="submittingMessage"
        :display-name="currentDisplayName"
        :role-name="currentRoleName"
        @submit="submitMessage"
      />

      <section v-else class="guest-card" aria-labelledby="guest-card-title">
        <div class="guest-icon">
          <el-icon><Lock /></el-icon>
        </div>
        <div class="guest-copy">
          <p class="guest-kicker">当前为游客模式</p>
          <h2 id="guest-card-title">登录后参与互动</h2>
          <p>{{ isLoggedIn ? '学生账号需完成线下核验和监护人授权后才能发布互动内容。' : '你可以浏览已审核公开的留言和回复。登录后，符合条件的账号可参与互动。' }}</p>
          <div class="guest-features" aria-label="游客权限说明">
            <span>自由浏览</span>
            <span>{{ isLoggedIn ? '等待核验' : '登录参与' }}</span>
          </div>
        </div>
        <el-button v-if="!isLoggedIn" type="primary" size="large" :icon="UserFilled" @click="goToLogin">
          前往登录
        </el-button>
      </section>

      <section ref="listAnchor" class="message-board" aria-labelledby="message-list-title">
        <div class="list-heading">
          <div>
            <p class="list-kicker">COMMUNITY FEED · 交流广场</p>
            <h2 id="message-list-title">最新留言</h2>
          </div>
          <div class="list-summary">
            <el-icon v-if="refreshing" class="is-loading"><Loading /></el-icon>
            <span>{{ refreshing ? '正在刷新…' : listSummary }}</span>
          </div>
        </div>

        <MessageListSkeleton v-if="loading" />

        <div v-else-if="loadError && messages.length === 0" class="state-card error-state">
          <div class="state-icon">
            <el-icon><RefreshRight /></el-icon>
          </div>
          <h3>留言暂时没有加载出来</h3>
          <p>{{ loadError }}</p>
          <el-button type="primary" :icon="RefreshRight" @click="fetchMessages()">
            重新加载
          </el-button>
        </div>

        <template v-else>
          <div v-if="loadError" class="inline-error" role="alert">
            <span>{{ loadError }}</span>
            <el-button type="primary" link :icon="RefreshRight" @click="fetchMessages()">
              重试
            </el-button>
          </div>

          <el-empty
            v-if="messages.length === 0"
            class="empty-state"
            description="还没有人留言，来成为第一个分享的人吧"
            :image-size="118"
          />

          <div v-else class="message-list">
            <MessageItem
              v-for="message in messages"
              :key="message.id"
              :message="message"
              :can-reply="canPublish"
              :can-delete="canDelete"
              :can-report="isLoggedIn"
              :current-user-id="currentUserId"
              :is-moderator="isModerator"
              :reply-draft="replyDrafts[message.id] || ''"
              :reply-loading="submittingReplyIds.has(message.id)"
              :deleting-message-id="deletingMessageId"
              :deleting-reply-id="deletingReplyId"
              @update:reply-draft="updateReplyDraft(message.id, $event)"
              @submit-reply="submitReply"
              @delete-message="removeMessage"
              @delete-reply="removeReply"
              @report-message="reportContent('MESSAGE', $event)"
              @report-reply="reportContent('REPLY', $event)"
            />
          </div>

          <div v-if="total > PAGE_SIZE" class="pagination-wrap">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="PAGE_SIZE"
              :pager-count="5"
              :total="total"
              layout="prev, pager, next"
              background
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.message-page {
  min-height: 100vh;
  overflow-x: clip;
  color: #243847;
  background:
    radial-gradient(circle at 5% 24%, rgb(232 170 66 / 8%), transparent 26rem),
    radial-gradient(circle at 96% 48%, rgb(57 151 111 / 7%), transparent 25rem),
    linear-gradient(180deg, #f4f8f8 0, #fafaf7 520px, #f7f8f5 100%);
}

.message-hero {
  position: relative;
  overflow: hidden;
  color: #17344b;
  background:
    linear-gradient(112deg, #fffdf8 0%, #f5faf8 48%, #e8f4fb 100%);
}

.message-hero::before {
  position: absolute;
  top: 42px;
  left: max(30px, calc((100% - 1140px) / 2 - 48px));
  width: 92px;
  height: 92px;
  border: 1px solid rgb(30 136 229 / 11%);
  border-radius: 50%;
  background: radial-gradient(circle, rgb(232 170 66 / 14%) 0 3px, transparent 4px);
  background-size: 18px 18px;
  content: '';
  opacity: 0.8;
}

.message-hero::after {
  position: absolute;
  right: -85px;
  bottom: -150px;
  width: 330px;
  height: 330px;
  border: 54px solid rgb(30 136 229 / 6%);
  border-radius: 50%;
  content: '';
}

.hero-inner {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(390px, 470px);
  align-items: center;
  gap: 64px;
  width: min(1140px, calc(100% - 48px));
  min-height: 438px;
  margin: 0 auto;
  padding: 48px 0 68px;
}

.hero-copy {
  max-width: 610px;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 13px;
  border: 1px solid rgb(30 136 229 / 17%);
  border-radius: 999px;
  color: #1e78bd;
  background: rgb(255 255 255 / 76%);
  box-shadow: 0 8px 24px rgb(42 83 106 / 6%);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.hero-copy h1 {
  display: grid;
  margin: 19px 0 16px;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: clamp(36px, 4.3vw, 54px);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.045em;
}

.headline-accent {
  color: #17344b;
}

.hero-copy h1 span:last-child {
  position: relative;
  width: fit-content;
  margin-top: 1px;
  color: #1e88e5;
}

.hero-copy h1 span:last-child::after {
  position: absolute;
  right: 0;
  bottom: 3px;
  left: 0;
  z-index: -1;
  height: 10px;
  border-radius: 999px 20px 999px 30px;
  background: rgb(241 180 75 / 24%);
  content: '';
  transform: rotate(-1.2deg);
}

.hero-copy > p {
  max-width: 570px;
  margin: 0;
  color: #637683;
  font-size: 15px;
  line-height: 1.9;
}

.hero-points {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 26px;
  margin-top: 22px;
  color: #355166;
  font-size: 12px;
  font-weight: 700;
}

.hero-points span {
  position: relative;
  padding-left: 17px;
}

.hero-points span::before {
  position: absolute;
  top: 50%;
  left: 1px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #39976f;
  box-shadow: 0 0 0 4px rgb(57 151 111 / 11%);
  content: '';
  transform: translateY(-50%);
}

.hero-visual {
  position: relative;
  width: 100%;
  height: 318px;
  margin: 0;
  isolation: isolate;
}

.hero-visual::before {
  position: absolute;
  top: -18px;
  right: -20px;
  z-index: -1;
  width: 72%;
  height: 82%;
  border-radius: 18px 66px 18px 18px;
  background: #dceef7;
  content: '';
}

.hero-visual::after {
  position: absolute;
  top: -6px;
  right: 38px;
  z-index: 1;
  width: 58px;
  height: 17px;
  border-radius: 2px;
  background: rgb(243 186 85 / 88%);
  box-shadow: 0 4px 10px rgb(104 76 30 / 11%);
  content: '';
  transform: rotate(3deg);
}

.community-photo {
  position: relative;
  display: block;
  overflow: hidden;
  width: 100%;
  height: 100%;
  border: 7px solid #fff;
  border-radius: 28px 28px 84px 28px;
  object-fit: cover;
  object-position: center 48%;
  box-shadow: 0 28px 64px rgb(30 73 98 / 18%);
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
}

.hero-glow-one {
  top: -250px;
  right: 22%;
  width: 430px;
  height: 430px;
  background: rgb(255 255 255 / 76%);
}

.hero-glow-two {
  bottom: -340px;
  left: -180px;
  width: 520px;
  height: 520px;
  background: rgb(237 181 80 / 7%);
}

.message-container {
  position: relative;
  z-index: 3;
  width: min(1020px, calc(100% - 40px));
  margin: -18px auto 0;
  padding-bottom: 76px;
}

.guest-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 18px;
  overflow: hidden;
  padding: 22px 25px;
  border: 1px solid #dfe7e4;
  border-radius: 22px;
  background:
    linear-gradient(110deg, #fff 0%, #fff 67%, #f3faf7 100%);
  box-shadow: 0 18px 48px rgb(47 76 67 / 9%);
}

.guest-card::after {
  position: absolute;
  top: -52px;
  right: 120px;
  width: 130px;
  height: 130px;
  border: 22px solid rgb(226 173 70 / 7%);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.guest-icon {
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 16px;
  color: #1e88e5;
  background: linear-gradient(145deg, #f2f9fd, #e1f0f8);
  box-shadow: 0 8px 22px rgb(30 136 229 / 10%);
  font-size: 23px;
}

.guest-copy {
  min-width: 0;
  flex: 1;
}

.guest-kicker,
.list-kicker {
  margin: 0 0 3px;
  color: #1e88e5;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.12em;
}

.guest-copy h2,
.list-heading h2 {
  margin: 0;
  color: #203c4d;
}

.guest-copy h2 {
  font-size: 20px;
}

.guest-copy > p:not(.guest-kicker) {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.65;
}

.guest-features {
  display: flex;
  gap: 7px;
  margin-top: 10px;
}

.guest-features span {
  padding: 4px 8px;
  border-radius: 999px;
  color: #35745c;
  background: #eef7f2;
  font-size: 10px;
  font-weight: 700;
}

.guest-card :deep(.el-button) {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  border-radius: 11px;
  box-shadow: 0 8px 20px rgb(37 99 235 / 16%);
}

.message-board {
  scroll-margin-top: 90px;
  margin-top: 36px;
}

.list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 17px;
  padding: 0 3px;
}

.list-heading h2 {
  font-size: 28px;
  letter-spacing: -0.025em;
}

.list-summary {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 11px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  color: #64748b;
  background: rgb(255 255 255 / 72%);
  font-size: 13px;
  box-shadow: 0 5px 16px rgb(15 23 42 / 4%);
}

.message-list {
  display: grid;
  gap: 15px;
}

.inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border: 1px solid #fecaca;
  border-radius: 12px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
}

.state-card {
  padding: 56px 24px;
  border: 1px dashed #bfdbfe;
  border-radius: 20px;
  text-align: center;
  background:
    radial-gradient(circle at 50% 0, rgb(219 234 254 / 60%), transparent 40%),
    rgb(255 255 255 / 86%);
}

.state-icon {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 0 auto 16px;
  place-items: center;
  border-radius: 18px;
  color: #2563eb;
  background: #dbeafe;
  font-size: 26px;
}

.state-card h3 {
  margin: 0;
  color: #1e293b;
  font-size: 19px;
}

.state-card p {
  max-width: 500px;
  margin: 8px auto 20px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.empty-state {
  padding: 42px 20px;
  border: 1px dashed #bfdbfe;
  border-radius: 20px;
  background:
    radial-gradient(circle at 50% 0, rgb(219 234 254 / 52%), transparent 42%),
    rgb(255 255 255 / 86%);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

.pagination-wrap :deep(.el-pagination) {
  max-width: 100%;
}

.pagination-wrap :deep(.btn-prev),
.pagination-wrap :deep(.btn-next),
.pagination-wrap :deep(.number) {
  border-radius: 9px !important;
}

@media (max-width: 1020px) {
  .hero-inner {
    grid-template-columns: minmax(0, 1fr) 390px;
    gap: 36px;
  }
}

@media (max-width: 840px) {
  .hero-inner {
    grid-template-columns: 1fr;
    gap: 38px;
    padding: 44px 0 88px;
  }

  .hero-copy {
    max-width: 650px;
  }

  .hero-visual {
    width: min(560px, calc(100% - 14px));
    height: 330px;
    margin: 0 auto;
  }
}

@media (max-width: 640px) {
  .message-page {
    background:
      radial-gradient(circle at 0 28%, rgb(232 170 66 / 7%), transparent 20rem),
      linear-gradient(180deg, #f5f9f7 0, #fafaf7 500px);
  }

  .hero-inner,
  .message-container {
    width: min(100% - 24px, 960px);
  }

  .hero-inner {
    gap: 30px;
    padding: 30px 2px 74px;
  }

  .hero-copy h1 {
    margin-top: 15px;
    font-size: 34px;
    line-height: 1.2;
  }

  .hero-copy > p {
    font-size: 14px;
    line-height: 1.75;
  }

  .hero-points {
    gap: 10px 20px;
    margin-top: 17px;
    font-size: 11px;
  }

  .hero-points span {
    padding-left: 15px;
  }

  .hero-points span::before {
    left: 1px;
    width: 5px;
    height: 5px;
  }

  .hero-visual {
    width: calc(100% - 8px);
    height: 230px;
  }

  .hero-visual::before {
    top: -11px;
    right: -10px;
  }

  .hero-visual::after {
    right: 25px;
  }

  .community-photo {
    border-width: 5px;
    border-radius: 22px 22px 58px 22px;
  }

  .message-container {
    margin-top: -10px;
    padding-bottom: 52px;
  }

  .guest-card {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 20px 16px;
    border-radius: 18px;
  }

  .guest-icon {
    width: 44px;
    height: 44px;
    flex-basis: 44px;
    border-radius: 13px;
  }

  .guest-copy {
    width: calc(100% - 62px);
    flex: 1 1 calc(100% - 62px);
  }

  .guest-card :deep(.el-button) {
    width: 100%;
  }

  .guest-card::after {
    display: none;
  }

  .message-board {
    margin-top: 32px;
  }

  .list-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }

  .list-heading h2 {
    font-size: 24px;
  }

  .list-summary {
    padding: 5px 9px;
    font-size: 12px;
  }

  .inline-error {
    align-items: flex-start;
  }

  .pagination-wrap {
    overflow: hidden;
  }
}
</style>
