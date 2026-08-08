import instance from '@/utils/http'

export function submitVolunteerApplication(data) {
  return instance.post('volunteer-applications', data)
}

export function getVolunteerApplications(params) {
  return instance.get('admin/volunteer-applications', { params })
}

export function updateVolunteerApplicationStatus(id, status) {
  return instance.patch(`admin/volunteer-applications/${id}/status`, { status })
}
