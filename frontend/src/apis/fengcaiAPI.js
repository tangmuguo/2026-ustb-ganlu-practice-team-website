import instance from "@/utils/http"
import AllPATH from "@/utils/path"

// 团队风采公开端：仅使用 teamId/pageId，不携带登录用户的 userId。
export function getTeamYears () {
  return instance({
    url: '/teams/years',
    method: 'GET'
  })
}

export function getTeamsByYear (year, { page = 1, size = 12 } = {}) {
  return instance({
    url: '/teams',
    method: 'GET',
    params: { year, page, size }
  })
}

export function getTeamDetail (teamId) {
  return instance({
    url: `/teams/${encodeURIComponent(teamId)}`,
    method: 'GET'
  })
}

export function getPublicTeamContent (teamId) {
  return instance({
    url: `/team-content/public/${encodeURIComponent(teamId)}`,
    method: 'GET'
  })
}

export function stagePublicImage (photoFile, onProgress) {
  const formData = new FormData()
  formData.append('imageFile', photoFile)
  return instance({
    url: AllPATH.uploadTeamImagePath,
    method: 'POST',
    data: formData,
    onUploadProgress: onProgress
  })
}

export function cancelPublicImageUpload (token) {
  return instance({
    url: AllPATH.uploadTeamImagePath,
    method: 'DELETE',
    params: { token }
  })
}

export function uploadWholeImage (val) {
  return instance({
    url: Number(val.type) === 1 ? AllPATH.teamContentMembersPath : AllPATH.teamContentPhotosPath,
    method: 'POST',
    data: val
  })
}

export function getMyTeamContent () {
  return instance({ url: AllPATH.teamContentMinePath, method: 'GET' })
}

// 私有图片必须通过 Authorization header 获取 Blob，禁止把完整登录 JWT 拼进 URL。
export function getTeamContentImage (imageId) {
  return instance({
    url: `/team-content/image/${encodeURIComponent(imageId)}`,
    method: 'GET',
    responseType: 'blob'
  })
}

export function uploadMember (file, extra = {}) {
  return uploadImageMultipart(AllPATH.teamContentMembersPath, file, extra)
}

export function uploadPhotoNew (file, extra = {}) {
  return uploadImageMultipart(AllPATH.teamContentPhotosPath, file, extra)
}

export function uploadLog (payload) {
  return postForm(AllPATH.teamContentLogsPath, payload)
}

export function uploadHonor (payload) {
  return postForm(AllPATH.teamContentHonorsPath, payload)
}

export function uploadMedia (file, extra = {}) {
  const data = new FormData()
  data.append('file', file)
  if (extra.relatedType) data.append('relatedType', extra.relatedType)
  if (extra.relatedId != null) data.append('relatedId', extra.relatedId)
  return instance({ url: AllPATH.teamContentMediaPath, method: 'POST', data })
}

export function deleteContent (type, id) {
  return instance({ url: AllPATH.teamContentDeletePath(type, id), method: 'POST' })
}

export function downloadMedia (mediaId) {
  return instance({ url: AllPATH.teamContentMediaDownloadPath(mediaId), method: 'GET', responseType: 'blob' })
}

export function downloadMediaOwner (mediaId) {
  return instance({ url: AllPATH.teamContentMediaOwnerDownloadPath(mediaId), method: 'GET', responseType: 'blob' })
}

export function downloadMediaAdmin (mediaId) {
  return instance({ url: AllPATH.adminTeamContentMediaDownloadPath(mediaId), method: 'GET', responseType: 'blob' })
}

export function adminListTeams () {
  return instance({ url: AllPATH.adminTeamContentTeamsPath, method: 'GET' })
}

export function adminListContent (params = {}) {
  return instance({ url: AllPATH.adminTeamContentPath, method: 'GET', params })
}

export function adminPublish (type, id) {
  return instance({ url: AllPATH.adminTeamContentPublishPath(type, id), method: 'POST' })
}

export function adminReject (type, id, reason) {
  return instance({ url: AllPATH.adminTeamContentRejectPath(type, id), method: 'POST', params: { reason } })
}

export function adminArchive (type, id) {
  return instance({ url: AllPATH.adminTeamContentArchivePath(type, id), method: 'POST' })
}

export function adminPurgeMedia (id) {
  return instance({ url: `/admin/team-content/media/${encodeURIComponent(id)}/purge`, method: 'POST' })
}

export function adminListFileDeletionTasks (limit = 100) {
  return instance({ url: '/admin/file-deletion-tasks', method: 'GET', params: { limit } })
}

export function adminRetryFileDeletionTask (id) {
  return instance({ url: `/admin/file-deletion-tasks/${encodeURIComponent(id)}/retry`, method: 'POST' })
}

export const getPublishedYears = getTeamYears

export function getPublishedTeamsByYear (year, page = 1, size = 12) {
  return getTeamsByYear(year, { page, size })
}

function uploadImageMultipart (url, file, extra) {
  const data = new FormData()
  data.append('file', file)
  Object.entries(extra).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') data.append(key, value)
  })
  return instance({ url, method: 'POST', data })
}

function postForm (url, payload) {
  const data = new URLSearchParams()
  Object.entries(payload || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') data.append(key, value)
  })
  return instance({ url, method: 'POST', data })
}
