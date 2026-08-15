import instance from '@/utils/http'
import AllPATH from '@/utils/path'

export function login({ username, password }) {
  return instance.post(AllPATH.loginPath, { username, password })
}

export function GetAllTeams() {
  return instance.get(AllPATH.teamsPath)
}

export function GetAllStudents() {
  return instance.get(AllPATH.studentsPath)
}

export function AddTeam(data) {
  return instance.post(AllPATH.addTeamPath, {
    ...data,
    confirmPassword: data.confirmPassword || data.password,
  })
}

export function AddStudent(data) {
  return instance.post(AllPATH.studentsPath, data)
}

export function UpdateTeam(data) {
  return instance.put(`user/teams/${data.id}`, data)
}

export function DeleteTeam(id) {
  const ids = Array.isArray(id) ? id : [id]
  return instance.delete('user/teams', { data: ids })
}

export function UpdateStudent(id, data) {
  return instance.put(`user/students/${id}`, data)
}

export function DeleteStudents(id) {
  const ids = Array.isArray(id) ? id : [id]
  return instance.delete('user/students', { data: ids })
}

export function logout() {
  return instance.post('user/logout')
}
