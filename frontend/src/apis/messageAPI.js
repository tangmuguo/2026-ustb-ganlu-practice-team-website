import instance from '@/utils/http'
import AllPATH from '@/utils/path'

// 获取留言列表
export function getMessages(page, pageSize) {
  return instance({
    url: AllPATH.messageListPath,
    method: 'get',
    params: { page, pageSize }
  })
}

// 添加留言
export function addMessage(data) {
  return instance({
    url: AllPATH.messageAddPath,
    method: 'post',
    data
  })
}

// 删除留言
export function deleteMessage(id) {
  return instance({
    url: AllPATH.messageDeletePath,
    method: 'post',
    data: {id}
  })
}

// 添加回复
export function addReply(data) {
  return instance({
    url: AllPATH.replyAddPath,
    method: 'post',
    data
  })
}

// 删除回复
export function deleteReply(id) {
  return instance({
    url: AllPATH.replyDeletePath,
    method: 'post',
    data:{
      id
    }
  })
}
