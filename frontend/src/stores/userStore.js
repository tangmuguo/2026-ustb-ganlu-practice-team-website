import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const userinfoStore = defineStore('UserInfo', () => {
  const user = ref({})
  const token = computed(() => user.value?.token ?? null)
  const currentUser = computed(() => user.value?.content ?? null)
  const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))

  function setUser(u) {
    user.value=u
  }
  function clearUser(){
    user.value={}
  }

  return { user, token, currentUser, isLoggedIn, setUser, clearUser }
},
{persist:true}
)
