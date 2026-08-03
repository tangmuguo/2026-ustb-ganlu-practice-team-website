const ALLOWED_EXTENSIONS = new Set(['jpg', 'png', 'webp'])
const CONTENT_TYPES = {
  jpg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp'
}

export function parsePublicImageAccept(accept) {
  const entries = String(accept || '').split(',').map((entry) => entry.trim()).filter(Boolean)
  if (entries.length === 0 || entries.some((entry) => !/^\.(jpe?g|png|webp)$/i.test(entry))) {
    throw new TypeError('图片 accept 必须使用明确的 .jpg、.jpeg、.png、.webp 扩展名')
  }
  return [...new Set(entries.map((entry) => {
    const extension = entry.slice(1).toLowerCase()
    return extension === 'jpeg' ? 'jpg' : extension
  }))]
}

export function isPublicImageAcceptValid(accept) {
  try {
    parsePublicImageAccept(accept)
    return true
  } catch (error) {
    return false
  }
}

export function normalizePublicImageUploadInfo(info) {
  if (!info || typeof info !== 'object') {
    throw new TypeError('服务器返回了无效的图片暂存凭证')
  }
  const extension = String(info.extension || '').toLowerCase()
  const tokenPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  if (!tokenPattern.test(info.token || '') || !ALLOWED_EXTENSIONS.has(extension)
      || CONTENT_TYPES[extension] !== info.contentType || !Number.isFinite(info.size) || info.size <= 0) {
    throw new TypeError('服务器返回了无效的图片暂存凭证')
  }
  return Object.freeze({
    token: info.token,
    originalName: String(info.originalName || ''),
    extension,
    contentType: info.contentType,
    size: info.size
  })
}
