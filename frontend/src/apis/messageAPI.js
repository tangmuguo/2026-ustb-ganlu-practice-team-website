import instance from '@/utils/http'
import AllPATH from '@/utils/path'

export function getMessages(page, pageSize) {
  return instance({
    url: AllPATH.messageListPath,
    method: 'get',
    params: { page, pageSize }
  })
}

export function addMessage(content) {
  return instance({
    url: AllPATH.messageAddPath,
    method: 'post',
    data: { content }
  })
}

export function deleteMessage(id, reasonCode) {
  return instance({
    url: AllPATH.messageDeletePath,
    method: 'post',
    data: { id, ...(reasonCode ? { reasonCode } : {}) }
  })
}

export function addReply(messageId, content) {
  return instance({
    url: AllPATH.replyAddPath,
    method: 'post',
    data: { messageId, content }
  })
}

export function deleteReply(id, reasonCode) {
  return instance({
    url: AllPATH.replyDeletePath,
    method: 'post',
    data: { id, ...(reasonCode ? { reasonCode } : {}) }
  })
}
