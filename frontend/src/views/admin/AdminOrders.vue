<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ClipboardList, LoaderCircle, MapPin, RefreshCw, Search, ShoppingBag } from 'lucide-vue-next'
import { getOrders } from '../../services/orderService'
import { useAuth } from '../../stores/authStore'
import { useStaffEvents } from '../../composables/useStaffEvents'
import { useOrderActions } from '../../composables/useOrderActions'
import { dateTime, money, timeAgo } from '../../utils/formatters'
import { nextActions, PAYMENT_LABELS, STATUS } from '../../utils/status'
import EmptyState from '../../components/EmptyState.vue'
import OrderDetailsModal from '../../components/OrderDetailsModal.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const auth = useAuth()
const orders = ref([])
const loading = ref(true)
const error = ref('')
const selected = ref(null)
const filters = ref({ status: '', q: '', type: '', range: 'today', payment: '' })
let searchTimer = null

const tabs = computed(() => [{ value: '', label: 'All' }, ...Object.entries(STATUS).map(([value, meta]) => ({ value, label: meta.label }))])
const totalShown = computed(() => orders.value.filter((o) => o.status !== 'CANCELLED').reduce((sum, o) => sum + Number(o.totalAmount), 0))

async function load() {
  try {
    orders.value = await getOrders({ ...filters.value, limit: 300 })
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

const { busy, setStatus } = useOrderActions(() => load())
useStaffEvents(() => load())
watch(() => [filters.value.status, filters.value.type, filters.value.range, filters.value.payment], () => { loading.value = true; load() })
watch(() => filters.value.q, () => { clearTimeout(searchTimer); searchTimer = setTimeout(load, 300) })
onMounted(load)
</script>

<template>
  <div>
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="eyebrow">Operations</p>
        <h1 class="mt-1 text-3xl font-extrabold">Orders</h1>
        <p class="text-sm text-ink-500">{{ orders.length }} orders · {{ money(totalShown) }} (excluding cancelled)</p>
      </div>
      <button class="btn-light" @click="load"><RefreshCw class="h-4 w-4" /> Refresh</button>
    </div>

    <!-- Filters -->
    <div class="card mt-6 p-3">
      <div class="flex flex-col gap-2 lg:flex-row">
        <label class="flex flex-1 items-center gap-2 rounded-xl border border-ink-200 px-3 focus-within:border-brand-400 focus-within:ring-4 focus-within:ring-brand-100">
          <Search class="h-4 w-4 text-ink-400" />
          <input v-model="filters.q" type="search" class="min-w-0 flex-1 bg-transparent py-2.5 text-sm outline-none" placeholder="Search order number, table, customer or phone" aria-label="Search orders">
        </label>
        <div class="grid grid-cols-3 gap-2">
          <select v-model="filters.range" class="input" aria-label="Date range"><option value="today">Today</option><option value="">All time</option></select>
          <select v-model="filters.type" class="input" aria-label="Order type"><option value="">All types</option><option value="DINE_IN">Dine in</option><option value="TAKEAWAY">Takeaway</option></select>
          <select v-model="filters.payment" class="input" aria-label="Payment"><option value="">Any payment</option><option value="UNPAID">Unpaid</option><option value="PAID">Paid</option></select>
        </div>
      </div>
      <div class="no-scrollbar mt-3 flex gap-1.5 overflow-x-auto">
        <button v-for="tab in tabs" :key="tab.value" class="rounded-lg px-3 py-1.5 text-sm font-semibold whitespace-nowrap transition" :class="filters.status === tab.value ? 'bg-ink-900 text-white' : 'text-ink-500 hover:bg-ink-100'" @click="filters.status = tab.value">{{ tab.label }}</button>
      </div>
    </div>

    <p v-if="error" class="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700">{{ error }}</p>

    <div v-if="loading" class="mt-6 space-y-2"><div v-for="n in 6" :key="n" class="skeleton h-16 rounded-2xl" /></div>

    <EmptyState v-else-if="!orders.length" class="mt-6" :icon="ClipboardList" title="No orders found" :message="filters.range === 'today' ? 'No orders match these filters today. Try “All time”.' : 'Try changing the filters.'" />

    <template v-else>
      <!-- Desktop table -->
      <div class="card mt-6 hidden overflow-x-auto md:block">
        <table class="table-base">
          <thead><tr><th>Order</th><th>Table / type</th><th>Customer</th><th>Items</th><th>Total</th><th>Payment</th><th>Status</th><th class="text-right">Actions</th></tr></thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id" class="cursor-pointer" @click="selected = order.id">
              <td><p class="font-bold">{{ order.orderNumber }}</p><p class="text-xs text-ink-400" :title="dateTime(order.createdAt)">{{ timeAgo(order.createdAt) }}</p></td>
              <td>
                <span class="inline-flex items-center gap-1.5 font-semibold">
                  <MapPin v-if="order.orderType === 'DINE_IN'" class="h-4 w-4 text-brand-500" /><ShoppingBag v-else class="h-4 w-4 text-brand-500" />
                  {{ order.orderType === 'DINE_IN' ? order.tableNumber : 'Takeaway' }}
                </span>
              </td>
              <td><p class="font-semibold">{{ order.customerName || 'Guest' }}</p><p v-if="order.customerPhone" class="text-xs text-ink-400">{{ order.customerPhone }}</p></td>
              <td class="tabular-nums">{{ order.itemCount }}</td>
              <td class="font-bold tabular-nums">{{ money(order.totalAmount) }}</td>
              <td><span class="text-xs font-bold" :class="order.paymentStatus === 'PAID' ? 'text-emerald-600' : 'text-amber-600'">{{ order.paymentStatus === 'PAID' ? 'Paid' : 'Unpaid' }}</span><p class="text-xs text-ink-400">{{ PAYMENT_LABELS[order.paymentMethod] }}</p></td>
              <td><StatusBadge :status="order.status" /></td>
              <td class="text-right" @click.stop>
                <div class="inline-flex gap-1.5">
                  <button v-for="action in nextActions(order, auth.role.value)" :key="action.status" class="btn-sm" :class="action.style" :disabled="busy[order.id]" @click="setStatus(order, action.status)">
                    <LoaderCircle v-if="busy[order.id]" class="h-3.5 w-3.5 animate-spin" />{{ action.label }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Mobile cards -->
      <div class="mt-6 space-y-3 md:hidden">
        <article v-for="order in orders" :key="order.id" class="card p-4" @click="selected = order.id">
          <div class="flex items-start justify-between gap-2">
            <div>
              <p class="font-extrabold">{{ order.orderNumber }}</p>
              <p class="text-xs text-ink-500">{{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : 'Takeaway' }} · {{ timeAgo(order.createdAt) }}</p>
            </div>
            <StatusBadge :status="order.status" size="sm" />
          </div>
          <div class="mt-3 flex items-center justify-between">
            <span class="font-bold">{{ money(order.totalAmount) }} <span class="text-xs font-semibold" :class="order.paymentStatus === 'PAID' ? 'text-emerald-600' : 'text-amber-600'">· {{ order.paymentStatus === 'PAID' ? 'Paid' : 'Unpaid' }}</span></span>
            <div class="flex gap-1.5" @click.stop>
              <button v-for="action in nextActions(order, auth.role.value)" :key="action.status" class="btn-sm" :class="action.style" :disabled="busy[order.id]" @click="setStatus(order, action.status)">{{ action.label }}</button>
            </div>
          </div>
        </article>
      </div>
    </template>

    <OrderDetailsModal :order-id="selected" @close="selected = null" @changed="load" />
  </div>
</template>
