// Live notifications over the Java WebSocket server (proxied at /ws in development).
// Every connection automatically reconnects and re-subscribes after network drops.
import { getKitchenCode, getToken } from './api'

function defaultUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws`
}

/**
 * Opens a managed socket. Returns { close(), status() }.
 * onEvent receives every parsed server message; onStatus gets 'connecting' | 'live' | 'offline'.
 */
function connect(subscribe, onEvent, onStatus = () => {}) {
  let socket = null
  let closed = false
  let retry = 0
  let timer = null

  const open = () => {
    if (closed) return
    onStatus('connecting')
    socket = new WebSocket(defaultUrl())
    socket.addEventListener('open', () => {
      retry = 0
      socket.send(JSON.stringify(subscribe()))
    })
    socket.addEventListener('message', (event) => {
      let payload
      try { payload = JSON.parse(event.data) } catch { return }
      if (payload.type === 'SUBSCRIBED') onStatus('live')
      onEvent(payload)
    })
    socket.addEventListener('close', () => {
      onStatus('offline')
      if (closed) return
      retry += 1
      timer = setTimeout(open, Math.min(15000, 1000 * 2 ** Math.min(retry, 4)))
    })
    socket.addEventListener('error', () => socket.close())
  }
  open()

  return {
    close() {
      closed = true
      clearTimeout(timer)
      if (socket && socket.readyState <= WebSocket.OPEN) socket.close()
    },
    status: () => (socket?.readyState === WebSocket.OPEN ? 'live' : 'offline'),
  }
}

/** Staff screens: every order event. */
export function connectStaffSocket(onEvent, onStatus) {
  return connect(() => ({ type: 'SUBSCRIBE', channel: 'staff', token: getToken(), accessCode: getKitchenCode() || undefined }), onEvent, onStatus)
}

/** Order tracking page: one order (guest uses its secret tracking token). */
export function connectOrderSocket(orderId, trackingToken, onEvent, onStatus) {
  return connect(() => ({
    type: 'SUBSCRIBE', channel: 'order', orderId: Number(orderId), trackingToken, token: getToken(),
  }), onEvent, onStatus)
}

/** Signed-in customers: updates for all of their orders. */
export function connectUserSocket(onEvent, onStatus) {
  return connect(() => ({ type: 'SUBSCRIBE', channel: 'user', token: getToken() }), onEvent, onStatus)
}
