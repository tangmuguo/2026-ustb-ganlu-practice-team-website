import instance from '@/utils/http'

export function getPendingContent(type, page = 1, pageSize = 20) {
  return instance.get('message/moderation/pending', { params: { type, page, pageSize } })
}

export function reviewContent(data) {
  return instance.post('message/moderation/review', data)
}

export function createContentReport(data) {
  return instance.post('reports', data)
}

export function getContentReports(status = 'OPEN', page = 1, pageSize = 50) {
  return instance.get('admin/reports', { params: { status, page, pageSize } })
}

export function resolveContentReport(id, data) {
  return instance.put(`admin/reports/${id}`, data)
}
