const ROLE_META = {
  0: { name: '系统管理员', className: 'admin' },
  1: { name: '甘露团队', className: 'team' },
  2: { name: '学生账号', className: 'student' }
}

export function getMessageAuthor(item = {}) {
  const rawLevel = item.userLevel
  const parsedLevel = rawLevel === null || rawLevel === undefined || rawLevel === ''
    ? null
    : Number(rawLevel)
  const role = Number.isInteger(parsedLevel) ? ROLE_META[parsedLevel] : null
  const displayName = String(item.displayName || '').trim() || '已注销用户'
  const teamname = String(item.teamname || '').trim()

  return {
    displayName,
    username: String(item.username || '').trim(),
    roleName: parsedLevel === 1 && teamname
      ? teamname
      : role?.name || '账号类型待同步',
    roleClass: role?.className || 'member'
  }
}
