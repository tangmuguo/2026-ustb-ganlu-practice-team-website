import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const manage = readFileSync(resolve(here, '../src/views/TeamContentManage.vue'), 'utf8')
const uploadPhotos = readFileSync(resolve(here, '../src/components/UploadPhotos.vue'), 'utf8')

test('私有图片预览只通过带认证的 Blob 请求，不把登录令牌放进 URL', () => {
  assert.match(manage, /getTeamContentImage\(row\.id\)/)
  assert.match(manage, /URL\.createObjectURL/)
  assert.match(manage, /URL\.revokeObjectURL/)
  assert.doesNotMatch(manage, /\?token=/)
  assert.doesNotMatch(manage, /encodeURIComponent\(token\)/)
})

test('照片提交成功只在本地凭证消费并清理后通知父页面刷新', () => {
  assert.match(uploadPhotos, /defineEmits\(\['uploaded'\]\)/)
  const consumed = uploadPhotos.indexOf('markConsumed()')
  const cleared = uploadPhotos.indexOf('clearFile({ cancelRemote: false })')
  const emitted = uploadPhotos.indexOf("emit('uploaded')")
  assert.ok(consumed >= 0 && cleared > consumed && emitted > cleared)
  assert.equal((uploadPhotos.match(/emit\('uploaded'\)/g) || []).length, 1)
})
