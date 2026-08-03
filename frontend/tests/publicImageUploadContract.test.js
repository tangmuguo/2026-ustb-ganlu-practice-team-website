import assert from 'node:assert/strict'
import test from 'node:test'
import {
  isPublicImageAcceptValid,
  normalizePublicImageUploadInfo,
  parsePublicImageAccept
} from '../src/utils/publicImageUpload.js'

test('公共图片只能声明明确的安全扩展名', () => {
  assert.deepEqual(parsePublicImageAccept('.jpg,.jpeg,.png,.webp'), ['jpg', 'png', 'webp'])
  assert.equal(isPublicImageAcceptValid('image/*'), false)
  assert.equal(isPublicImageAcceptValid('.svg'), false)
})

test('上传事件返回不可变的暂存凭证对象而不是图片 URL', () => {
  const upload = normalizePublicImageUploadInfo({
    token: '123e4567-e89b-42d3-a456-426614174000',
    originalName: 'photo.png',
    extension: 'png',
    contentType: 'image/png',
    size: 128
  })

  assert.equal(upload.token, '123e4567-e89b-42d3-a456-426614174000')
  assert.equal(Object.isFrozen(upload), true)
  assert.equal('url' in upload, false)
})

test('拒绝扩展名与服务器 MIME 类型不一致的凭证', () => {
  assert.throws(() => normalizePublicImageUploadInfo({
    token: '123e4567-e89b-42d3-a456-426614174000',
    extension: 'jpg',
    contentType: 'image/png',
    size: 128
  }), /无效/)
})
