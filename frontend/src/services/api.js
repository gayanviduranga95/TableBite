// Thin fetch wrapper. In development every call goes through the Vite proxy (/api),
// so the app works from phones on the LAN as well as from localhost.
const API_BASE = import.meta.env.VITE_API_URL || '/api'
const TOKEN_KEY = 'tablebite_session'

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message)
    this.status = status
    this.code = code
  }
}

export function getToken() {
  try { return localStorage.getItem(TOKEN_KEY) || '' } catch { return '' }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch { /* storage unavailable (private mode) */ }
}

// Kitchen screens unlock with a shared kitchen code instead of an account.
const KITCHEN_KEY = 'tablebite_kitchen_code'
export function getKitchenCode() {
  try { return localStorage.getItem(KITCHEN_KEY) || '' } catch { return '' }
}
export function setKitchenCode(code) {
  try {
    if (code) localStorage.setItem(KITCHEN_KEY, code)
    else localStorage.removeItem(KITCHEN_KEY)
  } catch { /* storage unavailable */ }
}

let unauthorizedHandler = null
export function onUnauthorized(handler) { unauthorizedHandler = handler }

export async function request(path, { method = 'GET', body, headers = {}, raw = false } = {}) {
  const token = getToken()
  const kitchenCode = token ? '' : getKitchenCode()
  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        ...(raw ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(kitchenCode ? { 'X-Staff-Access-Code': kitchenCode } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Cannot reach the restaurant server. Check your connection and try again.', 0, 'NETWORK')
  }
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && token && unauthorizedHandler) unauthorizedHandler()
    throw new ApiError(data?.message || `Request failed (${response.status})`, response.status, data?.code)
  }
  return data
}

export const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  delete: (path) => request(path, { method: 'DELETE' }),
}
