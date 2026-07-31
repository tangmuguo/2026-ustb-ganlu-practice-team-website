import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { AiRequestError, sendAiChat } from '@/apis/aiAPI'

export const AI_MAX_CONTEXT_MESSAGES = 20
export const AI_MAX_INPUT_LENGTH = 2000

const SESSION_KEY = 'ganlu-ai-session-v1'
const VALID_ROLES = new Set(['user', 'assistant'])
const VALID_STORED_STATUSES = new Set(['sent', 'error', 'stopped'])

function createMessageId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function readSessionMessages() {
  if (typeof sessionStorage === 'undefined') return []

  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? '[]')
    if (!Array.isArray(saved)) return []

    return saved
      .filter(
        (message) =>
          message &&
          VALID_ROLES.has(message.role) &&
          typeof message.content === 'string' &&
          message.content.length > 0,
      )
      .slice(-AI_MAX_CONTEXT_MESSAGES)
      .map((message) => ({
        id: typeof message.id === 'string' ? message.id : createMessageId(),
        role: message.role,
        content: message.content.slice(0, 50000),
        status: VALID_STORED_STATUSES.has(message.status) ? message.status : 'sent',
      }))
  } catch {
    return []
  }
}

function writeSessionMessages(messages) {
  if (typeof sessionStorage === 'undefined') return

  try {
    if (!messages.length) {
      sessionStorage.removeItem(SESSION_KEY)
      return
    }

    const necessaryMessages = messages.slice(-AI_MAX_CONTEXT_MESSAGES).map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content.slice(0, 50000),
      status: message.status === 'sending' ? 'stopped' : message.status,
    }))
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(necessaryMessages))
  } catch {
    // sessionStorage 可能被浏览器策略或容量限制禁用；会话仍保留在当前 Pinia 内存中。
  }
}

function fallbackError(error) {
  if (error instanceof AiRequestError) return error
  return new AiRequestError('请求失败，请稍后重试')
}

export const useAiStore = defineStore('AiAssistant', () => {
  const messages = ref(readSessionMessages())
  const isLoading = ref(false)
  const error = ref(null)
  const retryableMessageId = ref(
    [...messages.value].reverse().find((message) => ['error', 'stopped'].includes(message.status))
      ?.id ?? null,
  )

  let activeController = null
  let requestVersion = 0

  const hasMessages = computed(() => messages.value.length > 0)
  const contextMessages = computed(() =>
    messages.value
      .slice(-AI_MAX_CONTEXT_MESSAGES)
      .map(({ role, content }) => ({ role, content })),
  )

  watch(
    messages,
    (currentMessages) => writeSessionMessages(currentMessages),
    { deep: true },
  )

  function discardPreviousFailure() {
    if (retryableMessageId.value) {
      messages.value = messages.value.filter(
        (message) => message.id !== retryableMessageId.value,
      )
    }
    retryableMessageId.value = null
    error.value = null
  }

  async function requestAnswer(userMessageId) {
    const version = ++requestVersion
    activeController = new AbortController()
    isLoading.value = true

    try {
      const result = await sendAiChat(contextMessages.value, {
        signal: activeController.signal,
      })
      if (version !== requestVersion) return { ok: false, cancelled: true }

      const userMessage = messages.value.find((message) => message.id === userMessageId)
      if (userMessage) userMessage.status = 'sent'

      messages.value.push({
        id: createMessageId(),
        role: 'assistant',
        content: result.answer,
        status: 'sent',
        requestId: result.requestId,
      })
      messages.value = messages.value.slice(-AI_MAX_CONTEXT_MESSAGES)
      error.value = null
      retryableMessageId.value = null
      return { ok: true }
    } catch (caughtError) {
      if (version !== requestVersion) return { ok: false, cancelled: true }

      const normalizedError = fallbackError(caughtError)
      const userMessage = messages.value.find((message) => message.id === userMessageId)
      if (userMessage) {
        userMessage.status = normalizedError.kind === 'cancelled' ? 'stopped' : 'error'
      }
      error.value = {
        message: normalizedError.message,
        kind: normalizedError.kind,
        status: normalizedError.status,
      }
      retryableMessageId.value = userMessageId
      return { ok: false, error: error.value }
    } finally {
      if (version === requestVersion) {
        isLoading.value = false
        activeController = null
      }
    }
  }

  async function sendMessage(rawContent) {
    if (isLoading.value) {
      return {
        ok: false,
        error: { message: '正在处理上一条问题，请稍候', kind: 'busy' },
      }
    }

    const content = typeof rawContent === 'string' ? rawContent.trim() : ''
    if (!content) {
      return {
        ok: false,
        error: { message: '请输入问题内容', kind: 'input' },
      }
    }
    if (content.length > AI_MAX_INPUT_LENGTH) {
      return {
        ok: false,
        error: { message: `问题不能超过 ${AI_MAX_INPUT_LENGTH} 字`, kind: 'input' },
      }
    }

    if (retryableMessageId.value) {
      const previousMessage = messages.value.find(
        (message) => message.id === retryableMessageId.value,
      )
      if (previousMessage?.content === content) {
        return retryMessage(previousMessage.id)
      }
      discardPreviousFailure()
    }

    const userMessage = {
      id: createMessageId(),
      role: 'user',
      content,
      status: 'sending',
    }
    messages.value.push(userMessage)
    messages.value = messages.value.slice(-AI_MAX_CONTEXT_MESSAGES)
    return requestAnswer(userMessage.id)
  }

  async function retryMessage(messageId) {
    if (isLoading.value || messageId !== retryableMessageId.value) {
      return {
        ok: false,
        error: { message: '这条问题当前无法重试', kind: 'busy' },
      }
    }

    const message = messages.value.find((item) => item.id === messageId)
    if (!message || message.role !== 'user') {
      return {
        ok: false,
        error: { message: '未找到需要重试的问题', kind: 'input' },
      }
    }

    message.status = 'sending'
    error.value = null
    return requestAnswer(message.id)
  }

  function stopRequest() {
    if (!isLoading.value || !activeController) return
    activeController.abort()
  }

  function clearConversation() {
    requestVersion += 1
    if (activeController) activeController.abort()
    activeController = null
    isLoading.value = false
    messages.value = []
    error.value = null
    retryableMessageId.value = null
    if (typeof sessionStorage !== 'undefined') {
      try {
        sessionStorage.removeItem(SESSION_KEY)
      } catch {
        // 浏览器禁用会话存储时，清理内存状态仍然有效。
      }
    }
  }

  return {
    messages,
    isLoading,
    error,
    retryableMessageId,
    hasMessages,
    sendMessage,
    retryMessage,
    stopRequest,
    clearConversation,
  }
})
