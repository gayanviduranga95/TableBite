const DEFAULT_WS_URL = 'ws://localhost:8081'
const listeners = new Map()

function buildUrl({ accessCode = '', orderId = null } = {}) {
  const baseUrl = import.meta.env.VITE_WS_URL || DEFAULT_WS_URL
  const params = new URLSearchParams()
  if (accessCode) params.set('accessCode', accessCode)
  if (orderId) params.set('orderId', String(orderId))
  const suffix = params.toString()
  return suffix ? `${baseUrl}?${suffix}` : baseUrl
}

function registerSocket(socket, callback) {
  if (!socket) return null
  const handler = (event) => {
    try {
      const payload = JSON.parse(event.data)
      callback(payload)
    } catch (error) {
      console.warn('WebSocket payload was not valid JSON', error)
    }
  }
  socket.addEventListener('message', handler)
  listeners.set(socket, handler)
  return socket
}

function removeSocketListener(socket) {
  if (!socket) return
  const handler = listeners.get(socket)
  if (handler) {
    socket.removeEventListener('message', handler)
    listeners.delete(socket)
  }
}

function attachReconnect(socket, reconnectAction) {
  socket._keepAlive = true
  socket.addEventListener('close', () => {
    if (!socket._keepAlive) return
    setTimeout(() => reconnectAction(), 1000)
  })
}

function connectSocket({ accessCode = '', orderId = null, callback, subscribePayload }) {
  const socket = new WebSocket(buildUrl({ accessCode, orderId }))
  attachReconnect(socket, () => {
    connectSocket({ accessCode, orderId, callback, subscribePayload })
  })
  socket.addEventListener('open', () => {
    socket.send(JSON.stringify(subscribePayload))
  })
  return registerSocket(socket, callback)
}

export function connectKitchenSocket(callback) {
  const accessCode = sessionStorage.getItem('tablebite_staff_access') || ''
  return connectSocket({
    accessCode,
    callback,
    subscribePayload: { type: 'SUBSCRIBE', channel: 'kitchen', accessCode },
  })
}

export function connectOrderSocket(orderId, callback) {
  return connectSocket({
    orderId,
    callback,
    subscribePayload: { type: 'SUBSCRIBE', channel: 'order', orderId },
  })
}

export function disconnectSocket(socket) {
  if (!socket) return
  removeSocketListener(socket)
  socket._keepAlive = false
  if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
    socket.close()
  }
}

export function socketStatus(socket) {
  if (!socket) return 'disconnected'
  if (socket.readyState === WebSocket.CONNECTING) return 'connecting'
  if (socket.readyState === WebSocket.OPEN) return 'connected'
  if (socket.readyState === WebSocket.CLOSING) return 'closing'
  return 'disconnected'
}
