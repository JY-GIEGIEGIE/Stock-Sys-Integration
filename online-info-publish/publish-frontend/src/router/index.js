import { createRouter, createWebHistory } from 'vue-router'

const Portal = () => import('../views/Portal.vue')
const StockDetail = () => import('../views/StockDetail.vue')

const routes = [
  { path: '/', name: 'Portal', component: Portal },
  { path: '/stock/:code', name: 'StockDetail', component: StockDetail, props: true }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
