<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrder } from '../../services/orderService'
import { connectOrderSocket, disconnectSocket } from '../../services/socketService'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const error = ref('')
const loading = ref(true)
const socket = ref(null)
let timer
const steps = ['PENDING', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED']
const statusLabel = { PENDING: 'Order received', ACCEPTED: 'Accepted by kitchen', PREPARING: 'Being prepared', READY: 'Ready to enjoy', SERVED: 'Enjoy your meal' }
const currentStep = computed(() => steps.indexOf(order.value?.status))

async function load() {
  try {
    order.value = await getOrder(route.params.id)
    if (['SERVED', 'CANCELLED'].includes(order.value.status)) {
      clearInterval(timer)
    }
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

function handleSocketEvent(event) {
  if (event.type === 'ORDER_STATUS_CHANGED' && Number(event.orderId) === Number(route.params.id)) {
    order.value = { ...order.value, status: event.status }
    if (['SERVED', 'CANCELLED'].includes(event.status)) {
      clearInterval(timer)
    }
  }
}

onMounted(() => {
  load()
  socket.value = connectOrderSocket(Number(route.params.id), handleSocketEvent)
  socket.value.addEventListener('open', () => {
    load()
  })
  timer = setInterval(() => {
    if (!socket.value || socket.value.readyState !== WebSocket.OPEN) {
      load()
    }
  }, 5000)
})

onUnmounted(() => {
  clearInterval(timer)
  disconnectSocket(socket.value)
})
</script>

<template>
  <section class="tracking-page">
    <div v-if="loading" class="loading-state"><div class="spinner"></div><p>Finding your order...</p></div>
    <template v-else-if="order">
      <div class="success-header">
        <div class="success-mark">{{ order.status === 'CANCELLED' ? '!' : '✓' }}</div>
        <p class="eyebrow">{{ order.status === 'CANCELLED' ? 'Order update' : 'Thank you' }}</p>
        <h1>{{ order.status === 'CANCELLED' ? 'Order cancelled' : 'Order confirmed' }}</h1>
        <p>{{ order.status === 'CANCELLED' ? 'Please ask a team member if you need help.' : 'Your order is on its way to the kitchen.' }}</p>
      </div>
      <div class="order-number">ORDER <strong>#{{ order.id }}</strong><span>•</span> TABLE {{ order.tableId }}</div>
      <div v-if="order.status === 'CANCELLED'" class="notice">This order was cancelled.</div>
      <div v-else class="timeline-card">
        <div v-for="(step, index) in steps" :key="step" class="timeline-step" :class="{ complete: index <= currentStep, current: index === currentStep }">
          <div class="timeline-dot">{{ index < currentStep ? '✓' : index + 1 }}</div>
          <span>{{ statusLabel[step] }}</span>
          <i v-if="index < steps.length - 1"></i>
        </div>
      </div>
      <div class="order-detail-card">
        <div class="page-heading"><div><p class="eyebrow">Your items</p><h2>Order details</h2></div><strong class="status-text">{{ statusLabel[order.status] || order.status }}</strong></div>
        <div v-for="item in order.items" :key="item.menuItemId" class="detail-line"><span>{{ item.quantity }} × {{ item.name }}</span><strong>Rs {{ (Number(item.unitPrice) * item.quantity).toFixed(2) }}</strong></div>
      </div>
      <button class="button" @click="router.push('/')">Order more</button>
    </template>
    <p v-else class="error">{{ error || 'Order not found' }}</p>
  </section>
</template>
