import { ElMessage } from 'element-plus'
import { userinfoStore } from '@/stores/userStore'
import router from '@/router'

export function hasRole(user, allowedLevels) {
  const levels = Array.isArray(allowedLevels) ? allowedLevels : [allowedLevels]
  return Boolean(user && levels.includes(Number(user.level)))
}

export function access(allowedLevels) {
  const userStore = userinfoStore()
  if (!userStore.isLoggedIn) {
    ElMessage.info('请先登录')
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    return false
  }

  if (!hasRole(userStore.currentUser, allowedLevels)) {
    ElMessage.info('当前账号没有此操作权限')
    router.push('/')
    return false
  }

  return true
}
