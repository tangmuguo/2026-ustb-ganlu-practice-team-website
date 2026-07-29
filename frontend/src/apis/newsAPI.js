import instance from '@/utils/http'
import AllPATH from '@/utils/path'

export function GetAllNews() {
  return instance({
    url: AllPATH.newsListPath,
    method: 'get'
  })
}

export function GetLimitNews() {
  return instance({
    url: AllPATH.newsLimitPath,
    method: 'get'
  })
}

export function AddNews(data) {
  return instance({
    url: AllPATH.newsAddPath,
    method: 'post',
    data
  })
}

export function UpdateNews(data) {
  return instance({
    url: AllPATH.newsUpdatedPath,
    method: 'post',
    data
  })
}

export function DeleteNews(id) {
 // const ids = Array.isArray(id) ? id : [id];
  return instance({
    url: AllPATH.newsDeletePath,
    method: 'post',
    data: {id:id}
  })
}

export function getNewsById(id) {
    // const ids = Array.isArray(id) ? id : [id];
  return instance({
    url: AllPATH.newsGetPath,
    method: 'post',
    data: {id:id}
  })
}