<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  Ban, CircleCheck, Clock, LoaderCircle, MapPin, MessageSquareText, Radio, RefreshCw,
  RotateCcw, SearchX, ShoppingBag, Wallet, XCircle,
} from 'lucide-vue-next'
import { cancelOrder, getOrder } from '../../services/orderService'
import { connectOrderSocket } from '../../services/socketService'
import { useOrderStore } from '../../stores/orderStore'
import { useToast } from '../../composables/useToast'
import { useConfirm } from '../../composables/useConfirm'
import { useReorder } from '../../composables/useReorder'
import { showSystemNotification } from '../../composables/useNotifications'
import { dateTime, money, time } from '../../utils/formatters'
import { PAYMENT_LABELS, STATUS } from '../../utils/status'
import { dishImage, onImageError } from '../../utils/images'
import EmptyState from '../../components/EmptyState.vue'
import OrderProgress from '../../components/OrderProgress.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const route = useRoute()
const cart = useOrderStore()
const toast = useToast()
const { confirm } = useConfirm()
const reorder = useReorder()

const order = ref(null)
const loading = ref(true)
const error = ref('')
const live = ref('connecting')
const cancelling = ref(false)
const justPlaced = ref(route.query.placed === '1')
let socket = null
let poll = null

const orderId = computed(() => Number(route.params.id))
const token = computed(() => String(route.query.t || cart.trackingTokenFor(orderId.value) || ''))
const finished = computed(() => ['COMPLETED', 'CANCELLED'].includes(order.value?.status))

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    order.value = await getOrder(orderId.value, token.value || undefined)
    error.value = ''
  } catch (exception) {
    if (!silent) error.value = exception.message
  } finally {
    loading.value = false
  }
}

function onEvent(event) {
  if (Number(event.orderId) !== orderId.value || !event.order) return
  const previous = order.value?.status
  order.value = { ...order.value, ...event.order, items: order.value?.items || event.order.items, trackingToken: order.value?.trackingToken }
  if (event.type === 'ORDER_STATUS_CHANGED' && previous !== event.status) {
    toast.info(event.message, { title: STATUS[event.status]?.customer })
    showSystemNotification('TableBite', event.message)
    if (navigator.vibrate) navigator.vibrate([80, 40, 80])
    load(true) // refresh history timeline
  }
  if (event.type === 'PAYMENT_UPDATED') toast.success('Payment received — thank you!')
}

async function cancel() {
  const reason = await confirm({
    title: 'Cancel this order?',
    message: 'You can cancel while the kitchen hasn\'t accepted it yet.',
    confirmText: 'Cancel order',
    cancelText: 'Keep order',
    danger: true,
    input: true,
    inputLabel: 'Reason (optional)',
  })
  if (reason === null) return
  cancelling.value = true
  try {
    order.value = { ...(await cancelOrder(orderId.value, token.value || undefined, reason || undefined)), trackingToken: order.value.trackingToken }
    toast.success('Your order was cancelled')
  } catch (exception) {
    toast.error(exception)
    load(true)
  } finally {
    cancelling.value = false
  }
}

onMounted(async () => {
  await load()
  if (!order.value) return
  socket = connectOrderSocket(orderId.value, token.value, onEvent, (status) => {
    live.value = status
    if (status === 'live') load(true) // catch up on anything missed while offline
  })
  // Safety net when WebSockets are blocked by the network.
  poll = setInterval(() => { if (live.value !== 'live' && !finished.value) load(true) }, 10000)
})

onUnmounted(() => {
  socket?.close()
  clearInterval(poll)
})
</script>

<template>
  <div class="container-app py-6 sm:py-10">
    <div v-if="loading" class="mx-auto max-w-3xl space-y-4">
      <div class="skeleton h-32 rounded-3xl" /><div class="skeleton h-40 rounded-3xl" /><div class="skeleton h-56 rounded-3xl" />
    </div>

    <EmptyState v-else-if="!order" :icon="SearchX" title="We couldn't find this order" :message="error || 'Open the tracking link from the device you ordered on, or sign in to see your orders.'">
      <RouterLink to="/orders" class="btn-primary">My orders</RouterLink>
      <button class="btn-light" @click="load()"><RefreshCw class="h-4 w-4" /> Retry</button>
    </EmptyState>

    <div v-else class="mx-auto max-w-3xl space-y-5">
      <!-- Header -->
      <section class="relative overflow-hidden rounded-[2rem] bg-ink-900 p-6 text-white shadow-lift sm:p-8">
        <div class="absolute -top-20 -right-16 h-60 w-60 rounded-full blur-3xl" :class="order.status === 'CANCELLED' ? 'bg-red-500/30' : 'bg-brand-500/35'" />
        <div class="relative">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-bold">
              <Radio class="h-3.5 w-3.5" :class="live === 'live' ? 'text-emerald-400' : 'text-ink-400'" />
              {{ live === 'live' ? 'Live updates on' : finished ? 'Order closed' : 'Reconnecting…' }}
            </span>
            <StatusBadge :status="order.status" customer />
          </div>
          <div v-if="justPlaced && order.status === 'PENDING'" class="mt-6 flex items-center gap-3">
            <span class="grid h-12 w-12 animate-pop place-items-center rounded-2xl bg-leaf-500"><CircleCheck class="h-7 w-7" /></span>
            <p class="text-lg font-bold">Thank you! Your order was sent to the kitchen.</p>
          </div>
          <p class="mt-6 text-sm text-ink-300">Order number</p>
          <h1 class="font-display text-4xl font-normal tracking-normal text-white sm:text-5xl">{{ order.orderNumber }}</h1>
          <div class="mt-4 flex flex-wrap gap-2 text-sm">
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 font-semibold">
              <MapPin v-if="order.orderType === 'DINE_IN'" class="h-4 w-4 text-brand-300" /><ShoppingBag v-else class="h-4 w-4 text-brand-300" />
              {{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : 'Takeaway' }}
            </span>
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 font-semibold"><Clock class="h-4 w-4 text-brand-300" /> Placed {{ time(order.createdAt) }}</span>
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5 font-semibold"><Wallet class="h-4 w-4 text-brand-300" /> {{ PAYMENT_LABELS[order.paymentMethod] }} · {{ order.paymentStatus === 'PAID' ? 'Paid' : 'Unpaid' }}</span>
          </div>
        </div>
      </section>

      <!-- Progress -->
      <section v-if="order.status !== 'CANCELLED'" class="card-pad">
        <h2 class="text-lg font-extrabold">{{ STATUS[order.status].customer }}</h2>
        <p class="mt-1 text-sm text-ink-500">{{ order.orderType === 'TAKEAWAY' && order.status === 'READY' ? 'Your order is ready — please collect it at the counter.' : STATUS[order.status].description }}</p>
        <div class="mt-6"><OrderProgress :status="order.status" :order-type="order.orderType" /></div>
        <div v-if="order.status === 'PENDING'" class="mt-6 flex flex-col gap-2 border-t border-ink-100 pt-5 sm:flex-row sm:items-center sm:justify-between">
          <p class="text-sm text-ink-500">Changed your mind? You can cancel until the kitchen accepts your order.</p>
          <button class="btn-light text-red-600" :disabled="cancelling" @click="cancel">
            <LoaderCircle v-if="cancelling" class="h-4 w-4 animate-spin" /><Ban v-else class="h-4 w-4" /> Cancel order
          </button>
        </div>
      </section>
      <section v-else class="flex items-start gap-4 rounded-3xl bg-red-50 p-6 ring-1 ring-red-200">
        <XCircle class="h-7 w-7 shrink-0 text-red-600" />
        <div>
          <h2 class="font-extrabold text-red-800">This order was cancelled</h2>
          <p class="text-sm text-red-700">{{ order.cancelReason || 'Please speak to a member of staff if you need help.' }}</p>
        </div>
      </section>

      <!-- Items -->
      <section class="card overflow-hidden">
        <h2 class="border-b border-ink-100 px-6 py-4 font-extrabold">Your items</h2>
        <ul class="divide-y divide-ink-100">
          <li v-for="item in order.items" :key="`${item.menuItemId}-${item.notes}`" class="flex items-center gap-3 px-6 py-3">
            <img :src="dishImage(item)" :alt="item.name" class="h-12 w-12 rounded-xl object-cover" @error="onImageError">
            <div class="min-w-0 flex-1">
              <p class="font-semibold"><span class="text-brand-600">{{ item.quantity }}×</span> {{ item.name }}</p>
              <p v-if="item.notes" class="text-xs text-ink-500">“{{ item.notes }}”</p>
            </div>
            <strong class="text-sm">{{ money(item.lineTotal) }}</strong>
          </li>
        </ul>
        <div v-if="order.specialInstructions" class="mx-6 mb-4 flex gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
          <MessageSquareText class="mt-0.5 h-4 w-4 shrink-0" /> {{ order.specialInstructions }}
        </div>
        <dl class="space-y-2 border-t border-ink-100 bg-ink-50/50 px-6 py-4 text-sm">
          <div class="flex justify-between"><dt class="text-ink-500">Subtotal</dt><dd>{{ money(order.subtotal) }}</dd></div>
          <div v-if="Number(order.taxAmount)" class="flex justify-between"><dt class="text-ink-500">Tax</dt><dd>{{ money(order.taxAmount) }}</dd></div>
          <div v-if="Number(order.serviceCharge)" class="flex justify-between"><dt class="text-ink-500">Service charge</dt><dd>{{ money(order.serviceCharge) }}</dd></div>
          <div class="flex justify-between text-base font-extrabold"><dt>Total</dt><dd>{{ money(order.totalAmount) }}</dd></div>
        </dl>
      </section>

      <!-- Timeline -->
      <section v-if="order.history?.length" class="card-pad">
        <h2 class="font-extrabold">Order timeline</h2>
        <ol class="mt-4 space-y-3">
          <li v-for="entry in order.history" :key="entry.changedAt + entry.toStatus" class="flex items-center gap-3 text-sm">
            <span class="h-2 w-2 shrink-0 rounded-full bg-brand-500" />
            <span class="font-semibold">{{ STATUS[entry.toStatus]?.customer || entry.toStatus }}</span>
            <span v-if="entry.note && entry.toStatus === 'CANCELLED'" class="text-ink-500">— {{ entry.note }}</span>
            <span class="ml-auto text-xs text-ink-400">{{ dateTime(entry.changedAt) }}</span>
          </li>
        </ol>
      </section>

      <div class="flex flex-col gap-2 sm:flex-row">
        <button class="btn-dark flex-1" @click="reorder(order)"><RotateCcw class="h-4 w-4" /> Order these again</button>
        <RouterLink to="/menu" class="btn-light flex-1">Add more food</RouterLink>
      </div>
    </div>
  </div>
</template>
