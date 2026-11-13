<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  Armchair, ChefHat, CircleAlert, CircleCheck, ClipboardList, Clock, Hourglass, RefreshCw,
  TrendingUp, Users, Wallet,
} from 'lucide-vue-next'
import { getStats } from '../../services/orderService'
import { useStaffEvents } from '../../composables/useStaffEvents'
import { money, shortMoney, timeAgo } from '../../utils/formatters'
import { dishImage, onImageError } from '../../utils/images'
import BarChart from '../../components/BarChart.vue'
import EmptyState from '../../components/EmptyState.vue'
import OrderDetailsModal from '../../components/OrderDetailsModal.vue'
import StatCard from '../../components/StatCard.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const stats = ref(null)
const loading = ref(true)
const error = ref('')
const days = ref(14)
const selected = ref(null)

const totals = computed(() => stats.value?.totals)
const revenueSeries = computed(() => (stats.value?.revenueByDay || []).map((d) => {
  const date = new Date(`${d.day}T00:00:00`)
  return {
    label: date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' }),
    value: Number(d.revenue),
    sub: `${d.orders} order${d.orders === 1 ? '' : 's'}`,
  }
}))
const ordersSeries = computed(() => Array.from({ length: 24 }, (_, hour) => ({
  label: `${String(hour).padStart(2, '0')}:00`,
  value: Number(stats.value?.ordersByHour?.[hour] || 0),
})).filter((_, hour) => hour >= 8 || stats.value?.ordersByHour?.[hour]))
const maxPopular = computed(() => Math.max(1, ...(stats.value?.popularItems || []).map((p) => p.quantity)))
const pipeline = computed(() => totals.value ? [
  { label: 'Pending', value: totals.value.pending, tone: 'bg-amber-500' },
  { label: 'Confirmed', value: totals.value.confirmed, tone: 'bg-sky-500' },
  { label: 'Preparing', value: totals.value.preparing, tone: 'bg-violet-500' },
  { label: 'Ready', value: totals.value.ready, tone: 'bg-emerald-500' },
  { label: 'Served', value: totals.value.served, tone: 'bg-teal-500' },
  { label: 'Completed', value: totals.value.completed, tone: 'bg-stone-400' },
  { label: 'Cancelled', value: totals.value.cancelled, tone: 'bg-red-500' },
] : [])

async function load() {
  try {
    stats.value = await getStats(days.value)
    error.value = ''
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

useStaffEvents(() => load())
onMounted(load)
</script>

<template>
  <div>
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="eyebrow">Overview</p>
        <h1 class="mt-1 text-3xl font-extrabold">Dashboard</h1>
        <p class="text-sm text-ink-500">{{ new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }) }} · updates live as orders arrive</p>
      </div>
      <div class="flex gap-2">
        <select v-model.number="days" class="input w-auto" aria-label="Revenue range" @change="load">
          <option :value="7">Last 7 days</option>
          <option :value="14">Last 14 days</option>
          <option :value="30">Last 30 days</option>
        </select>
        <button class="btn-light" @click="load"><RefreshCw class="h-4 w-4" /> Refresh</button>
      </div>
    </div>

    <EmptyState v-if="error && !stats" class="mt-8" :icon="CircleAlert" tone="error" title="Could not load the dashboard" :message="error">
      <button class="btn-primary" @click="load">Try again</button>
    </EmptyState>

    <div v-else-if="loading" class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="n in 8" :key="n" class="skeleton h-32 rounded-3xl" />
    </div>

    <template v-else-if="stats">
      <!-- KPIs -->
      <div class="mt-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <StatCard label="Today's revenue" :value="money(totals.todayRevenue)" :hint="`${totals.todayOrders} orders today`" :icon="TrendingUp" />
        <StatCard label="Total revenue" :value="money(totals.totalRevenue)" :hint="`${money(totals.paidRevenue)} collected`" :icon="Wallet" accent="bg-emerald-50 text-emerald-600" />
        <StatCard label="Today's orders" :value="totals.todayOrders" :hint="`${totals.totalOrders} orders all time`" :icon="ClipboardList" accent="bg-sky-50 text-sky-600" />
        <StatCard label="Average order" :value="money(totals.averageOrderValue)" :hint="`${totals.dineIn} dine-in · ${totals.takeaway} takeaway`" :icon="Users" accent="bg-violet-50 text-violet-600" />
        <StatCard label="Pending" :value="totals.pending" hint="Waiting for the kitchen" :icon="Hourglass" accent="bg-amber-50 text-amber-600" />
        <StatCard label="Preparing" :value="totals.preparing + totals.confirmed" :hint="`${totals.ready} ready to serve`" :icon="ChefHat" accent="bg-brand-50 text-brand-600" />
        <StatCard label="Completed" :value="totals.completed + totals.served" :hint="`${totals.cancelled} cancelled`" :icon="CircleCheck" accent="bg-emerald-50 text-emerald-600" />
        <StatCard label="Tables in use" :value="`${totals.activeTables}/${totals.tables}`" :hint="`${totals.customers} registered customers`" :icon="Armchair" accent="bg-stone-100 text-stone-600" />
      </div>

      <div class="mt-6 grid gap-6 xl:grid-cols-3">
        <!-- Revenue chart -->
        <section class="card-pad xl:col-span-2">
          <div class="flex items-start justify-between">
            <div>
              <h2 class="font-extrabold">Daily revenue</h2>
              <p class="text-xs text-ink-500">Excludes cancelled orders · last {{ days }} days</p>
            </div>
            <p class="text-right"><span class="block text-2xl font-extrabold">{{ money(revenueSeries.reduce((s, d) => s + d.value, 0)) }}</span><span class="text-xs text-ink-500">in this period</span></p>
          </div>
          <div class="mt-4"><BarChart :data="revenueSeries" :format="money" :axis-format="shortMoney" title="Daily revenue" /></div>
        </section>

        <!-- Pipeline -->
        <section class="card-pad">
          <h2 class="font-extrabold">Order status</h2>
          <p class="text-xs text-ink-500">All orders by current status</p>
          <ul class="mt-5 space-y-3">
            <li v-for="row in pipeline" :key="row.label">
              <div class="flex justify-between text-sm"><span class="font-semibold text-ink-700">{{ row.label }}</span><span class="font-extrabold tabular-nums">{{ row.value }}</span></div>
              <div class="mt-1.5 h-2 rounded-full bg-ink-100">
                <div class="h-2 rounded-full transition-all duration-700" :class="row.tone" :style="{ width: `${totals.totalOrders ? Math.max(row.value ? 3 : 0, (row.value / totals.totalOrders) * 100) : 0}%` }" />
              </div>
            </li>
          </ul>
        </section>
      </div>

      <div class="mt-6 grid gap-6 xl:grid-cols-3">
        <!-- Popular dishes -->
        <section class="card-pad">
          <h2 class="font-extrabold">Popular dishes</h2>
          <p class="text-xs text-ink-500">By quantity sold</p>
          <p v-if="!stats.popularItems.length" class="mt-6 text-sm text-ink-500">No sales yet.</p>
          <ul class="mt-4 space-y-3">
            <li v-for="(item, index) in stats.popularItems" :key="item.id" class="flex items-center gap-3">
              <span class="w-4 text-xs font-extrabold text-ink-400">{{ index + 1 }}</span>
              <img :src="dishImage(item)" :alt="item.name" class="h-10 w-10 rounded-xl object-cover" @error="onImageError">
              <div class="min-w-0 flex-1">
                <div class="flex justify-between gap-2 text-sm"><span class="truncate font-semibold">{{ item.name }}</span><span class="font-extrabold tabular-nums">{{ item.quantity }}</span></div>
                <div class="mt-1 h-1.5 rounded-full bg-ink-100"><div class="h-1.5 rounded-full bg-brand-600" :style="{ width: `${(item.quantity / maxPopular) * 100}%` }" /></div>
                <p class="mt-0.5 text-[11px] text-ink-400">{{ money(item.revenue) }}</p>
              </div>
            </li>
          </ul>
        </section>

        <!-- Orders by hour -->
        <section class="card-pad">
          <h2 class="font-extrabold">Orders by hour</h2>
          <p class="text-xs text-ink-500">Today</p>
          <div class="mt-4"><BarChart :data="ordersSeries" :height="200" :format="(v) => `${v} orders`" title="Orders by hour today" /></div>
        </section>

        <!-- Recent orders -->
        <section class="card-pad">
          <div class="flex items-center justify-between">
            <h2 class="font-extrabold">Recent orders</h2>
            <RouterLink to="/admin/orders" class="text-xs font-bold text-brand-600">View all</RouterLink>
          </div>
          <p v-if="!stats.recentOrders.length" class="mt-6 text-sm text-ink-500">No orders yet.</p>
          <ul class="mt-3 divide-y divide-ink-100">
            <li v-for="order in stats.recentOrders" :key="order.id">
              <button class="flex w-full items-center gap-3 py-2.5 text-left hover:bg-ink-50/60" @click="selected = order.id">
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-bold">{{ order.orderNumber }}</p>
                  <p class="flex items-center gap-1 text-xs text-ink-500"><Clock class="h-3 w-3" /> {{ timeAgo(order.createdAt) }} · {{ order.tableNumber ? `Table ${order.tableNumber}` : 'Takeaway' }}</p>
                </div>
                <div class="text-right">
                  <StatusBadge :status="order.status" size="sm" />
                  <p class="mt-0.5 text-xs font-bold">{{ money(order.totalAmount) }}</p>
                </div>
              </button>
            </li>
          </ul>
        </section>
      </div>
    </template>

    <OrderDetailsModal :order-id="selected" @close="selected = null" @changed="load" />
  </div>
</template>
