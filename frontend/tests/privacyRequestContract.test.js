import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const api = readFileSync(resolve(here, '../src/apis/privacyRequestAPI.js'), 'utf8')
const view = readFileSync(resolve(here, '../src/views/PrivacyRequests.vue'), 'utf8')

test('隐私工单 API 绑定认证用户工单和管理员处置接口', () => {
  assert.match(api, /instance\.post\('privacy-requests'/)
  assert.match(api, /instance\.get\('privacy-requests\/mine'/)
  assert.match(api, /instance\.get\('admin\/privacy-requests'/)
  assert.match(api, /instance\.put\(`admin\/privacy-requests\/\$\{id\}`/)
})

test('隐私工单页面明确删除仅为评估且避免收集证件和令牌', () => {
  assert.match(view, /资料删除评估/)
  assert.match(view, /不会在此处直接删除账号、内容或文件/)
  assert.match(view, /密码、验证码、Token/)
  assert.match(view, /requestType/)
  assert.match(view, /decisionReason/)
})
