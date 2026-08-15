import instance from '@/utils/http'

/** Authenticated requester endpoints. The server derives the user from JWT. */
export function createPrivacyRequest(data) {
  return instance.post('privacy-requests', data)
}

export function getMyPrivacyRequests(page = 1, pageSize = 20) {
  return instance.get('privacy-requests/mine', { params: { page, pageSize } })
}

export function getMyPrivacyRequest(id) {
  return instance.get(`privacy-requests/${id}`)
}

/** Administrator-only queue endpoints. */
export function getPrivacyRequests(status = '', page = 1, pageSize = 50) {
  const params = { page, pageSize }
  if (status) params.status = status
  return instance.get('admin/privacy-requests', { params })
}

export function processPrivacyRequest(id, data) {
  return instance.put(`admin/privacy-requests/${id}`, data)
}
