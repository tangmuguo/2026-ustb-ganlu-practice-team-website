import axios from 'axios'
import instance from '@/utils/http'

const STATUS_MESSAGES = {
  400: '输入内容不符合要求，请检查后重试',
  401: '登录状态已失效，请重新登录',
  429: '请求较多，请稍后再试',
  502: 'AI 服务暂时不可用',
  503: 'AI 服务暂时不可用',
  504: 'AI 服务暂时不可用',
}

export class AiRequestError extends Error {
  constructor(message, { status = null, kind = 'unknown' } = {}) {
    super(message)
    this.name = 'AiRequestError'
    this.status = status
    this.kind = kind
  }
}

function normalizeRequestError(error) {
  if (axios.isCancel(error) || error?.code === 'ERR_CANCELED') {
    return new AiRequestError('已停止生成，你可以重新发送这条问题', {
      kind: 'cancelled',
    })
  }

  if (error?.code === 'ECONNABORTED') {
    return new AiRequestError('AI 服务响应超时，请稍后重试', {
      status: 504,
      kind: 'service',
    })
  }

  const status = error?.response?.status ?? error?.response?.data?.code ?? null
  if (status) {
    return new AiRequestError(
      STATUS_MESSAGES[status] ?? error?.response?.data?.message ?? '请求失败，请稍后重试',
      {
        status,
        kind: status === 401 ? 'auth' : status === 400 ? 'input' : 'service',
      },
    )
  }

  if (!error?.response) {
    return new AiRequestError('网络连接失败，请检查网络后重试', {
      kind: 'network',
    })
  }

  return new AiRequestError('请求失败，请稍后重试')
}

/**
 * 调用本站后端 AI 代理。请求体只保留接口合同允许的 role 和 content。
 */
export async function sendAiChat(messages, { signal } = {}) {
  const requestMessages = messages.map(({ role, content }) => ({ role, content }))

  try {
    const response = await instance({
      url: '/ai/chat',
      method: 'POST',
      data: { messages: requestMessages },
      signal,
      timeout: 70000,
    })

    const responseData = response?.data
    if (responseData?.code !== 200) {
      const status = responseData?.code ?? response?.status ?? null
      throw new AiRequestError(
        STATUS_MESSAGES[status] ?? responseData?.message ?? '请求失败，请稍后重试',
        {
          status,
          kind: status === 401 ? 'auth' : status === 400 ? 'input' : 'service',
        },
      )
    }

    const answer = responseData?.content?.answer
    if (typeof answer !== 'string' || !answer.trim()) {
      throw new AiRequestError('AI 服务暂时没有返回有效内容，请稍后重试', {
        kind: 'service',
      })
    }

    return {
      answer,
      requestId: responseData?.content?.requestId ?? null,
    }
  } catch (error) {
    if (error instanceof AiRequestError) {
      throw error
    }
    throw normalizeRequestError(error)
  }
}
