import { createRouter, createWebHashHistory } from 'vue-router'
import { session, waitForAuth } from './lib/auth'
import LoginView from './views/LoginView.vue'

export const router = createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', component: () => import('./views/RequestsView.vue') },
    { path: '/livrer', component: () => import('./views/DeliverView.vue') },
    { path: '/compte', component: () => import('./views/AccountView.vue') },
    { path: '/courier', redirect: '/compte' },
    { path: '/courses/new', component: () => import('./views/NewCourseView.vue') },
    { path: '/courses/:id', component: () => import('./views/CourseView.vue'), props: true },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  await waitForAuth()
  if (!to.meta.public && !session.value) {
    return { path: '/login', query: { next: to.fullPath } }
  }
  if (to.path === '/login' && session.value) {
    return '/'
  }
  return true
})
