import { createRouter, createWebHistory } from 'vue-router'

// History mode: nginx.conf falls back to index.html for unknown paths.
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'courses', component: () => import('@/views/CoursesView.vue') },
    { path: '/courses/:id', name: 'course', component: () => import('@/views/CourseView.vue'), props: true },
    { path: '/lectures/:id', name: 'lecture', component: () => import('@/views/LectureView.vue'), props: true },
  ],
})
