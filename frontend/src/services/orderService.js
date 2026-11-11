const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(body.message || 'Request failed')
  }
  return body
}

export const getMenu = () => request('/menu')
export const getTable = (token) => request(`/tables/${encodeURIComponent(token)}`)
export const createOrder = (tableToken, items) =>
  request('/orders', { method: 'POST', body: JSON.stringify({ tableToken, items }) })
export const getOrder = (id) => request(`/orders/${id}`)
const staffOptions = () => ({
  headers: { 'X-Staff-Access-Code': sessionStorage.getItem('tablebite_staff_access') || '' },
})
export const getOrders = () => request('/orders', staffOptions())
export const updateOrderStatus = (id, status) =>
  request(`/orders/${id}`, {
    method: 'PATCH',
    ...staffOptions(),
    body: JSON.stringify({ status }),
  })
