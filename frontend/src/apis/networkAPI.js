import instance from "@/utils/http"
import AllPATH from "@/utils/path"

export function testUser(){
  return instance({
    url:AllPATH.testPath
  })

}

export function getUserMessage () {
  return instance({
    url: AllPATH.userPath
  })
}

export function login ({ username, password }) {
  console.log(username);
  console.log(password);

  return instance({
    url: AllPATH.loginPath,
    method: 'POST',
    data: {
      username: username,
      password: password
    }
  })
}

export function Regist ({ username, password, nickname }) {
  return instance({
    url: AllPATH.regPath,
    method: 'POST',
    data: {
      username: username,
      password: password,
      nickname: nickname
    }
  })
}

export function hotProduct (val) {
  return instance({
    url: AllPATH.hotProduct,
    method: 'GET',
    params: {
      type: val
    }
  })
}

export function singleProduct (val) {
  return instance({
    url: AllPATH.signleProduct,
    method: 'GET',
    params: {
      id: val
    }
  })
}

export function addOrders (val) {
  return instance({
    url: AllPATH.addOrders,
    method: 'post',
    params: {
      val
    }
  })
}

