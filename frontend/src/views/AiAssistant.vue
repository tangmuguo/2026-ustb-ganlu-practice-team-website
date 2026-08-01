<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AiComposer from '@/components/ai/AiComposer.vue'
import AiMessageBubble from '@/components/ai/AiMessageBubble.vue'
import AiWelcome from '@/components/ai/AiWelcome.vue'
import { useAiStore } from '@/stores/aiStore'
import { userinfoStore } from '@/stores/userStore'

const router = useRouter()
const userStore = userinfoStore()
const aiStore = useAiStore()
const draft = ref('')
const messageListRef = ref(null)
const composerRef = ref(null)

const isLoggedIn = computed(() => userStore.isLoggedIn)

async function scrollToBottom() {
  await nextTick()
  const messageList = messageListRef.value
  if (messageList) {
    messageList.scrollTo({ top: messageList.scrollHeight, behavior: 'smooth' })
  }
}

watch(
  () => [aiStore.messages.length, aiStore.isLoading],
  () => scrollToBottom(),
)

watch(
  () => [userStore.isLoggedIn, userStore.currentUser?.id, userStore.currentUser?.username],
  () => {
    draft.value = ''
  },
  { flush: 'sync' },
)

function chooseExample(question) {
  draft.value = question
  nextTick(() => composerRef.value?.focus())
}

async function sendMessage() {
  const submittedContent = draft.value
  const result = await aiStore.sendMessage(submittedContent)
  if (result.cancelled) return

  if (result.ok) {
    draft.value = ''
    return
  }

  draft.value = submittedContent
  if (result.error?.kind === 'input' || result.error?.kind === 'busy') {
    ElMessage.warning(result.error.message)
  }
}

async function retryMessage(messageId) {
  const message = aiStore.messages.find((item) => item.id === messageId)
  const result = await aiStore.retryMessage(messageId)
  if (result.cancelled) return

  if (result.ok) {
    draft.value = ''
  } else if (message) {
    draft.value = message.content
  }
}

function stopRequest() {
  aiStore.stopRequest()
}

async function confirmClear() {
  if (!aiStore.hasMessages && !draft.value) return

  try {
    await ElMessageBox.confirm(
      '当前对话内容将从本次浏览器会话中清除，是否继续？',
      '开始新对话',
      {
        confirmButtonText: '确认清空',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    aiStore.clearConversation()
    draft.value = ''
    nextTick(() => composerRef.value?.focus())
  } catch {
    // 用户取消清空时保留当前会话。
  }
}

function goToLogin() {
  router.push('/login')
}

onBeforeUnmount(() => {
  if (aiStore.isLoading) aiStore.stopRequest()
})
</script>

<template>
  <div class="ai-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">GANLU LEARNING ASSISTANT</p>
        <h1>AI 小助手</h1>
        <p class="page-description">一起梳理教学思路，把复杂知识讲得简单一些。</p>
      </div>
      <el-button v-if="isLoggedIn" plain :disabled="!aiStore.hasMessages && !draft" @click="confirmClear">
        新对话
      </el-button>
    </div>

    <section v-if="!isLoggedIn" class="login-guide" aria-labelledby="login-guide-title">
      <div class="login-illustration" aria-hidden="true">AI</div>
      <h2 id="login-guide-title">登录后开始对话</h2>
      <p>AI 小助手面向已登录用户开放。登录后可以进行连续问答，辅助备课与学习。</p>
      <el-button type="primary" size="large" @click="goToLogin">前往登录</el-button>
      <span>回答仅供学习参考，请勿提交学生隐私信息</span>
    </section>

    <section v-else class="assistant-panel" aria-label="AI 对话窗口">
      <div ref="messageListRef" class="message-list" aria-live="polite">
        <AiWelcome v-if="!aiStore.hasMessages" @select="chooseExample" />

        <template v-else>
          <AiMessageBubble
            v-for="message in aiStore.messages"
            :key="message.id"
            :message="message"
            :retryable="message.id === aiStore.retryableMessageId"
            :loading="aiStore.isLoading"
            @retry="retryMessage"
          />

          <div v-if="aiStore.isLoading" class="thinking" role="status">
            <span class="thinking-avatar" aria-hidden="true">AI</span>
            <span class="thinking-dots" aria-hidden="true"><i></i><i></i><i></i></span>
            <span>正在思考</span>
          </div>
        </template>
      </div>

      <div v-if="aiStore.error" class="error-area">
        <el-alert
          :title="aiStore.error.message"
          :type="aiStore.error.kind === 'cancelled' ? 'info' : 'error'"
          :closable="false"
          show-icon
        >
          <template v-if="aiStore.error.status === 401" #default>
            <el-button link type="primary" @click="goToLogin">重新登录</el-button>
          </template>
        </el-alert>
      </div>

      <AiComposer
        ref="composerRef"
        v-model="draft"
        :loading="aiStore.isLoading"
        :has-messages="aiStore.hasMessages"
        @send="sendMessage"
        @stop="stopRequest"
        @clear="confirmClear"
      />
    </section>
  </div>
</template>

<style scoped>
.ai-page {
  width: 100%;
  min-height: calc(100dvh - 120px);
  padding: 32px clamp(14px, 4vw, 48px) 48px;
  background:
    radial-gradient(circle at 12% 8%, rgba(59, 130, 246, 0.08), transparent 28%),
    radial-gradient(circle at 88% 4%, rgba(34, 160, 107, 0.08), transparent 24%),
    #f7f9fc;
}

.page-heading {
  display: flex;
  width: min(100%, 1100px);
  margin: 0 auto 20px;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  color: #28715a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

h1 {
  margin-top: 3px;
  color: #182230;
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 750;
  letter-spacing: -0.035em;
  line-height: 1.2;
}

.page-description {
  margin-top: 5px;
  color: #6b7585;
}

.assistant-panel,
.login-guide {
  width: min(100%, 1100px);
  margin: 0 auto;
  border: 1px solid rgba(216, 224, 233, 0.9);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 55px rgba(30, 64, 120, 0.1);
}

.assistant-panel {
  display: flex;
  height: min(720px, calc(100dvh - 220px));
  min-height: 560px;
  overflow: hidden;
  flex-direction: column;
}

.message-list {
  display: flex;
  min-height: 0;
  padding: 24px clamp(14px, 3vw, 30px);
  flex: 1 1 auto;
  flex-direction: column;
  gap: 22px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scroll-behavior: smooth;
  scrollbar-color: #c9d4e1 transparent;
  scrollbar-width: thin;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #667085;
  font-size: 13px;
}

.thinking-avatar {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.thinking-dots {
  display: inline-flex;
  gap: 4px;
}

.thinking-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7f8da1;
  animation: thinking 1.2s infinite ease-in-out;
}

.thinking-dots i:nth-child(2) {
  animation-delay: 0.14s;
}

.thinking-dots i:nth-child(3) {
  animation-delay: 0.28s;
}

.error-area {
  padding: 0 18px 2px;
  background: rgba(255, 255, 255, 0.96);
}

.login-guide {
  display: flex;
  min-height: 480px;
  padding: 54px 24px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.login-illustration {
  display: grid;
  width: 76px;
  height: 76px;
  margin-bottom: 20px;
  place-items: center;
  border-radius: 24px;
  background: linear-gradient(145deg, #e8f2ff, #e1f7ed);
  color: #2563eb;
  font-size: 21px;
  font-weight: 800;
  box-shadow: 0 14px 35px rgba(37, 99, 235, 0.13);
}

.login-guide h2 {
  color: #1f2937;
  font-size: 26px;
  font-weight: 700;
}

.login-guide p {
  max-width: 520px;
  margin: 10px 0 24px;
  color: #6b7585;
  line-height: 1.8;
}

.login-guide > span {
  margin-top: 18px;
  color: #8a94a3;
  font-size: 12px;
}

@keyframes thinking {
  0%,
  70%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  35% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (max-width: 768px) {
  .ai-page {
    padding-top: 20px;
  }

  .assistant-panel {
    height: calc(100dvh - 178px);
    min-height: 540px;
    border-radius: 18px;
  }

  .page-description {
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .ai-page {
    min-height: 100dvh;
    padding: 14px 0 0;
  }

  .page-heading {
    margin-bottom: 12px;
    padding: 0 12px;
    align-items: center;
  }

  .eyebrow,
  .page-description {
    display: none;
  }

  h1 {
    font-size: 25px;
  }

  .assistant-panel {
    height: calc(100dvh - 72px);
    min-height: 520px;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 18px 18px 0 0;
    box-shadow: none;
  }

  .message-list {
    padding: 16px 10px;
    gap: 18px;
  }

  .error-area {
    padding: 0 10px 2px;
  }

  .login-guide {
    min-height: calc(100dvh - 80px);
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 18px 18px 0 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .thinking-dots i {
    animation: none;
  }
}
</style>
