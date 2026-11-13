<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  ArrowLeft, BellRing, ChefHat, Clock, Flame, LayoutDashboard, LoaderCircle, LogOut, Maximize,
  MessageSquareText, RefreshCw, ShoppingBag, Volume2, VolumeX,
} from 'lucide-vue-next'
import { getKitchenOrders } from '../../services/orderService'
import { setKitchenCode } from '../../services/api'
import { useAuth } from '../../stores/authStore'
import { useStaffEvents } from '../../composables/useStaffEvents'
import { useOrderActions } from '../../composables/useOrderActions'
import { playChime } from '../../composables/useNotifications'
import { minutesSince, time } from '../../utils/formatters'
import { nextActions } from '../../utils/status'

const emit = defineEmits(['locked'])
const auth = useAuth()
const orders = ref([])
const loading = ref(true)
const error = ref('')
const sound = ref(true)
const now = ref(Date.now())
const flash = ref(new Set())
let clock = null
let poll = null

const COLUMNS = [
  { status: 'PENDING', title: 'New', tint: 'bg-amber-400', ring: 'ring-amber-400/40' },
  { status: 'CONFIRMED', title: 'Accepted', tint: 'bg-sky-400', ring: 'ring-sky-400/40' },
  { status: 'PREPARING', title: 'Cooking', tint: 'bg-violet-400', ring: 'ring-violet-400/40' },
  { status: 'READY', title: 'Ready to serve', tint: 'bg-emerald-400', ring: 'ring-emerald-400/40' },
]
const byStatus = computed(() => Object.fromEntries(COLUMNS.map((column) => [column.status, orders.value.filter((o) => o.status === column.status)])))
const urgentCount = computed(() => orders.value.filter((o) => urgency(o) === 'late').length)

function elapsed(order) {
  const minutes = minutesSince(order.createdAt, now.value)
  return minutes < 60 ? `${minutes}m` : `${Math.floor(minutes / 60)}h ${minutes % 60}m`
}

function urgency(order) {
  if (order.status === 'READY') return 'ok'
  const minutes = minutesSince(order.createdAt, now.value)
  if (minutes >= 25) return 'late'
  if (minutes >= 15) return 'warn'
  return 'ok'
}

async function load() {
  try {
    orders.value = await getKitchenOrders()
    error.value = ''
  } catch (exception) {
    if (exception.status === 401 || exception.status === 403) {
      setKitchenCode('')
      emit('locked', exception.message)
      return
    }
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

const { busy, setStatus } = useOrderActions(() => load())

const { connection } = useStaffEvents((event) => {
  if (event.type === 'ORDER_CREATED') {
    if (sound.value) playChime()
    flash.value = new Set([...flash.value, event.orderId])
    setTimeout(() => { flash.value.delete(event.orderId); flash.value = new Set(flash.value) }, 6000)
  }
  load()
})

function fullscreen() {
  if (document.fullscreenElement) document.exitFullscreen()
  else document.documentElement.requestFullscreen?.()
}

// Signed-in admins/kitchen accounts use their role; the kitchen code acts as KITCHEN.
const role = computed(() => auth.role.value || 'KITCHEN')

async function signOut() {
  setKitchenCode('')
  if (auth.user.value) await auth.logout()
  emit('locked')
}

onMounted(() => {
  load()
  clock = setInterval(() => { now.value = Date.now() }, 15000)
  poll = setInterval(load, 30000) // safety refresh even if the socket drops
})
onUnmounted(() => { clearInterval(clock); clearInterval(poll) })
</script>

<template>
  <div class="min-h-screen bg-[#120d0a] text-white">
    <!-- Top bar -->
    <header class="sticky top-0 z-20 flex flex-wrap items-center gap-3 border-b border-white/10 bg-[#120d0a]/95 px-4 py-3 backdrop-blur sm:px-6">
      <RouterLink v-if="auth.role.value === 'ADMIN'" to="/admin" class="btn btn-sm text-ink-300 hover:bg-white/10"><ArrowLeft class="h-4 w-4" /></RouterLink>
      <span class="grid h-10 w-10 place-items-center rounded-xl bg-brand-500"><ChefHat class="h-5.5 w-5.5" /></span>
      <div>
        <h1 class="text-lg leading-tight font-extrabold text-white">Kitchen display</h1>
        <p class="flex items-center gap-1.5 text-xs text-ink-400">
          <span class="h-2 w-2 rounded-full" :class="connection === 'live' ? 'bg-emerald-400' : 'bg-amber-400'" />
          {{ connection === 'live' ? 'Live' : 'Reconnecting…' }} · {{ new Date(now).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }) }}
        </p>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <span v-if="urgentCount" class="inline-flex items-center gap-1.5 rounded-full bg-red-500/20 px-3 py-1.5 text-sm font-bold text-red-300"><Flame class="h-4 w-4" /> {{ urgentCount }} running late</span>
        <span class="hidden rounded-full bg-white/10 px-3 py-1.5 text-sm font-bold sm:inline">{{ orders.length }} active</span>
        <button class="btn-icon text-ink-300 hover:bg-white/10" :aria-label="sound ? 'Mute new-order sound' : 'Unmute'" @click="sound = !sound">
          <Volume2 v-if="sound" class="h-5 w-5" /><VolumeX v-else class="h-5 w-5" />
        </button>
        <button class="btn-icon text-ink-300 hover:bg-white/10" aria-label="Refresh" @click="load"><RefreshCw class="h-5 w-5" /></button>
        <button class="btn-icon hidden text-ink-300 hover:bg-white/10 sm:inline-flex" aria-label="Full screen" @click="fullscreen"><Maximize class="h-5 w-5" /></button>
        <RouterLink v-if="auth.role.value === 'ADMIN'" to="/admin/orders" class="btn-icon text-ink-300 hover:bg-white/10" aria-label="Console"><LayoutDashboard class="h-5 w-5" /></RouterLink>
        <button class="btn-icon text-ink-300 hover:bg-white/10" aria-label="Lock kitchen screen" title="Lock screen" @click="signOut"><LogOut class="h-5 w-5" /></button>
      </div>
    </header>

    <p v-if="error" class="mx-4 mt-4 rounded-xl bg-red-500/15 p-3 text-sm font-semibold text-red-300 sm:mx-6">{{ error }}</p>

    <div v-if="loading" class="grid min-h-[60vh] place-items-center"><LoaderCircle class="h-10 w-10 animate-spin text-brand-400" /></div>

    <div v-else class="grid gap-4 p-4 sm:p-6 md:grid-cols-2 xl:grid-cols-4">
      <section v-for="column in COLUMNS" :key="column.status" class="flex min-h-[40vh] flex-col rounded-3xl bg-white/[0.04] p-3 ring-1 ring-white/5">
        <h2 class="mb-3 flex items-center gap-2 px-2 pt-1 text-sm font-extrabold tracking-wider text-ink-200 uppercase">
          <span class="h-2.5 w-2.5 rounded-full" :class="column.tint" /> {{ column.title }}
          <span class="ml-auto rounded-full bg-white/10 px-2 py-0.5 text-xs">{{ byStatus[column.status].length }}</span>
        </h2>

        <TransitionGroup name="list" tag="div" class="relative flex flex-1 flex-col gap-3">
          <article
            v-for="order in byStatus[column.status]"
            :key="order.id"
            class="rounded-2xl bg-[#1f1712] p-4 ring-2 transition"
            :class="[
              urgency(order) === 'late' ? 'ring-red-500/80' : urgency(order) === 'warn' ? 'ring-amber-400/60' : column.ring,
              flash.has(order.id) ? 'animate-pulse-ring' : '',
            ]"
          >
            <header class="flex items-start justify-between gap-2">
              <div>
                <p class="text-2xl leading-none font-extrabold text-white">
                  <template v-if="order.orderType === 'DINE_IN'">T{{ order.tableNumber?.replace(/^T/i, '') }}</template>
                  <span v-else class="inline-flex items-center gap-1.5"><ShoppingBag class="h-5 w-5 text-brand-300" /> Takeaway</span>
                </p>
                <p class="mt-1 text-xs font-semibold text-ink-400">{{ order.orderNumber }}<span v-if="order.customerName"> · {{ order.customerName }}</span></p>
              </div>
              <span
                class="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-sm font-extrabold tabular-nums"
                :class="urgency(order) === 'late' ? 'bg-red-500 text-white' : urgency(order) === 'warn' ? 'bg-amber-400 text-ink-900' : 'bg-white/10 text-ink-200'"
              >
                <Clock class="h-3.5 w-3.5" /> {{ elapsed(order) }}
              </span>
            </header>

            <ul class="mt-3 space-y-1.5 border-t border-white/10 pt-3">
              <li v-for="item in order.items" :key="`${item.menuItemId}-${item.notes}`">
                <p class="text-base font-bold text-white"><span class="mr-1.5 inline-block min-w-7 rounded-md bg-brand-500 px-1.5 text-center">{{ item.quantity }}</span>{{ item.name }}</p>
                <p v-if="item.notes" class="ml-9 text-sm font-semibold text-amber-300">↳ {{ item.notes }}</p>
              </li>
            </ul>
            <p v-if="order.specialInstructions" class="mt-3 flex gap-2 rounded-xl bg-amber-400/15 p-2.5 text-sm font-semibold text-amber-200">
              <MessageSquareText class="mt-0.5 h-4 w-4 shrink-0" /> {{ order.specialInstructions }}
            </p>
            <p class="mt-3 text-xs text-ink-500">Received {{ time(order.createdAt) }}</p>

            <div class="mt-3 flex gap-2">
              <button
                v-for="action in nextActions(order, role)"
                :key="action.status"
                class="btn flex-1 py-3 text-sm"
                :class="action.status === 'CANCELLED' ? 'bg-white/10 text-ink-200 hover:bg-white/20' : action.style"
                :disabled="busy[order.id]"
                @click="setStatus(order, action.status)"
              >
                <LoaderCircle v-if="busy[order.id]" class="h-4 w-4 animate-spin" />
                <BellRing v-else-if="action.status === 'READY'" class="h-4 w-4" />
                {{ action.label }}
              </button>
            </div>
          </article>
        </TransitionGroup>

        <p v-if="!byStatus[column.status].length" class="grid flex-1 place-items-center py-10 text-center text-sm text-ink-500">Nothing here</p>
      </section>
    </div>
  </div>
</template>
