// One shared staff WebSocket; pages subscribe to order events through this bus.
import { onBeforeUnmount, ref } from 'vue'
import { connectStaffSocket } from '../services/socketService'

const listeners = new Set()
const connection = ref('offline')
let socket = null
let users = 0

function ensureSocket() {
  if (socket) return
  socket = connectStaffSocket((event) => {
    if (['ORDER_CREATED', 'ORDER_STATUS_CHANGED', 'PAYMENT_UPDATED'].includes(event.type)) {
      listeners.forEach((listener) => listener(event))
    }
  }, (status) => { connection.value = status })
}

/** Subscribe the calling component to order events for its lifetime. */
export function useStaffEvents(handler) {
  users += 1
  ensureSocket()
  if (handler) listeners.add(handler)
  onBeforeUnmount(() => {
    if (handler) listeners.delete(handler)
    users -= 1
    if (users === 0) {
      socket?.close()
      socket = null
      connection.value = 'offline'
    }
  })
  return { connection }
}
