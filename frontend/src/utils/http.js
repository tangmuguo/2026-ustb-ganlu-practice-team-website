import axios from 'axios'
import { ElMessage } from 'element-plus'
import { userinfoStore } from '@/stores/userStore'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/'

const instance = axios.create({
  baseURL: configuredBaseUrl.endsWith('/') ? configuredBaseUrl : `${configuredBaseUrl}/`,
  timeout: 120000,
})

instance.interceptors.request.use((config) => {
  const userStore = userinfoStore()
  if (userStore.token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message

    if (status === 401) {
      const userStore = userinfoStore()
      userStore.clearUser()
      if (window.location.pathname !== '/login') {
        ElMessage.error(message || '登录已过期，请重新登录')
      }
      if (window.location.pathname !== '/login') {
        const redirect = `${window.location.pathname}${window.location.search}`
        window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
      }
    } else if (status === 403) {
      ElMessage.error(message || '当前账号没有此操作权限')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络后重试')
    }

    return Promise.reject(error)
  },
)

export default instance
