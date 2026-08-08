<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userinfoStore } from '@/stores/userStore'
import logoUrl from '@/images/甘露.png'

const route = useRoute()
const router = useRouter()
const userStore = userinfoStore()
const mobileOpen = ref(false)

const publicLinks = [
  { to: '/', label: '首页' },
  { to: '/showm', label: '课件共享' },
  { to: '/fengcai', label: '团队风采' },
  { to: '/messageboard', label: '互动留言' },
  { to: '/about', label: '关于甘露' },
]

const displayName = computed(() => userStore.currentUser?.teamname
  || userStore.currentUser?.realname
  || userStore.currentUser?.username
  || '我的账号')

const level = computed(() => Number(userStore.currentUser?.level))

const managementItems = computed(() => {
  if (!userStore.isLoggedIn) return []

  const common = [
    { command: '/uppt', label: '上传课件' },
    { command: '/mmanage', label: '课件管理' },
  ]

  if (level.value === 0) {
    return [
      { command: '/mbanner', label: '轮播图管理' },
      { command: '/mnews', label: '新闻管理' },
      { command: '/muser', label: '团队账号管理' },
      { command: '/regt', label: '创建团队账号' },
      { command: '/mstudent', label: '学生账号管理' },
      { command: '/admin/team-content', label: '团队内容审核' },
      ...common,
    ]
  }

  if (level.value === 1) {
    return [
      { command: '/mstudent', label: '学生账号管理' },
      { command: '/mnews', label: '新闻管理' },
      { command: '/team-content-manage', label: '团队内容管理' },
      ...common,
    ]
  }

  return []
})

function logout() {
  userStore.clearUser()
  mobileOpen.value = false
  router.push('/')
}

function handleCommand(command) {
  if (command === 'logout') logout()
  else router.push(command)
}

watch(() => route.fullPath, () => {
  mobileOpen.value = false
})
</script>

<template>
  <header class="site-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/" aria-label="返回甘露支教首页">
        <span class="brand-mark"><img :src="logoUrl" alt="甘露支教标志" class="brand-logo"></span>
        <span class="brand-copy">
          <strong>甘露支教</strong>
          <small>GANLU EDUCATION</small>
        </span>
      </RouterLink>

      <nav class="desktop-nav" aria-label="主导航">
        <RouterLink
          v-for="item in publicLinks"
          :key="item.to"
          :to="item.to"
          class="nav-link"
        >
          {{ item.label }}
        </RouterLink>
        <RouterLink v-if="userStore.isLoggedIn" to="/ai" class="nav-link">AI 小助手</RouterLink>
        <RouterLink to="/news" class="nav-link">新闻</RouterLink>
      </nav>

      <div class="account-area">
        <RouterLink v-if="!userStore.isLoggedIn" to="/login" class="login-button">登录</RouterLink>
        <el-dropdown v-else trigger="click" @command="handleCommand">
          <button class="account-button" type="button">
            <span class="account-name">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/ai">AI 小助手</el-dropdown-item>
              <el-dropdown-item
                v-for="item in managementItems"
                :key="item.command"
                :command="item.command"
              >
                {{ item.label }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button class="mobile-trigger" type="button" aria-label="打开导航菜单" @click="mobileOpen = true">
          <el-icon><Menu /></el-icon>
        </button>
      </div>
    </div>

    <el-drawer v-model="mobileOpen" title="甘露支教" size="min(86vw, 360px)" direction="rtl">
      <nav class="mobile-nav" aria-label="移动端导航">
        <RouterLink v-for="item in publicLinks" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
        <RouterLink v-if="userStore.isLoggedIn" to="/ai">AI 小助手</RouterLink>
        <RouterLink to="/news">新闻</RouterLink>
        <template v-if="userStore.isLoggedIn">
          <div class="mobile-divider">账号功能</div>
          <RouterLink v-for="item in managementItems" :key="item.command" :to="item.command">
            {{ item.label }}
          </RouterLink>
          <button type="button" @click="logout">退出登录</button>
        </template>
        <RouterLink v-else class="mobile-login" to="/login">登录 / 注册</RouterLink>
      </nav>
    </el-drawer>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 100;
  color: #163b6d;
  background: rgba(255, 255, 255, 0.88);
  border-bottom: 1px solid rgba(207, 225, 248, 0.84);
  box-shadow: 0 10px 28px rgba(34, 88, 150, 0.08);
  backdrop-filter: blur(18px);
}

.header-inner {
  display: flex;
  min-height: 80px;
  max-width: 1240px;
  align-items: center;
  gap: 30px;
  margin: 0 auto;
  padding: 0 24px;
}

.brand {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 11px;
  color: #113d76;
}

.brand-mark {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  overflow: hidden;
  background: linear-gradient(145deg, #f7fbff, #dcecff);
  border: 1px solid #cfe2fb;
  border-radius: 14px;
  box-shadow: 0 8px 18px rgba(45, 114, 205, 0.14);
}

.brand-logo {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  object-fit: cover;
}

.brand-copy {
  display: grid;
  gap: 1px;
  line-height: 1.1;
}

.brand-copy strong {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.brand-copy small {
  color: #7b98bd;
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.15em;
}

.desktop-nav {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.nav-link {
  position: relative;
  padding: 10px 13px;
  color: #5f7899;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
  transition: color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: #1f6fdf;
  background: #ebf4ff;
  transform: translateY(-1px);
}

.account-area {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
}

.login-button,
.account-button {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 17px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 750;
  transition: 0.2s ease;
}

.login-button {
  color: #fff;
  background: linear-gradient(135deg, #3e90fb, #1f67d8);
  box-shadow: 0 10px 20px rgba(40, 120, 240, 0.2);
}

.login-button:hover {
  color: #fff;
  background: linear-gradient(135deg, #58a0ff, #1f67d8);
  box-shadow: 0 13px 24px rgba(40, 120, 240, 0.28);
  transform: translateY(-1px);
}

.account-button {
  color: #245388;
  background: #f2f7ff;
  border-color: #d9e8fb;
}

.account-button:hover {
  color: #1f67d8;
  background: #e9f3ff;
  border-color: #bcd8fb;
}

.account-name {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-trigger {
  display: none;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  color: #1f6fdf;
  background: #edf5ff;
  border: 1px solid #d6e7fb;
  border-radius: 12px;
  font-size: 22px;
}

.mobile-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mobile-nav a,
.mobile-nav button {
  width: 100%;
  padding: 13px 14px;
  color: #243746;
  background: transparent;
  border: 0;
  border-radius: 10px;
  text-align: left;
}

.mobile-nav a:hover,
.mobile-nav a.router-link-active,
.mobile-nav button:hover {
  color: #1f6fdf;
  background: #edf5ff;
}

.mobile-divider {
  margin: 16px 0 4px;
  padding: 8px 14px;
  color: #8a98a5;
  border-top: 1px solid #e8edf2;
  font-size: 12px;
}

.mobile-login {
  margin-top: 12px;
  color: white !important;
  background: linear-gradient(135deg, #3e90fb, #1f67d8) !important;
  text-align: center !important;
}

@media (max-width: 1120px) {
  .desktop-nav {
    display: none;
  }

  .header-inner {
    justify-content: space-between;
  }

  .mobile-trigger {
    display: inline-flex;
  }
}

@media (max-width: 560px) {
  .header-inner {
    min-height: 64px;
    padding: 0 16px;
  }

  .brand {
    gap: 8px;
  }

  .brand-mark {
    width: 39px;
    height: 39px;
    border-radius: 12px;
  }

  .brand-logo {
    width: 34px;
    height: 34px;
  }

  .brand-copy strong {
    font-size: 17px;
  }

  .brand-copy small {
    font-size: 7px;
  }

  .account-button,
  .login-button {
    display: none;
  }
}
</style>
