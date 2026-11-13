// Cart, table context and guest order references, persisted in localStorage so a
// page refresh (or closing the browser tab) never loses the customer's basket.
import { computed, reactive, watch } from 'vue'
import { useSettings } from './settingsStore'

const KEY = 'tablebite_cart_v2'
const TABLE_TTL_MS = 8 * 60 * 60 * 1000

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || '{}')
    if (saved.table && Date.now() - (saved.table.savedAt || 0) > TABLE_TTL_MS) saved.table = null
    return saved
  } catch {
    return {}
  }
}

const saved = load()
const state = reactive({
  items: Array.isArray(saved.items) ? saved.items : [],
  table: saved.table || null, // { token, tableNumber, savedAt }
  orderType: saved.orderType || 'DINE_IN',
  guestOrders: Array.isArray(saved.guestOrders) ? saved.guestOrders : [], // { id, token, orderNumber, createdAt }
  lastOrder: saved.lastOrder || null,
})

watch(state, () => {
  try { localStorage.setItem(KEY, JSON.stringify(state)) } catch { /* ignore */ }
}, { deep: true })

const round = (value) => Math.round(value * 100) / 100

export function useOrderStore() {
  const settings = useSettings()

  const count = computed(() => state.items.reduce((sum, item) => sum + item.quantity, 0))
  const subtotal = computed(() => round(state.items.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0)))
  const tax = computed(() => round(subtotal.value * settings.taxRate / 100))
  const serviceCharge = computed(() => (state.orderType === 'DINE_IN' ? round(subtotal.value * settings.serviceRate / 100) : 0))
  const total = computed(() => round(subtotal.value + tax.value + serviceCharge.value))
  const hasUnavailable = computed(() => state.items.some((item) => item.available === false))

  function quantityOf(id) {
    return state.items.find((entry) => entry.id === id)?.quantity || 0
  }

  function add(item, quantity = 1, notes) {
    if (item.available === false) return false
    const existing = state.items.find((entry) => entry.id === item.id)
    if (existing) {
      existing.quantity = Math.min(50, existing.quantity + quantity)
      if (notes !== undefined) existing.notes = notes
    } else {
      state.items.push({
        id: item.id, name: item.name, price: Number(item.price), imageUrl: item.imageUrl,
        category: item.category, available: true, quantity: Math.min(50, quantity), notes: notes || '',
      })
    }
    return true
  }

  function setQuantity(id, quantity) {
    const existing = state.items.find((entry) => entry.id === id)
    if (!existing) return
    if (quantity <= 0) state.items.splice(state.items.indexOf(existing), 1)
    else existing.quantity = Math.min(50, quantity)
  }

  const increase = (id) => setQuantity(id, quantityOf(id) + 1)
  const decrease = (id) => setQuantity(id, quantityOf(id) - 1)
  const remove = (id) => setQuantity(id, 0)

  function setNotes(id, notes) {
    const existing = state.items.find((entry) => entry.id === id)
    if (existing) existing.notes = notes.slice(0, 255)
  }

  function clear() {
    state.items.splice(0)
  }

  /** Refreshes prices / availability of cart lines from the latest menu. */
  function syncWithMenu(menu) {
    const byId = new Map(menu.map((item) => [item.id, item]))
    state.items.forEach((line) => {
      const fresh = byId.get(line.id)
      if (!fresh) { line.available = false; return }
      line.price = Number(fresh.price)
      line.name = fresh.name
      line.imageUrl = fresh.imageUrl
      line.available = fresh.available
    })
  }

  function setTable(table) {
    state.table = table ? { token: table.qrToken || table.token, tableNumber: table.tableNumber, savedAt: Date.now() } : null
    if (table) state.orderType = 'DINE_IN'
  }

  function rememberOrder(order, trackingToken) {
    const entry = { id: order.id, token: trackingToken, orderNumber: order.orderNumber, createdAt: order.createdAt }
    state.guestOrders = [entry, ...state.guestOrders.filter((o) => o.id !== order.id)].slice(0, 20)
    state.lastOrder = entry
  }

  function trackingTokenFor(id) {
    return state.guestOrders.find((o) => Number(o.id) === Number(id))?.token || ''
  }

  return {
    state, count, subtotal, tax, serviceCharge, total, hasUnavailable,
    quantityOf, add, setQuantity, increase, decrease, remove, setNotes, clear, syncWithMenu,
    setTable, rememberOrder, trackingTokenFor,
  }
}
