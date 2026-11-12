<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createOrder } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'

const router = useRouter()
const store = useOrderStore()
const error = ref('')
const saving = ref(false)
const itemCount = computed(() => store.state.cart.reduce((sum, item) => sum + item.quantity, 0))

async function submit() {
  if (!store.state.tableToken) {
    error.value = 'Scan a table QR code before placing an order.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const result = await createOrder(
      store.state.tableToken,
      store.state.cart.map((item) => ({ menuItemId: item.id, quantity: item.quantity })),
    )
    store.state.orderId = result.orderId
    store.clear()
    router.push(`/orders/${result.orderId}`)
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="checkout-page">
    <button class="back-link" @click="router.back()">← Continue browsing</button>
    <div class="checkout-heading"><p class="eyebrow">Almost there</p><h1>Your order</h1></div>
    <div v-if="!store.state.cart.length" class="empty-state">
      <span class="empty-icon">🛒</span><h2>Your basket is waiting</h2><p>Add something delicious from the menu.</p>
      <button class="button" @click="router.push('/')">Browse menu</button>
    </div>
    <div v-else class="checkout-layout">
      <div>
        <div class="table-context"><span>⌖</span><div><small>Ordering for</small><strong>Table {{ store.state.tableNumber || 'your table' }}</strong></div></div>
        <article v-for="item in store.state.cart" :key="item.id" class="cart-item">
          <img :src="item.image || 'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=200&q=80'" :alt="item.name">
          <div class="cart-item-info"><h3>{{ item.name }}</h3><span>Rs {{ Number(item.price).toFixed(2) }} each</span></div>
          <div class="quantity-control">
            <button aria-label="Decrease quantity" @click="store.remove(item)">−</button>
            <b>{{ item.quantity }}</b>
            <button aria-label="Increase quantity" @click="store.add(item)">＋</button>
          </div>
          <strong class="item-total">Rs {{ (Number(item.price) * item.quantity).toFixed(2) }}</strong>
        </article>
      </div>
      <aside class="summary-card">
        <p class="eyebrow">Order summary</p><h2>Ready to order?</h2>
        <div class="summary-line"><span>Items ({{ itemCount }})</span><strong>Rs {{ Number(store.total).toFixed(2) }}</strong></div>
        <div class="summary-line muted"><span>Service</span><span>At your table</span></div>
        <div class="summary-total"><span>Total</span><strong>Rs {{ Number(store.total).toFixed(2) }}</strong></div>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="button checkout-button" :disabled="saving" @click="submit">{{ saving ? 'Sending to kitchen...' : 'Place order  →' }}</button>
        <small class="secure-note">✦ No payment needed · Pay at the counter</small>
      </aside>
    </div>
  </section>
</template>
