import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import {
  AI_MAX_CONTEXT_LENGTH,
  AI_MAX_MESSAGE_LENGTH,
  AiRequestError,
  sendAiChat,
} from '@/apis/aiAPI'
import { userinfoStore } from '@/stores/userStore'

export const AI_MAX_CONTEXT_MESSAGES = 20
export const AI_MAX_INPUT_LENGTH = AI_MAX_MESSAGE_LENGTH

const SESSION_KEY = 'ganlu-ai-session-v1'
const VALID_ROLES = new Set(['user', 'assistant'])
const VALID_STORED_STATUSES = new Set(['sent', 'error', 'stopped'])

function createMessageId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function removeSessionMessages() {
  if (typeof sessionStorage === 'undefined') return

  try {
    sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // 浏览器禁用会话存储时，清理内存状态仍然有效。
  }
}

function resolveUserIdentity(userStore) {
  if (!userStore.isLoggedIn) return null

  const currentUser = userStore.currentUser
  if (currentUser?.id != null) return `id:${currentUser.id}`
  if (currentUser?.username) return `username:${currentUser.username}`
  return null
}

/**
 * 只保留完整的 user/assistant 问答轮次，以及最后一条尚待回答的 user 消息。
 * 当存在待回答消息时，最多保留 9 轮历史，为本轮助手回答预留第 20 个槽位。
 */
function trimConversation(sourceMessages, maxMessages = AI_MAX_CONTEXT_MESSAGES) {
  const completedRounds = []
  let pendingUser = null

  for (const message of sourceMessages) {
    if (!message || !VALID_ROLES.has(message.role)) continue

    if (message.role === 'user') {
      pendingUser = message
    } else if (pendingUser) {
      completedRounds.push([pendingUser, message])
      pendingUser = null
    }
  }

  const trailingMessages = pendingUser ? [pendingUser] : []
  const roundLimit = Math.floor((maxMessages - trailingMessages.length) / 2)
  const recentRounds = roundLimit > 0 ? completedRounds.slice(-roundLimit) : []
  return [...recentRounds.flat(), ...trailingMessages]
}

/**
 * 构造后端接口上下文：页面保留完整回答，但请求中单条最多 2000 字、总计最多 32000 字。
 * 超出总量时只从开头删除完整的 user/assistant 问答轮次，始终保留末尾本轮 user。
 */
export function buildContextMessages(sourceMessages) {
  const requestMessages = trimConversation(sourceMessages).map(({ role, content }) => ({
    role,
    content: content.slice(0, AI_MAX_MESSAGE_LENGTH),
  }))
  let contextLength = requestMessages.reduce(
    (total, message) => total + message.content.length,
    0,
  )

  while (contextLength > AI_MAX_CONTEXT_LENGTH && requestMessages.length > 1) {
    const [oldestUser, oldestAssistant] = requestMessages
    if (oldestUser?.role !== 'user' || oldestAssistant?.role !== 'assistant') break

    contextLength -= oldestUser.content.length + oldestAssistant.content.length
    requestMessages.splice(0, 2)
  }

  return requestMessages
}

function readSessionMessages(identity) {
  if (typeof sessionStorage === 'undefined') return []

  if (!identity) {
    removeSessionMessages()
    return []
  }

  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? 'null')
    if (saved?.owner !== identity || !Array.isArray(saved?.messages)) {
      removeSessionMessages()
      return []
    }

    const restoredMessages = saved.messages
      .filter(
        (message) =>
          message &&
          VALID_ROLES.has(message.role) &&
          typeof message.content === 'string' &&
          message.content.length > 0,
      )
      .map((message) => ({
        id: typeof message.id === 'string' ? message.id : createMessageId(),
        role: message.role,
        content: message.content,
        status: VALID_STORED_STATUSES.has(message.status) ? message.status : 'sent',
      }))
    return trimConversation(restoredMessages)
  } catch {
    removeSessionMessages()
    return []
  }
}

function writeSessionMessages(messages, identity) {
  if (typeof sessionStorage === 'undefined') return

  try {
    if (!identity || !messages.length) {
      removeSessionMessages()
      return
    }

    const necessaryMessages = trimConversation(messages).map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      status: message.status === 'sending' ? 'stopped' : message.status,
    }))
    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({ owner: identity, messages: necessaryMessages }),
    )
  } catch {
    // sessionStorage 可能被浏览器策略或容量限制禁用；会话仍保留在当前 Pinia 内存中。
  }
}

function fallbackError(error) {
  if (error instanceof AiRequestError) return error
  return new AiRequestError('请求失败，请稍后重试')
}

export const useAiStore = defineStore('AiAssistant', () => {
  const userStore = userinfoStore()
  const activeIdentity = ref(resolveUserIdentity(userStore))
  const messages = ref(readSessionMessages(activeIdentity.value))
  const isLoading = ref(false)
  const error = ref(null)
  const retryableMessageId = ref(
    [...messages.value].reverse().find((message) => ['error', 'stopped'].includes(message.status))
      ?.id ?? null,
  )

  let activeController = null
  let requestVersion = 0

  const hasMessages = computed(() => messages.value.length > 0)
  const contextMessages = computed(() => buildContextMessages(messages.value))

  watch(
    messages,
    (currentMessages) => writeSessionMessages(currentMessages, activeIdentity.value),
    { deep: true },
  )

  watch(
    () => resolveUserIdentity(userStore),
    (nextIdentity) => {
      if (nextIdentity === activeIdentity.value) return
      activeIdentity.value = nextIdentity
      clearConversation()
    },
    { flush: 'sync' },
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

      messages.value = trimConversation([
        ...messages.value,
        {
          id: createMessageId(),
          role: 'assistant',
          content: result.answer,
          status: 'sent',
          requestId: result.requestId,
        },
      ])
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
    messages.value = trimConversation([...messages.value, userMessage])
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
    removeSessionMessages()
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
