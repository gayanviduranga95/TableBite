<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOrders, updateOrderStatus } from '../../services/orderService'
const orders = ref([])
const error = ref('')
const active = computed(() => orders.value.filter((order) => !['SERVED', 'CANCELLED'].includes(order.status)).length)
async function load() { try { orders.value = await getOrders() } catch (exception) { error.value = exception.message } }
async function serve(order) { try { await updateOrderStatus(order.id, 'SERVED'); await load() } catch (exception) { error.value = exception.message } }
onMounted(load)
</script>
<template>
  <section class="staff-page">
    <div class="staff-hero"><div><p class="eyebrow">Front of house</p><h1>Restaurant orders</h1><p>Stay one step ahead of every table.</p></div><div class="stat-card"><small>Active orders</small><strong>{{ active }}</strong></div></div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="order-grid">
      <article v-for="order in orders" :key="order.id" class="staff-order"><div class="staff-order-head"><h2>Order #{{ order.id }}</h2><span class="status-badge">{{ order.status }}</span></div><p>Table {{ order.tableId }}</p><small>{{ order.createdAt }}</small><div class="staff-actions"><button v-if="order.status === 'READY'" @click="serve(order)">Mark served</button></div></article>
    </div>
  </section>
</template>
