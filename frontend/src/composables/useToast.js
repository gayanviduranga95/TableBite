import { reactive } from 'vue'

const toasts = reactive([])
let nextId = 1

/** Toast notifications: toast.success('Saved'), toast.error(err), toast.info(...). */
export function useToast() {
  function push(type, message, { title, timeout = 3800 } = {}) {
    const id = nextId++
    toasts.push({ id, type, title, message })
    if (timeout) setTimeout(() => dismiss(id), timeout)
    return id
  }
  function dismiss(id) {
    const index = toasts.findIndex((toast) => toast.id === id)
    if (index >= 0) toasts.splice(index, 1)
  }
  return {
    toasts,
    dismiss,
    success: (message, options) => push('success', message, options),
    info: (message, options) => push('info', message, options),
    error: (error, options) => push('error', typeof error === 'string' ? error : error?.message || 'Something went wrong', { timeout: 6000, ...options }),
  }
}
