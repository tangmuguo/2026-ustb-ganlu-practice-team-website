import { createRouter, createWebHistory } from 'vue-router'
import { userinfoStore } from '@/stores/userStore'

const defaultMeta = { layout: 'DefaultLayout', hideBanner: true }

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
      meta: { ...defaultMeta, hideBanner: false, title: '首页' },
    },
    {
      path: '/about',
      name: 'about-ganlu',
      component: () => import('@/views/AboutGanlu.vue'),
      meta: { ...defaultMeta, title: '关于甘露' },
    },
    {
      path: '/contact',
      name: 'contact-us',
      component: () => import('@/views/ContactUs.vue'),
      meta: { ...defaultMeta, title: '联系我们' },
    },
    {
      path: '/join',
      name: 'join-ganlu',
      component: () => import('@/views/JoinGanlu.vue'),
      meta: { ...defaultMeta, title: '加入甘露' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { ...defaultMeta, title: '登录' },
    },
    {
      path: '/regs',
      name: 'register-student',
      component: () => import('@/views/RegisteStudent.vue'),
      meta: { ...defaultMeta, title: '学生注册' },
    },
    {
      path: '/regt',
      name: 'register-team',
      component: () => import('@/views/RegisteTeam.vue'),
      meta: { ...defaultMeta, title: '创建团队账号', requiresAuth: true, roles: [0] },
    },
    {
      path: '/ai',
      name: 'ai-assistant',
      component: () => import('@/views/AiAssistant.vue'),
      meta: { ...defaultMeta, title: 'AI 小助手', requiresAuth: true, roles: [0, 1, 2] },
    },
    {
      path: '/showm',
      name: 'materials',
      component: () => import('@/views/ShowMaterials.vue'),
      meta: { ...defaultMeta, title: '课件共享' },
    },
    {
      path: '/mdetail/:id',
      name: 'material-detail',
      component: () => import('@/views/MaterialDetail.vue'),
      meta: { ...defaultMeta, title: '课件详情' },
    },
    {
      path: '/uppt',
      name: 'material-upload',
      component: () => import('@/views/UpLoadMaterials.vue'),
      meta: { ...defaultMeta, title: '上传课件', requiresAuth: true, roles: [0, 1] },
    },
    {
      path: '/mmanage',
      alias: '/mmanage/',
      name: 'material-manage',
      component: () => import('@/views/MaterialManage.vue'),
      meta: { ...defaultMeta, title: '课件管理', requiresAuth: true, roles: [0, 1] },
    },
    {
      path: '/fengcai',
      name: 'fengcai',
      component: () => import('@/views/FengCai.vue'),
      meta: { ...defaultMeta, title: '团队风采' },
    },
    {
      path: '/fengcai/:year(\\d{4})',
      name: 'fengcai-year',
      component: () => import('@/views/FengCaiTeamList.vue'),
      meta: { ...defaultMeta, title: '年度小队' },
    },
    {
      path: '/fengcai/team/:teamId',
      name: 'fengcai-team-detail',
      component: () => import('@/views/FengCaiDetail.vue'),
      meta: { ...defaultMeta, title: '小队详情' },
    },
    {
      path: '/fengcaidetail/:id/:name?',
      redirect: (to) => `/fengcai/team/${to.params.id}`,
    },
    {
      path: '/messageboard',
      name: 'message-board',
      component: () => import('@/views/MessageBoard.vue'),
      meta: { ...defaultMeta, title: '互动留言' },
    },
    {
      path: '/muser',
      name: 'team-account-manage',
      component: () => import('@/views/ManageUser.vue'),
      meta: { ...defaultMeta, title: '团队账号管理', requiresAuth: true, roles: [0] },
    },
    {
      path: '/mstudent',
      name: 'student-account-manage',
      component: () => import('@/views/ManageStudents.vue'),
      meta: { ...defaultMeta, title: '学生账号管理', requiresAuth: true, roles: [0, 1] },
    },
    {
      path: '/mbanner',
      name: 'banner-manage',
      component: () => import('@/views/BannerManagement.vue'),
      meta: { ...defaultMeta, title: '轮播图管理', requiresAuth: true, roles: [0] },
    },
    {
      path: '/mnews',
      name: 'news-manage',
      component: () => import('@/views/ManageNews.vue'),
      meta: { ...defaultMeta, title: '新闻管理', requiresAuth: true, roles: [0] },
    },
    {
      path: '/applications',
      name: 'volunteer-applications',
      component: () => import('@/views/ManageVolunteerApplications.vue'),
      meta: { ...defaultMeta, title: '志愿者报名管理', requiresAuth: true, roles: [0] },
    },
    {
      path: '/logh',
      name: 'team-log-manage',
      component: () => import('@/views/LogHonor.vue'),
      meta: { ...defaultMeta, title: '日志与荣誉', requiresAuth: true, roles: [0, 1] },
    },
    {
      path: '/photo',
      name: 'team-photo-manage',
      component: () => import('@/views/Photos.vue'),
      meta: { ...defaultMeta, title: '团队照片', requiresAuth: true, roles: [0, 1] },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFound.vue'),
      meta: { ...defaultMeta, title: '页面不存在' },
    },
  ],
})

router.beforeEach((to) => {
  document.title = `${to.meta.title || '甘露支教'} - 甘露支教`

  if (!to.meta.requiresAuth) return true

  const userStore = userinfoStore()
  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const level = Number(userStore.currentUser?.level)
  if (Array.isArray(to.meta.roles) && !to.meta.roles.includes(level)) {
    return { path: '/', query: { denied: '1' } }
  }

  return true
})

export default router
