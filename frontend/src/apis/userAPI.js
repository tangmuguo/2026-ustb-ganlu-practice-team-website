import instance from "@/utils/http"
import AllPATH from "@/utils/path"

export function login ({ username, password }) {

  return instance({
    url: AllPATH.loginPath,
    method: 'POST',
    data: {
      username: username,
      password: password
    }
  })
}

export function GetAllTeams () {

  return instance({
    url: AllPATH.teamsPath,
    method: 'POST',
  })
}

export function GetAllStudents () {

  return instance({
    url: AllPATH.studentsPath,
    method: 'POST',
  })
}

export function AddTeam ({ username, password ,teamname,imageUrl}) {

  return instance({
    url: AllPATH.addTeamPath,
    method: 'POST',
    data: {
      username: username,
      password: password,
      teamname: teamname,
      imageUrl:imageUrl
    }
  })
}

export function UpdateTeam({id, username, password ,teamname,imageUrl,realname}){
return instance({
    url: AllPATH.updateTeamPath,
    method: 'POST',
    data: {
      id:id,
      username: username,
      password: password,
      teamname: teamname,
      realname:realname,
      imageUrl:imageUrl
    }
  })
}

export function AddStudent (val) {

  return instance({
    url: AllPATH.addStudentPath,
    method: 'POST',
    data: val
  })
}

export function DeleteTeam (id) {
  const ids = Array.isArray(id) ? id : [id];
  return instance({
    url: AllPATH.deleteTeamPath,
    method: 'POST',
    data: ids
  })
}