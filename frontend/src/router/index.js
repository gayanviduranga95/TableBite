import { createRouter, createWebHistory } from 'vue-router'
import MenuView from '../views/customer/MenuView.vue'
import CartView from '../views/customer/CartView.vue'
import OrderTrackingView from '../views/customer/OrderTrackingView.vue'
import KitchenDashboard from '../views/kitchen/KitchenDashboard.vue'
import WaiterDashboard from '../views/waiter/WaiterDashboard.vue'
import AdminDashboard from '../views/admin/AdminDashboard.vue'
import ScanRequiredView from '../views/customer/ScanRequiredView.vue'
import StaffAccessView from '../views/staff/StaffAccessView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ScanRequiredView },
    { path: '/table/:token', component: MenuView },
    { path: '/cart', component: CartView },
    { path: '/orders/:id', component: OrderTrackingView },
    { path: '/staff-access', component: StaffAccessView },
    { path: '/internal/kitchen', component: KitchenDashboard, meta: { internal: true } },
    { path: '/internal/orders', component: WaiterDashboard, meta: { internal: true } },
    { path: '/internal/admin', component: AdminDashboard, meta: { internal: true } },
    { path: '/kitchen', redirect: '/internal/kitchen' },
    { path: '/orders', redirect: '/internal/orders' },
    { path: '/waiter', redirect: '/internal/orders' },
    { path: '/admin', redirect: '/internal/admin' },
  ],
})

router.beforeEach((to) => {
  if (to.meta.internal && sessionStorage.getItem('tablebite_staff_access') !== import.meta.env.VITE_STAFF_ACCESS_CODE) {
    return { path: '/staff-access', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
