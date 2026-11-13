import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/authStore'

const customer = (path, component, meta = {}) => ({ path, component, meta: { layout: 'customer', ...meta } })
const staff = (path, component, roles, meta = {}) => ({ path, component, meta: { layout: 'staff', roles, ...meta } })
const ADMIN = ['ADMIN']
const KITCHEN = ['ADMIN', 'KITCHEN', 'STAFF']
const FLOOR = ['ADMIN', 'WAITER', 'STAFF']

const routes = [
  // ---------- customer app
  customer('/', () => import('../views/customer/HomeView.vue')),
  customer('/menu', () => import('../views/customer/MenuView.vue'), { title: 'Menu' }),
  customer('/menu/:id', () => import('../views/customer/FoodDetailView.vue'), { title: 'Dish' }),
  customer('/table/:token', () => import('../views/customer/TableEntryView.vue'), { title: 'Your table' }),
  customer('/scan', () => import('../views/customer/ScanView.vue'), { title: 'Scan table QR' }),
  customer('/cart', () => import('../views/customer/CartView.vue'), { title: 'Your cart' }),
  customer('/orders', () => import('../views/customer/OrdersView.vue'), { title: 'My orders' }),
  customer('/orders/:id', () => import('../views/customer/OrderTrackingView.vue'), { title: 'Track order' }),
  // Customers order as guests - no customer sign-in.
  { path: '/login', redirect: '/' },
  { path: '/register', redirect: '/' },
  { path: '/profile', redirect: '/orders' },

  // ---------- staff
  { path: '/staff/login', component: () => import('../views/staff/StaffAccessView.vue'), meta: { layout: 'bare', title: 'Staff sign in' } },
  // The kitchen unlocks with the kitchen code (no account needed).
  { path: '/kitchen', component: () => import('../views/kitchen/KitchenPage.vue'), meta: { layout: 'bare', title: 'Kitchen display' } },
  staff('/floor', () => import('../views/waiter/WaiterDashboard.vue'), FLOOR, { title: 'Floor' }),
  staff('/admin', () => import('../views/admin/AdminDashboard.vue'), ADMIN, { title: 'Dashboard' }),
  staff('/admin/orders', () => import('../views/admin/AdminOrders.vue'), ['ADMIN', 'WAITER', 'KITCHEN', 'STAFF'], { title: 'Orders' }),
  staff('/admin/menu', () => import('../views/admin/AdminMenu.vue'), ADMIN, { title: 'Menu' }),
  staff('/admin/tables', () => import('../views/admin/AdminTables.vue'), ADMIN, { title: 'Tables & QR' }),
  staff('/admin/payments', () => import('../views/admin/AdminPayments.vue'), ['ADMIN', 'WAITER', 'STAFF'], { title: 'Payments' }),
  staff('/admin/settings', () => import('../views/admin/AdminSettings.vue'), ADMIN, { title: 'Settings' }),
  { path: '/admin/tables/print', component: () => import('../views/admin/AdminTablePrint.vue'), meta: { layout: 'bare', roles: ADMIN, title: 'Print QR codes' } },

  // ---------- legacy URLs from the original project
  { path: '/staff-access', redirect: '/staff/login' },
  { path: '/internal/kitchen', redirect: '/kitchen' },
  { path: '/internal/orders', redirect: '/floor' },
  { path: '/internal/admin', redirect: '/admin' },
  { path: '/waiter', redirect: '/floor' },

  customer('/:pathMatch(.*)*', () => import('../views/customer/NotFoundView.vue'), { title: 'Not found' }),
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.restore()
  const user = auth.state.user

  if (to.meta.guestOnly && user) {
    return user.role === 'CUSTOMER' ? '/profile' : auth.staffHome(user.role)
  }
  if (to.meta.roles) {
    if (!user) {
      return { path: to.meta.loginPath || '/staff/login', query: { redirect: to.fullPath } }
    }
    if (!to.meta.roles.includes(user.role)) {
      return user.role === 'CUSTOMER' ? '/' : auth.staffHome(user.role)
    }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · TableBite` : 'TableBite · Order from your table'
})

export default router
