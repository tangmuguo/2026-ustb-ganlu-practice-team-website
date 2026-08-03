import instance from "@/utils/http"
import AllPATH from "@/utils/path"

// =====================================================================
// 新接口 — 团队风采内容管理
// =====================================================================

/**
 * 获取当前用户所属团队的全部内容（含所有状态）
 */
export function getMyTeamContent () {
  return instance({
    url: AllPATH.teamContentMinePath,
    method: 'GET'
  })
}

/**
 * 上传队员照
 * @param {File} file 图片文件
 * @param {Object} extra { caption, content, logDate }
 */
export function uploadMember (file, extra = {}) {
  const formData = buildImageFormData(file, extra);
  return instance({
    url: AllPATH.teamContentMembersPath,
    method: 'POST',
    data: formData,
    headers: { "Content-Type": "multipart/form-data" }
  })
}

/**
 * 上传支教/地区照片
 * @param {File} file 图片文件
 * @param {Object} extra { caption, content, logDate }
 */
export function uploadPhotoNew (file, extra = {}) {
  const formData = buildImageFormData(file, extra);
  return instance({
    url: AllPATH.teamContentPhotosPath,
    method: 'POST',
    data: formData,
    headers: { "Content-Type": "multipart/form-data" }
  })
}

/**
 * 上传日志
 * @param {Object} payload { caption, content, logDate }
 */
export function uploadLog (payload) {
  const params = new URLSearchParams()
  params.append('caption', payload.caption)
  params.append('content', payload.content)
  if (payload.logDate) params.append('logDate', payload.logDate)
  return instance({
    url: AllPATH.teamContentLogsPath,
    method: 'POST',
    data: params,
    headers: { "Content-Type": "application/x-www-form-urlencoded" }
  })
}

/**
 * 上传荣誉
 * @param {Object} payload { caption, content, logDate }
 */
export function uploadHonor (payload) {
  const params = new URLSearchParams()
  params.append('caption', payload.caption)
  params.append('content', payload.content)
  if (payload.logDate) params.append('logDate', payload.logDate)
  return instance({
    url: AllPATH.teamContentHonorsPath,
    method: 'POST',
    data: params,
    headers: { "Content-Type": "application/x-www-form-urlencoded" }
  })
}

/**
 * 上传视频/附件
 * @param {File} file 媒体文件
 * @param {Object} extra { relatedType, relatedId }
 */
export function uploadMedia (file, extra = {}) {
  const formData = new FormData();
  formData.append("file", file);
  if (extra.relatedType) formData.append("relatedType", extra.relatedType);
  if (extra.relatedId != null) formData.append("relatedId", extra.relatedId);
  return instance({
    url: AllPATH.teamContentMediaPath,
    method: 'POST',
    data: formData,
    headers: { "Content-Type": "multipart/form-data" }
  })
}

/**
 * 删除内容（逻辑删除 → ARCHIVED）
 * @param {String} type image / word / media
 * @param {Number} id
 */
export function deleteContent (type, id) {
  return instance({
    url: AllPATH.teamContentDeletePath(type, id),
    method: 'POST'
  })
}

/**
 * 获取公开端团队内容（仅 PUBLISHED）
 * @param {Number} teamId
 */
export function getPublicTeamContent (teamId) {
  return instance({
    url: AllPATH.teamContentPublicPath(teamId),
    method: 'GET'
  })
}

/**
 * 下载视频/附件（公开端，仅 PUBLISHED 附件可下）
 * @param {Number} mediaId
 */
export function downloadMedia (mediaId) {
  return instance({
    url: AllPATH.teamContentMediaDownloadPath(mediaId),
    method: 'GET',
    responseType: 'blob'
  })
}

/**
 * 团队端下载附件（含 PENDING/REJECTED/ARCHIVED，用于团队负责人确认上传内容）
 * @param {Number} mediaId
 */
export function downloadMediaOwner (mediaId) {
  return instance({
    url: AllPATH.teamContentMediaOwnerDownloadPath(mediaId),
    method: 'GET',
    responseType: 'blob'
  })
}

/**
 * 管理员下载附件（任意状态，用于审核 PENDING/REJECTED 时查看内容）
 * @param {Number} mediaId
 */
export function downloadMediaAdmin (mediaId) {
  return instance({
    url: AllPATH.adminTeamContentMediaDownloadPath(mediaId),
    method: 'GET',
    responseType: 'blob'
  })
}

// =====================================================================
// 管理员接口
// =====================================================================

/**
 * 管理员获取所有团队列表（下拉选择用）
 */
export function adminListTeams () {
  return instance({
    url: AllPATH.adminTeamContentTeamsPath,
    method: 'GET'
  })
}

/**
 * 管理员按团队+状态筛选内容
 * @param {Object} params { teamId, status }
 */
export function adminListContent (params = {}) {
  return instance({
    url: AllPATH.adminTeamContentPath,
    method: 'GET',
    params
  })
}

/**
 * 管理员发布内容
 * @param {String} type image / word / media
 * @param {Number} id
 */
export function adminPublish (type, id) {
  return instance({
    url: AllPATH.adminTeamContentPublishPath(type, id),
    method: 'POST'
  })
}

/**
 * 管理员驳回内容
 * @param {String} type image / word / media
 * @param {Number} id
 * @param {String} reason 驳回原因（必填）
 */
export function adminReject (type, id, reason) {
  return instance({
    url: AllPATH.adminTeamContentRejectPath(type, id),
    method: 'POST',
    params: { reason }
  })
}

/**
 * 管理员归档内容
 * @param {String} type image / word / media
 * @param {Number} id
 */
export function adminArchive (type, id) {
  return instance({
    url: AllPATH.adminTeamContentArchivePath(type, id),
    method: 'POST'
  })
}

// =====================================================================
// 辅助函数
// =====================================================================

function buildImageFormData (file, extra) {
  const formData = new FormData();
  formData.append("file", file);
  if (extra.caption) formData.append("caption", extra.caption);
  if (extra.content) formData.append("content", extra.content);
  if (extra.logDate) formData.append("logDate", extra.logDate);
  if (extra.type != null) formData.append("type", extra.type);
  return formData;
}

// =====================================================================
// 团队核心公开接口（来自王嘉阳 PR#5 TeamAction）— 返回真实 TeamEntity
// =====================================================================

/**
 * 获取已发布团队的年份列表
 */
export function getPublishedYears () {
  return instance({
    url: AllPATH.teamsYearsPath,
    method: 'GET'
  })
}

/**
 * 按年份获取已发布团队列表（返回真实 TeamEntity，team.id 为团队主键）
 * @param {String} year 年份
 * @param {Number} page 页码（默认 1）
 * @param {Number} size 每页数量（默认 12）
 */
export function getPublishedTeamsByYear (year, page = 1, size = 12) {
  return instance({
    url: AllPATH.teamsByYearPath,
    method: 'GET',
    params: { year, page, size }
  })
}
