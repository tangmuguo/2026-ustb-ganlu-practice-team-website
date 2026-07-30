import { createRouter, createWebHistory } from 'vue-router'
import { userinfoStore } from '@/stores/userStore'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: false } // 指定布局
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/regt',
      name: 'regt',
      component: () => import('@/views/RegisteTeam.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0] } // 指定布局
    },
    {
      path: '/regs',
      name: 'regs',
      component: () => import('@/views/RegisteStudent.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/uppt',
      name: 'uppt',
      component: () => import('@/views/UpLoadMaterials.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0, 1] } // 指定布局
    },
    {
      path: '/showm',   //  课件
      name: 'showm',
      component: () => import('@/views/ShowMaterials.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/mdetail/:id',   //  课件详细页
      name: 'mdetail',
      component: () => import('@/views/MaterialDetail.vue'),
      meta: { layout: 'EmptyLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/mmanage/',   //  课件管理
      name: 'mmanage',
      component: () => import('@/views/MaterialManage.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0, 1] } // 指定布局
    },
    {
      path: '/team-content-manage',   // 团队风采内容管理
      name: 'teamContentManage',
      component: () => import('@/views/TeamContentManage.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0, 1] }
    },
    {
      path: '/admin/team-content',   // 管理员审核面板
      name: 'adminTeamContent',
      component: () => import('@/views/TeamContentManage.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0] }
    },
    {
      path: '/fengcai',   //团队风采
      name: 'fengcai',
      component: () => import('@/views/FengCai.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/fengcaidetail/:id/:name',   //风采详细
      name: 'fengcaidetail',
      component: () => import('@/views/FengCaiDetail.vue'),
      meta: { layout: 'EmptyLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/messageboard',   //留言
      name: 'messageboard',
      component: () => import('@/views/MessageBoard.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true } // 指定布局
    },
    {
      path: '/muser',   //团队（用户）管理
      name: 'muser',
      component: () => import('@/views/ManageUser.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0] } // 指定布局
    },
    {
      path: '/mstudent',   //学生（用户）管理
      name: 'mstudent',
      component: () => import('@/views/ManageStudents.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0, 1] } // 指定布局
    },
    {
      path: '/mbanner',   //轮播图管理
      name: 'mbanner',
      component: () => import('@/views/BannerManagement.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0] } // 指定布局
    },
    {
      path: '/mnews',   //新闻管理
      name: 'mnews',
      component: () => import('@/views/ManageNews.vue'),
      meta: { layout: 'DefaultLayout', hideBanner: true, requiresAuth: true, roles: [0] } // 指定布局
    },
    // {
    //   path: '/about',
    //   name: 'about',
    //   // route level code-splitting
    //   // this generates a separate chunk (About.[hash] for the route
    //   // which is lazy-loaded when the route is visited.
    //   component: () => import('../views/AboutView.vue'),
    // },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return true

  const userStore = userinfoStore()
  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (Array.isArray(to.meta.roles) && !to.meta.roles.includes(userStore.currentUser.level)) {
    return { path: '/' }
  }

  return true
})

export default router
