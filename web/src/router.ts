import { createRouter, createWebHashHistory } from 'vue-router'
import { session, waitForAuth } from './lib/auth'
import LoginView from './views/LoginView.vue'
import HomeView from './views/HomeView.vue'
import NewCourseView from './views/NewCourseView.vue'
import CourseView from './views/CourseView.vue'
import CourierView from './views/CourierView.vue'

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', component: HomeView },
    { path: '/courses/new', component: NewCourseView },
    { path: '/courses/:id', component: CourseView, props: true },
    { path: '/courier', component: CourierView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  await waitForAuth()
  if (!to.meta.public && !session.value) {
    return { path: '/login', query: { next: to.fullPath } }
  }
  return true
})
