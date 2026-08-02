import assert from 'node:assert/strict'
import test from 'node:test'
import { getMessageAuthor } from '../src/utils/messageAuthor.js'

test('留言作者只按约定字段映射角色', () => {
  assert.deepEqual(
    getMessageAuthor({ userLevel: 0, displayName: '管理员甲' }),
    {
      displayName: '管理员甲',
      username: '',
      roleName: '系统管理员',
      roleClass: 'admin'
    }
  )
  assert.equal(
    getMessageAuthor({
      userLevel: '1',
      displayName: '甘露一队',
      teamname: '甘露一队'
    }).roleName,
    '甘露一队'
  )
  assert.equal(
    getMessageAuthor({ userLevel: 2, displayName: '学生乙' }).roleClass,
    'student'
  )
  assert.equal(
    getMessageAuthor({
      level: 0,
      displayName: '旧字段用户',
      teamname: '不能用于猜测角色'
    }).roleName,
    '账号类型待同步'
  )
})
