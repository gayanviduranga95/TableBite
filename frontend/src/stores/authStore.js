import { computed, reactive } from 'vue'
import { getToken, onUnauthorized, setToken } from '../services/api'
import * as service from '../services/orderService'

const state = reactive({ user: null, ready: false })
let readyPromise = null

const STAFF_HOME = { ADMIN: '/admin', KITCHEN: '/kitchen', WAITER: '/floor' }

onUnauthorized(() => {
  setToken('')
  state.user = null
})

export function useAuth() {
  async function restore() {
    if (!readyPromise) {
      readyPromise = (async () => {
        if (getToken()) {
          try { state.user = await service.getMe() } catch { setToken(''); state.user = null }
        }
        state.ready = true
      })()
    }
    return readyPromise
  }

  async function login(email, password) {
    const session = await service.login(email, password)
    setToken(session.token)
    state.user = session.user
    return session.user
  }

  async function register(data) {
    const session = await service.register(data)
    setToken(session.token)
    state.user = session.user
    return session.user
  }

  async function logout() {
    try { await service.logout() } catch { /* already invalid */ }
    setToken('')
    state.user = null
  }

  async function refresh() {
    state.user = await service.getMe()
  }

  return {
    state,
    restore,
    login,
    register,
    logout,
    refresh,
    user: computed(() => state.user),
    isCustomer: computed(() => state.user?.role === 'CUSTOMER'),
    isStaff: computed(() => !!state.user && state.user.role !== 'CUSTOMER'),
    role: computed(() => state.user?.role || null),
    staffHome: (role = state.user?.role) => STAFF_HOME[role] || '/',
  }
}
