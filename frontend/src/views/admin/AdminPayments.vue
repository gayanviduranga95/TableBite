<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Banknote, CreditCard, Info, RefreshCw, Store, Wallet } from 'lucide-vue-next'
import { getPayments } from '../../services/orderService'
import { useSettings } from '../../stores/settingsStore'
import { useStaffEvents } from '../../composables/useStaffEvents'
import { dateTime, money } from '../../utils/formatters'
import { PAYMENT_LABELS } from '../../utils/status'
import EmptyState from '../../components/EmptyState.vue'
import StatCard from '../../components/StatCard.vue'

const settings = useSettings()
const payments = ref([])
const loading = ref(true)
const error = ref('')
const filters = ref({ status: '', method: '', range: 'today' })

const STATUS_TONE = {
  PAID: 'bg-emerald-50 text-emerald-700', PENDING: 'bg-amber-50 text-amber-700',
  CANCELLED: 'bg-stone-100 text-stone-500', FAILED: 'bg-red-50 text-red-700', REFUNDED: 'bg-sky-50 text-sky-700',
}
const collected = computed(() => payments.value.filter((p) => p.status === 'PAID').reduce((s, p) => s + Number(p.amount), 0))
const outstanding = computed(() => payments.value.filter((p) => p.status === 'PENDING').reduce((s, p) => s + Number(p.amount), 0))
const byMethod = (method) => payments.value.filter((p) => p.status === 'PAID' && p.method === method).reduce((s, p) => s + Number(p.amount), 0)

async function load() {
  try {
    payments.value = await getPayments({ ...filters.value, limit: 500 })
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}
useStaffEvents((event) => { if (event.type !== 'ORDER_STATUS_CHANGED') load() })
watch(filters, load, { deep: true })
onMounted(load)
</script>

<template>
  <div>
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="eyebrow">Finance</p>
        <h1 class="mt-1 text-3xl font-extrabold">Payments</h1>
        <p class="text-sm text-ink-500">Every order creates a pending payment; staff confirm it when the bill is settled.</p>
      </div>
      <button class="btn-light" @click="load"><RefreshCw class="h-4 w-4" /> Refresh</button>
    </div>

    <div class="mt-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
      <StatCard label="Collected" :value="money(collected)" :icon="Wallet" accent="bg-emerald-50 text-emerald-600" />
      <StatCard label="Outstanding" :value="money(outstanding)" :icon="Store" accent="bg-amber-50 text-amber-600" />
      <StatCard label="Cash" :value="money(byMethod('CASH'))" :icon="Banknote" />
      <StatCard label="Card & counter" :value="money(byMethod('CARD') + byMethod('PAY_AT_COUNTER'))" :icon="CreditCard" accent="bg-sky-50 text-sky-600" />
    </div>

    <div v-if="!settings.state.online_payment_enabled" class="mt-5 flex gap-3 rounded-2xl bg-sky-50 p-4 text-sm text-sky-900 ring-1 ring-sky-200">
      <Info class="h-5 w-5 shrink-0 text-sky-600" />
      <p>Online card payments are not configured, so customers can only choose cash or pay at the counter. See <code>README.md → Payments</code> for connecting a gateway such as PayHere or Stripe.</p>
    </div>

    <div class="card mt-6 flex flex-wrap gap-2 p-3">
      <select v-model="filters.range" class="input w-auto" aria-label="Range"><option value="today">Today</option><option value="">All time</option></select>
      <select v-model="filters.status" class="input w-auto" aria-label="Status"><option value="">All statuses</option><option value="PAID">Paid</option><option value="PENDING">Pending</option><option value="CANCELLED">Cancelled</option></select>
      <select v-model="filters.method" class="input w-auto" aria-label="Method"><option value="">All methods</option><option v-for="(label, value) in PAYMENT_LABELS" :key="value" :value="value">{{ label }}</option></select>
    </div>

    <p v-if="error" class="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700">{{ error }}</p>
    <div v-if="loading" class="mt-6 space-y-2"><div v-for="n in 5" :key="n" class="skeleton h-14 rounded-2xl" /></div>
    <EmptyState v-else-if="!payments.length" class="mt-6" :icon="Wallet" title="No payments" message="Nothing matches these filters yet." />
    <div v-else class="card mt-6 overflow-x-auto">
      <table class="table-base">
        <thead><tr><th>Order</th><th>Table / customer</th><th>Method</th><th>Amount</th><th>Status</th><th>Received by</th><th>Reference</th><th>Date</th></tr></thead>
        <tbody>
          <tr v-for="p in payments" :key="p.id">
            <td class="font-bold">{{ p.orderNumber }}</td>
            <td>{{ p.tableNumber ? `Table ${p.tableNumber}` : 'Takeaway' }}<p v-if="p.customerName" class="text-xs text-ink-400">{{ p.customerName }}</p></td>
            <td>{{ PAYMENT_LABELS[p.method] }}</td>
            <td class="font-bold tabular-nums">{{ money(p.amount) }}</td>
            <td><span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="STATUS_TONE[p.status]">{{ p.status.toLowerCase() }}</span></td>
            <td>{{ p.receivedBy || '—' }}</td>
            <td class="text-xs text-ink-500">{{ p.reference || '—' }}</td>
            <td class="text-xs text-ink-500 whitespace-nowrap">{{ dateTime(p.paidAt || p.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
