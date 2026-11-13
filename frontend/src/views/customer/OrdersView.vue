<script setup>
import { computed, onMounted, ref } from 'vue'
import { ChevronRight, CircleAlert, MapPin, Receipt, RefreshCw, RotateCcw, ShoppingBag } from 'lucide-vue-next'
import { getMyOrders, getOrder } from '../../services/orderService'
import { useAuth } from '../../stores/authStore'
import { useOrderStore } from '../../stores/orderStore'
import { useReorder } from '../../composables/useReorder'
import { dateTime, money } from '../../utils/formatters'
import { ACTIVE_STATUSES } from '../../utils/status'
import { dishImage, onImageError } from '../../utils/images'
import EmptyState from '../../components/EmptyState.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const auth = useAuth()
const cart = useOrderStore()
const reorder = useReorder()
const orders = ref([])
const loading = ref(true)
const error = ref('')

const active = computed(() => orders.value.filter((o) => ACTIVE_STATUSES.includes(o.status)))
const past = computed(() => orders.value.filter((o) => !ACTIVE_STATUSES.includes(o.status)))

function trackLink(order) {
  const token = cart.trackingTokenFor(order.id)
  return token && !auth.isCustomer.value ? { path: `/orders/${order.id}`, query: { t: token } } : `/orders/${order.id}`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const mine = auth.isCustomer.value ? await getMyOrders() : []
    // Guest orders placed on this device (and not already in the account list).
    const known = new Set(mine.map((o) => o.id))
    const guest = await Promise.all(cart.state.guestOrders
      .filter((ref) => !known.has(ref.id))
      .slice(0, 10)
      .map((ref) => getOrder(ref.id, ref.token).catch(() => null)))
    orders.value = [...mine, ...guest.filter(Boolean)].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="container-app py-6 sm:py-10">
    <p class="eyebrow">Order history</p>
    <h1 class="mt-1 font-display text-4xl font-normal sm:text-5xl">My orders</h1>

    <p class="mt-2 text-sm text-ink-500">Orders placed on this device.</p>

    <div v-if="loading" class="mt-8 space-y-3"><div v-for="n in 3" :key="n" class="skeleton h-28 rounded-3xl" /></div>

    <EmptyState v-else-if="error" class="mt-8" :icon="CircleAlert" tone="error" title="Could not load your orders" :message="error">
      <button class="btn-primary" @click="load"><RefreshCw class="h-4 w-4" /> Try again</button>
    </EmptyState>

    <EmptyState v-else-if="!orders.length" class="mt-8" :icon="Receipt" title="No orders yet" message="When you place an order it will appear here so you can track it live.">
      <RouterLink to="/menu" class="btn-primary">Start an order</RouterLink>
    </EmptyState>

    <template v-else>
      <section v-if="active.length" class="mt-8">
        <h2 class="mb-3 text-sm font-extrabold tracking-wide text-ink-500 uppercase">In progress</h2>
        <div class="space-y-3">
          <RouterLink v-for="order in active" :key="order.id" :to="trackLink(order)" class="card flex items-center gap-4 p-4 ring-2 ring-brand-200 transition hover:shadow-lift">
            <span class="grid h-12 w-12 animate-pulse-ring place-items-center rounded-2xl bg-brand-500 text-white">
              <MapPin v-if="order.orderType === 'DINE_IN'" class="h-5 w-5" /><ShoppingBag v-else class="h-5 w-5" />
            </span>
            <div class="min-w-0 flex-1">
              <p class="font-extrabold">{{ order.orderNumber }}</p>
              <p class="text-xs text-ink-500">{{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : 'Takeaway' }} · {{ dateTime(order.createdAt) }}</p>
            </div>
            <StatusBadge :status="order.status" customer />
            <ChevronRight class="h-5 w-5 text-ink-300" />
          </RouterLink>
        </div>
      </section>

      <section v-if="past.length" class="mt-8">
        <h2 class="mb-3 text-sm font-extrabold tracking-wide text-ink-500 uppercase">Past orders</h2>
        <div class="grid gap-3 md:grid-cols-2">
          <article v-for="order in past" :key="order.id" class="card p-4">
            <div class="flex items-start justify-between gap-3">
              <div>
                <RouterLink :to="trackLink(order)" class="font-extrabold hover:text-brand-600">{{ order.orderNumber }}</RouterLink>
                <p class="text-xs text-ink-500">{{ dateTime(order.createdAt) }} · {{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : 'Takeaway' }}</p>
              </div>
              <StatusBadge :status="order.status" size="sm" />
            </div>
            <div class="mt-3 flex -space-x-2">
              <img v-for="item in (order.items || []).slice(0, 5)" :key="item.menuItemId" :src="dishImage(item)" :alt="item.name" class="h-10 w-10 rounded-full object-cover ring-2 ring-white" @error="onImageError">
            </div>
            <p class="mt-2 line-clamp-1 text-sm text-ink-600">{{ (order.items || []).map((i) => `${i.quantity}× ${i.name}`).join(', ') }}</p>
            <div class="mt-3 flex items-center justify-between border-t border-ink-100 pt-3">
              <strong>{{ money(order.totalAmount) }}</strong>
              <button v-if="order.items?.length" class="btn-light btn-sm" @click="reorder(order)"><RotateCcw class="h-3.5 w-3.5" /> Reorder</button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>
