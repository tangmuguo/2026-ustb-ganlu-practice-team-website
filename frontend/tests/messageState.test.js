import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createLatestRequestGuard,
  finishPending,
  startPending
} from '../src/utils/messageState.js'

test('列表只采纳最后开始的请求', () => {
  const guard = createLatestRequestGuard()
  const pageTwoRequest = guard.begin()
  const pageThreeRequest = guard.begin()

  assert.equal(guard.isLatest(pageTwoRequest), false)
  assert.equal(guard.isLatest(pageThreeRequest), true)
})

test('不同留言的回复提交状态互不覆盖', () => {
  const pendingIds = new Set()

  assert.equal(startPending(pendingIds, 101), true)
  assert.equal(startPending(pendingIds, 102), true)
  assert.equal(startPending(pendingIds, 101), false)

  finishPending(pendingIds, 101)
  assert.equal(pendingIds.has(101), false)
  assert.equal(pendingIds.has(102), true)
})
