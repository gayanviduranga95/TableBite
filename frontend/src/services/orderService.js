import { api, request } from './api'

const qs = (params = {}) => {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== false) search.set(key, String(value))
  })
  const text = search.toString()
  return text ? `?${text}` : ''
}

// ---------- public
export const getSettings = () => api.get('/settings')
export const getMenu = () => api.get('/menu')
export const getMenuItem = (id) => api.get(`/menu/${id}`)
export const getCategories = () => api.get('/categories')
export const getTable = (token) => api.get(`/tables/${encodeURIComponent(token)}`)
export const lookupTable = (tableNumber) => api.post('/tables/lookup', { tableNumber })

// ---------- auth
export const register = (data) => api.post('/auth/register', data)
export const login = (email, password) => api.post('/auth/login', { email, password })
export const logout = () => api.post('/auth/logout')
export const getMe = () => api.get('/auth/me')
export const updateProfile = (data) => api.put('/auth/me', data)
export const changePassword = (currentPassword, newPassword) => api.put('/auth/password', { currentPassword, newPassword })

// ---------- customer orders
export const createOrder = (payload) => api.post('/orders', payload)
export const getOrder = (id, token) => api.get(`/orders/${id}${qs({ token })}`)
export const cancelOrder = (id, token, reason) => api.post(`/orders/${id}/cancel`, { token, reason })
export const getMyOrders = () => api.get('/me/orders')

// ---------- staff
export const getOrders = (filters = {}) => api.get(`/orders${qs(filters)}`)
export const updateOrderStatus = (id, status, reason) => api.patch(`/orders/${id}`, { status, reason })
export const confirmPayment = (id, method, reference) => api.post(`/orders/${id}/payments`, { method, reference })
export const getKitchenOrders = () => api.get('/kitchen/orders')

// ---------- admin
export const getStats = (days = 14) => api.get(`/admin/stats${qs({ days })}`)
export const getAdminMenu = () => api.get('/admin/menu')
export const createMenuItem = (data) => api.post('/admin/menu', data)
export const updateMenuItem = (id, data) => api.put(`/admin/menu/${id}`, data)
export const setAvailability = (id, available) => api.patch(`/admin/menu/${id}/availability`, { available })
export const deleteMenuItem = (id) => api.delete(`/admin/menu/${id}`)
export const uploadImage = (file) => request('/admin/uploads', {
  method: 'POST', body: file, raw: true, headers: { 'Content-Type': file.type || 'application/octet-stream' },
})
export const getAdminCategories = () => api.get('/admin/categories')
export const createCategory = (data) => api.post('/admin/categories', data)
export const updateCategory = (id, data) => api.put(`/admin/categories/${id}`, data)
export const deleteCategory = (id) => api.delete(`/admin/categories/${id}`)
export const getTables = () => api.get('/admin/tables')
export const createTable = (data) => api.post('/admin/tables', data)
export const updateTable = (id, data) => api.put(`/admin/tables/${id}`, data)
export const deleteTable = (id) => api.delete(`/admin/tables/${id}`)
export const regenerateTableToken = (id) => api.post(`/admin/tables/${id}/regenerate`)
export const getTableOrders = (id) => api.get(`/admin/tables/${id}/orders`)
export const getPayments = (filters = {}) => api.get(`/admin/payments${qs(filters)}`)
export const getAdminSettings = () => api.get('/admin/settings')
export const saveSettings = (data) => api.put('/admin/settings', data)
export const getUsers = (role) => api.get(`/admin/users${qs({ role })}`)
export const createUser = (data) => api.post('/admin/users', data)
export const updateUserAccess = (id, role, active) => api.patch(`/admin/users/${id}`, { role, active })
