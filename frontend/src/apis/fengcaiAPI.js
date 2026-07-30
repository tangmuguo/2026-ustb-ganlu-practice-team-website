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

export function uploadPhoto (photoFile) {

  return instance({
    url: AllPATH.uploadTeamImagePath,
    method: 'POST',
    data: {
      imageFile: photoFile
    },
    headers: {
      "Content-Type": "multipart/form-data", // 关键！设置正确的 Content-Type
    }
  })
}

export function uploadWholeImage (val) {

  return instance({
    url: AllPATH.addTeamImagePath,
    method: 'POST',
    data: val
  })
}

export function uploadWholeWord (val) {

  return instance({
    url: AllPATH.addTeamWordPath,
    method: 'POST',
    data: val
  })
}

export function findAllWords (id) {

  return instance({
    url: AllPATH.findAllWordsPath,
    method: 'POST',
    data: {userId:id}
  })
}

export function findAllImages (id) {

  return instance({
    url: AllPATH.findAllImagesPath,
    method: 'POST',
    data: {userId:id}
  })
}

export function deleteImage (id) {
  const ids = Array.isArray(id) ? id : [id];
  return instance({
    url: AllPATH.deleteImagePath,
    method: 'POST',
    data: ids
  })
}

export function deleteWord (id) {
  const ids = Array.isArray(id) ? id : [id];
  return instance({
    url: AllPATH.deleteWordPath,
    method: 'POST',
    data: ids
  })
}
