export function createLatestRequestGuard() {
  let latestId = 0
  return {
    begin: () => ++latestId,
    isLatest: id => id === latestId
  }
}

export function startPending(pendingIds, id) {
  if (pendingIds.has(id)) return false
  pendingIds.add(id)
  return true
}

export function finishPending(pendingIds, id) {
  pendingIds.delete(id)
}
