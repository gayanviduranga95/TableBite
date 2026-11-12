import { computed, reactive } from 'vue'

const state = reactive({
  cart: [],
  tableToken: '',
  tableNumber: '',
  orderId: null,
})

export function useOrderStore() {
  const total = computed(() =>
    state.cart.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0),
  )

  function add(item) {
    const existing = state.cart.find((entry) => entry.id === item.id)
    if (existing) existing.quantity += 1
    else state.cart.push({ ...item, quantity: 1 })
  }

  function setTable(table) {
    state.tableToken = table.token || state.tableToken
    state.tableNumber = table.tableNumber || state.tableNumber
  }

  function remove(item) {
    const existing = state.cart.find((entry) => entry.id === item.id)
    if (!existing) return
    if (existing.quantity === 1) {
      state.cart.splice(state.cart.indexOf(existing), 1)
    }
    else existing.quantity -= 1
  }

  function clear() {
    state.cart.splice(0)
  }

  return {
    state,
    get total() {
      return total.value
    },
    add,
    remove,
    clear,
    setTable,
  }
}
