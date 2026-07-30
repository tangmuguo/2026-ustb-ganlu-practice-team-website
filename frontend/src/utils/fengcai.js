const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').trim()

export function unwrapApiData(response) {
  const body = response?.data ?? response

  const isApiEnvelope = body
    && typeof body === 'object'
    && 'code' in body

  if (isApiEnvelope && Number(body.code) !== 200) {
    const error = new Error(body.message || '团队风采数据加载失败')
    error.code = Number(body.code)
    throw error
  }

  if (isApiEnvelope) {
    if ('content' in body && body.content !== null && body.content !== undefined) {
      return body.content
    }
    if ('data' in body && body.data !== null && body.data !== undefined) {
      return body.data
    }
  }

  if (
    body
    && typeof body === 'object'
    && 'data' in body
    && (Object.keys(body).length === 1 || 'success' in body || 'message' in body)
  ) {
    return body.data
  }

  return body
}

export function extractCollection(response, preferredKeys = []) {
  const payload = unwrapApiData(response)

  if (Array.isArray(payload)) {
    return { items: payload, total: payload.length }
  }

  if (!payload || typeof payload !== 'object') {
    return { items: [], total: 0 }
  }

  const keys = [...preferredKeys, 'records', 'items', 'list', 'content']
  const key = keys.find((candidate) => Array.isArray(payload[candidate]))
  const items = key ? payload[key] : []
  const total = Number(payload.total ?? payload.totalElements ?? payload.count ?? items.length)

  return {
    items,
    total: Number.isFinite(total) ? total : items.length,
  }
}

export function resolveMediaUrl(path) {
  if (!path || typeof path !== 'string') return ''

  const trimmedPath = path.trim()
  if (/^(https?:|data:|blob:)/i.test(trimmedPath)) return trimmedPath
  if (!apiBaseUrl) return trimmedPath

  try {
    return new URL(trimmedPath, apiBaseUrl.endsWith('/') ? apiBaseUrl : `${apiBaseUrl}/`).href
  } catch {
    return trimmedPath
  }
}

export function isPublished(item) {
  const status = item?.status ?? item?.publishStatus ?? item?.reviewStatus
  if (status === null || status === undefined || status === '') return true
  return ['PUBLISHED', 'DISPLAY'].includes(String(status).toUpperCase())
}

export function publishedOnly(items) {
  return (Array.isArray(items) ? items : []).filter(isPublished)
}

export function formatDate(value) {
  if (!value) return ''

  const source = typeof value === 'string' ? value.replace(' ', 'T') : value
  const date = new Date(source)
  if (Number.isNaN(date.getTime())) return String(value)

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date)
}

export function getErrorMessage(error, fallback = '加载失败，请稍后重试') {
  const status = error?.response?.status ?? error?.code
  if (Number(status) === 404) return '没有找到对应的团队风采内容'
  if (Number(status) >= 500) return '服务暂时不可用，请稍后重试'
  if (error?.code === 'ECONNABORTED') return '请求超时，请检查网络后重试'
  return error?.response?.data?.message || error?.message || fallback
}

export function uniqueItems(items) {
  const seen = new Set()

  return (Array.isArray(items) ? items : []).filter((item, index) => {
    const key = item?.id
      ?? item?.mediaId
      ?? item?.imageUrl
      ?? item?.downloadUrl
      ?? item?.fileUrl
      ?? `${item?.caption ?? item?.title ?? 'item'}-${index}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
