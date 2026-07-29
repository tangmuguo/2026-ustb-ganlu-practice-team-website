import axios from "axios";
import { ElMessage } from 'element-plus';
import { userinfoStore } from '@/stores/userStore';
const instance = axios.create({
  baseURL: process.env.NODE_ENV === 'production' 
    ? 'http://47.95.209.65:8080/' 
    : 'http://localhost:8080/',
  timeout: 600000
})

instance.interceptors.request.use(
  config => {
    const userStore = userinfoStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  error => Promise.reject(error)
)

instance.interceptors.response.use(
  config => {
    console.log("响应拦截器启动——成功");
    if (config.data.code != 200) {
      //ElMessage({ type: "warning", message: config.data.message })
    }
    return config
  },
  error => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      const userStore = userinfoStore()
      userStore.clearUser()
      ElMessage.error(message || '登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    } else if (status === 403) {
      ElMessage.error(message || '无访问权限')
    }
    return Promise.reject(error)
  }
)

export default instance
