function parseDate(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const normalizedValue = typeof value === 'string'
    ? value.trim().replace(
        /^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}(?::\d{2})?)/,
        '$1T$2'
      )
    : value
  const date = value instanceof Date ? value : new Date(normalizedValue)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatTime(dateStr) {
  const date = parseDate(dateStr)
  if (!date) return '时间未知'

  const year = date.getUTCFullYear()
  const month = String(date.getUTCMonth() + 1).padStart(2, '0')
  const day = String(date.getUTCDate()).padStart(2, '0')
  const hours = String(date.getUTCHours()).padStart(2, '0')
  const minutes = String(date.getUTCMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

export function formatTime2(dateStr) {
  return formatMessageTime(dateStr)
}

export function formatMessageTime(value) {
  const date = parseDate(value)
  if (!date) return '时间未知'

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}
