<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getOrders, updateOrderStatus } from '../../services/orderService'
import { connectKitchenSocket, disconnectSocket } from '../../services/socketService'

const orders = ref([])
const error = ref('')
const socket = ref(null)

const counts = computed(() => ({
  pending: orders.value.filter((order) => order.status === 'PENDING').length,
  preparing: orders.value.filter((order) => order.status === 'PREPARING').length,
  ready: orders.value.filter((order) => order.status === 'READY').length,
}))

async function load() {
  try {
    orders.value = await getOrders()
  } catch (exception) {
    error.value = exception.message
  }
}

async function update(order, status) {
  try {
    await updateOrderStatus(order.id, status)
    await load()
  } catch (exception) {
    error.value = exception.message
  }
}

onMounted(async () => {
  await load()
  socket.value = connectKitchenSocket((event) => {
    if (event.type === 'ORDER_CREATED') {
      load()
    }
  })
})

onUnmounted(() => {
  disconnectSocket(socket.value)
})
</script>
<template>
  <section class="staff-page">
    <div class="staff-hero"><div><p class="eyebrow">Kitchen workspace</p><h1>Good morning, chef.</h1><p>Keep every order moving with care.</p></div><button class="button" @click="load">Refresh orders</button></div>
    <div class="stats-grid"><div class="stat-card"><small>Pending</small><strong>{{ counts.pending }}</strong></div><div class="stat-card"><small>Preparing</small><strong>{{ counts.preparing }}</strong></div><div class="stat-card"><small>Ready to serve</small><strong>{{ counts.ready }}</strong></div></div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="order-grid">
      <article v-for="order in orders" :key="order.id" class="staff-order">
        <div class="staff-order-head"><h2>Order #{{ order.id }}</h2><span class="status-badge">{{ order.status }}</span></div>
        <p>Table {{ order.tableId }} · {{ order.createdAt }}</p>
        <div class="staff-actions">
          <button v-if="order.status === 'PENDING'" @click="update(order, 'ACCEPTED')">Accept order</button>
          <button v-if="order.status === 'ACCEPTED'" @click="update(order, 'PREPARING')">Start preparing</button>
          <button v-if="order.status === 'PREPARING'" @click="update(order, 'READY')">Mark ready</button>
        </div>
      </article>
    </div>
    <div v-if="!orders.length && !error" class="empty-state"><span class="empty-icon">✦</span><h2>Kitchen is clear</h2><p>New orders will appear here.</p></div>
  </section>
</template>
