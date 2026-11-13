<script setup>
import { computed, onMounted, ref } from 'vue'
import { Armchair, BellRing, CircleCheck, LoaderCircle, MapPin, RefreshCw, ShoppingBag, Wallet } from 'lucide-vue-next'
import { getOrders, getTables } from '../../services/orderService'
import { useAuth } from '../../stores/authStore'
import { useStaffEvents } from '../../composables/useStaffEvents'
import { useOrderActions } from '../../composables/useOrderActions'
import { money, timeAgo } from '../../utils/formatters'
import { nextActions } from '../../utils/status'
import OrderDetailsModal from '../../components/OrderDetailsModal.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const auth = useAuth()
const orders = ref([])
const tables = ref([])
const loading = ref(true)
const error = ref('')
const selected = ref(null)

const ready = computed(() => orders.value.filter((o) => o.status === 'READY'))
const unpaid = computed(() => orders.value.filter((o) => o.paymentStatus !== 'PAID' && ['SERVED', 'COMPLETED', 'READY'].includes(o.status)))
const toComplete = computed(() => orders.value.filter((o) => o.status === 'SERVED'))
const activeByTable = computed(() => {
  const map = {}
  orders.value.filter((o) => !['COMPLETED', 'CANCELLED'].includes(o.status) && o.tableId)
    .forEach((o) => { (map[o.tableId] ||= []).push(o) })
  return map
})

async function load() {
  try {
    ;[orders.value, tables.value] = await Promise.all([getOrders({ limit: 300 }), getTables()])
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

const { busy, setStatus, markPaid } = useOrderActions(() => load())
useStaffEvents(() => load())
onMounted(load)

const tableTone = {
  AVAILABLE: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  OCCUPIED: 'bg-brand-50 text-brand-700 ring-brand-200',
  RESERVED: 'bg-sky-50 text-sky-700 ring-sky-200',
  OUT_OF_SERVICE: 'bg-stone-100 text-stone-500 ring-stone-200',
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="eyebrow">Front of house</p>
        <h1 class="mt-1 text-3xl font-extrabold">Floor service</h1>
        <p class="text-sm text-ink-500">Serve ready orders, take payments and keep an eye on every table.</p>
      </div>
      <button class="btn-light" @click="load"><RefreshCw class="h-4 w-4" /> Refresh</button>
    </div>
    <p v-if="error" class="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700">{{ error }}</p>

    <div v-if="loading" class="mt-8 grid place-items-center py-20"><LoaderCircle class="h-8 w-8 animate-spin text-brand-500" /></div>

    <template v-else>
      <div class="mt-6 grid gap-6 lg:grid-cols-3">
        <!-- Ready to serve -->
        <section class="card-pad lg:col-span-2">
          <h2 class="flex items-center gap-2 text-lg font-extrabold"><BellRing class="h-5 w-5 text-emerald-600" /> Ready to serve <span class="text-ink-400">{{ ready.length }}</span></h2>
          <p v-if="!ready.length" class="mt-6 rounded-2xl bg-ink-50 py-10 text-center text-sm text-ink-500">No orders waiting. The kitchen will notify you here.</p>
          <div v-else class="mt-4 grid gap-3 sm:grid-cols-2">
            <article v-for="order in ready" :key="order.id" class="rounded-2xl border-2 border-emerald-200 bg-emerald-50/50 p-4">
              <div class="flex items-start justify-between">
                <div>
                  <p class="flex items-center gap-1.5 text-xl font-extrabold">
                    <MapPin v-if="order.orderType === 'DINE_IN'" class="h-5 w-5 text-brand-500" /><ShoppingBag v-else class="h-5 w-5 text-brand-500" />
                    {{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : order.customerName || 'Takeaway' }}
                  </p>
                  <button class="text-xs font-semibold text-ink-500 hover:text-brand-600" @click="selected = order.id">{{ order.orderNumber }} · {{ order.itemCount }} items · {{ timeAgo(order.updatedAt) }}</button>
                </div>
                <StatusBadge :status="order.status" size="sm" />
              </div>
              <div class="mt-3 flex gap-2">
                <button v-for="action in nextActions(order, auth.role.value)" :key="action.status" :class="action.style" class="flex-1" :disabled="busy[order.id]" @click="setStatus(order, action.status)">
                  <CircleCheck class="h-4 w-4" /> {{ action.label }}
                </button>
              </div>
            </article>
          </div>
        </section>

        <!-- Payments -->
        <section class="card-pad">
          <h2 class="flex items-center gap-2 text-lg font-extrabold"><Wallet class="h-5 w-5 text-brand-600" /> Awaiting payment <span class="text-ink-400">{{ unpaid.length }}</span></h2>
          <p v-if="!unpaid.length" class="mt-6 rounded-2xl bg-ink-50 py-10 text-center text-sm text-ink-500">All bills are settled.</p>
          <ul v-else class="mt-4 space-y-2">
            <li v-for="order in unpaid" :key="order.id" class="flex items-center gap-3 rounded-2xl border border-ink-100 p-3">
              <button class="min-w-0 flex-1 text-left" @click="selected = order.id">
                <p class="font-bold">{{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : order.customerName || 'Takeaway' }}</p>
                <p class="text-xs text-ink-500">{{ order.orderNumber }} · {{ money(order.totalAmount) }}</p>
              </button>
              <button class="btn-success btn-sm" :disabled="busy[order.id]" @click="markPaid(order, order.paymentMethod === 'CASH' ? 'CASH' : 'PAY_AT_COUNTER')">Paid</button>
            </li>
          </ul>
          <template v-if="toComplete.length">
            <h3 class="mt-6 text-sm font-extrabold text-ink-500">Served — close when guests leave</h3>
            <ul class="mt-2 space-y-2">
              <li v-for="order in toComplete" :key="order.id" class="flex items-center justify-between gap-2 rounded-xl bg-ink-50 px-3 py-2 text-sm">
                <span class="font-semibold">{{ order.tableNumber ? `Table ${order.tableNumber}` : 'Takeaway' }} · {{ order.orderNumber }}</span>
                <button class="btn-dark btn-sm" :disabled="busy[order.id]" @click="setStatus(order, 'COMPLETED')">Complete</button>
              </li>
            </ul>
          </template>
        </section>
      </div>

      <!-- Tables -->
      <section class="card-pad mt-6">
        <h2 class="flex items-center gap-2 text-lg font-extrabold"><Armchair class="h-5 w-5 text-brand-600" /> Tables</h2>
        <div class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
          <div v-for="table in tables" :key="table.id" class="rounded-2xl p-4 ring-1" :class="tableTone[table.status]">
            <p class="text-2xl font-extrabold">{{ table.tableNumber }}</p>
            <p class="text-xs font-semibold">{{ table.status.replaceAll('_', ' ').toLowerCase() }} · {{ table.seats }} seats</p>
            <div class="mt-2 flex flex-wrap gap-1">
              <button v-for="order in activeByTable[table.id] || []" :key="order.id" @click="selected = order.id"><StatusBadge :status="order.status" size="sm" /></button>
            </div>
          </div>
        </div>
      </section>
    </template>

    <OrderDetailsModal :order-id="selected" @close="selected = null" @changed="load" />
  </div>
</template>
