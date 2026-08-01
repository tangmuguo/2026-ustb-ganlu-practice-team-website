import instance from '@/utils/http'

export const searchMaterials = (params) => instance({
  url: 'courseDetail/materials',
  method: 'GET',
  params
})

export const getCourseDetail = (id) => instance({
  url: `courseDetail/materials/${id}`,
  method: 'GET'
})

export const createMaterial = (payload) => instance({
  url: 'courseDetail/materials',
  method: 'POST',
  data: payload
})

export const deleteMaterial = (id) => instance({
  url: `courseDetail/materials/${id}`,
  method: 'DELETE'
})

export const getMaterialCategories = () => instance({
  url: 'courseCategory/list',
  method: 'GET'
})

export const getManagedMaterialCategories = () => instance({
  url: 'courseCategory/manage',
  method: 'GET'
})

export const addMaterialCategory = (courseName) => instance({
  url: 'courseCategory',
  method: 'POST',
  data: { courseName }
})

export const updateMaterialCategory = (id, payload) => instance({
  url: `courseCategory/${id}`,
  method: 'PUT',
  data: payload
})

export const uploadChunk = (formData, onProgress) => instance({
  url: 'courseDetail/uploadChunk',
  method: 'POST',
  data: formData,
  onUploadProgress: onProgress
})

export const checkFileExist = (identifier, purpose) => {
  const formData = new FormData()
  formData.append('identifier', identifier)
  formData.append('purpose', purpose)
  return instance({
    url: 'courseDetail/checkFileExist',
    method: 'POST',
    data: formData
  })
}

export const mergeChunks = ({ filename, identifier, totalChunks, expectedSize, purpose }) => {
  const formData = new FormData()
  formData.append('filename', filename)
  formData.append('identifier', identifier)
  formData.append('totalChunks', String(totalChunks))
  formData.append('expectedSize', String(expectedSize))
  formData.append('purpose', purpose)
  return instance({
    url: 'courseDetail/mergeChunks',
    method: 'POST',
    data: formData
  })
}

export const downloadMaterial = (id, onProgress) => instance({
  url: `courseDetail/materials/${id}/download`,
  method: 'GET',
  responseType: 'blob',
  onDownloadProgress: onProgress
})

export const saveDownload = (blob, filename) => {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename || '课件'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
}

export const resolveMaterialAssetUrl = (relativePath) => {
  if (!relativePath) return ''
  if (/^https?:\/\//i.test(relativePath)) return relativePath
  const baseUrl = instance.defaults.baseURL || import.meta.env.VITE_API_BASE_URL || window.location.origin
  return new URL(relativePath.replace(/^\/+/, ''), baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`).toString()
}
