<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOrders } from '../../services/orderService'
const orders = ref([])
const error = ref('')
const served = computed(() => orders.value.filter((order) => order.status === 'SERVED').length)
onMounted(async () => { try { orders.value = await getOrders() } catch (exception) { error.value = exception.message } })
</script>
<template>
  <section class="staff-page">
    <div class="staff-hero"><div><p class="eyebrow">Demo admin view</p><h1>Today at a glance</h1><p>Monitor the live order flow from one place.</p></div></div>
    <p class="notice">This MVP view is unauthenticated and is not production-safe.</p>
    <div class="stats-grid"><div class="stat-card"><small>Total orders</small><strong>{{ orders.length }}</strong></div><div class="stat-card"><small>Served</small><strong>{{ served }}</strong></div><div class="stat-card"><small>In progress</small><strong>{{ orders.length - served }}</strong></div></div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="order-grid"><article v-for="order in orders" :key="order.id" class="staff-order"><div class="staff-order-head"><h2>Order #{{ order.id }}</h2><span class="status-badge">{{ order.status }}</span></div><p>Table {{ order.tableId }}</p></article></div>
  </section>
</template>
