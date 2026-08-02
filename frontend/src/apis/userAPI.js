import instance from '@/utils/http'
import AllPATH from '@/utils/path'

export function login({ username, password }) {
  return instance.post(AllPATH.loginPath, { username, password })
}

export function RegisterStudent(data) {
  return instance.post('user/register/student', data)
}

export function GetAllTeams() {
  return instance.post(AllPATH.teamsPath)
}

export function GetAllStudents() {
  return instance.post(AllPATH.studentsPath)
}

export function AddTeam(data) {
  return instance.post(AllPATH.addTeamPath, {
    ...data,
    confirmPassword: data.confirmPassword || data.password,
  })
}

export function AddStudent(data) {
  return instance.post(AllPATH.addStudentPath, data)
}

export function UpdateTeam(data) {
  return instance.post(AllPATH.updateTeamPath, data)
}

export function DeleteTeam(id) {
  const ids = Array.isArray(id) ? id : [id]
  return instance.post(AllPATH.deleteTeamPath, ids)
}
