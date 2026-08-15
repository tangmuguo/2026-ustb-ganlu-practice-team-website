import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export const userinfoStore = defineStore('UserInfo', () => {
  const token = ref(null)
  const currentUser = ref(null)

  const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))

  // 兼容尚未合并改造的旧页面；新代码请直接使用 token/currentUser。
  const user = computed(() => ({
    token: token.value,
    content: currentUser.value,
  }))

  function setSession(session) {
    token.value = session?.token || null
    currentUser.value = session?.user || null
  }

  function setUser(payload) {
    const content = payload?.content || payload || {}
    setSession({
      token: content.token || payload?.token,
      user: content.user || (content.id ? content : payload?.user),
    })
  }

  function clearUser() {
    token.value = null
    currentUser.value = null
  }

  return {
    token,
    currentUser,
    isLoggedIn,
    user,
    setSession,
    setUser,
    clearUser,
  }
})
