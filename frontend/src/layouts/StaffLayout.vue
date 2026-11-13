<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChefHat, ClipboardList, ExternalLink, LayoutDashboard, LogOut, Menu as MenuIcon, QrCode,
  Settings, UtensilsCrossed, Wallet, X, HandPlatter,
} from 'lucide-vue-next'
import { useAuth } from '../stores/authStore'
import { useSettings } from '../stores/settingsStore'
import { useStaffEvents } from '../composables/useStaffEvents'
import { useToast } from '../composables/useToast'
import { useConfirm } from '../composables/useConfirm'
import { playChime } from '../composables/useNotifications'
import { initials } from '../utils/formatters'
import BrandMark from '../components/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const settings = useSettings()
const toast = useToast()
const { confirm } = useConfirm()
const drawer = ref(false)

const { connection } = useStaffEvents((event) => {
  if (event.type === 'ORDER_CREATED') {
    playChime()
    toast.info(event.message, { title: 'New order' })
  }
})

const ALL = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN'] },
  { to: '/admin/orders', label: 'Orders', icon: ClipboardList, roles: ['ADMIN', 'WAITER', 'KITCHEN', 'STAFF'] },
  { to: '/kitchen', label: 'Kitchen display', icon: ChefHat, roles: ['ADMIN', 'KITCHEN', 'STAFF'], external: true },
  { to: '/floor', label: 'Floor service', icon: HandPlatter, roles: ['ADMIN', 'WAITER', 'STAFF'] },
  { to: '/admin/menu', label: 'Menu', icon: UtensilsCrossed, roles: ['ADMIN'] },
  { to: '/admin/tables', label: 'Tables & QR', icon: QrCode, roles: ['ADMIN'] },
  { to: '/admin/payments', label: 'Payments', icon: Wallet, roles: ['ADMIN', 'WAITER', 'STAFF'] },
  { to: '/admin/settings', label: 'Settings', icon: Settings, roles: ['ADMIN'] },
]
const nav = computed(() => ALL.filter((item) => item.roles.includes(auth.role.value)))
const isActive = (to) => (to === '/admin' ? route.path === '/admin' : route.path.startsWith(to))

watch(() => route.path, () => { drawer.value = false })

async function signOut() {
  if (!(await confirm({ title: 'Sign out of the staff portal?', confirmText: 'Sign out' }))) return
  await auth.logout()
  router.replace('/staff/login')
}
</script>

<template>
  <div class="min-h-screen bg-ink-50/60 lg:flex">
    <!-- Mobile top bar -->
    <header class="sticky top-0 z-40 flex h-14 items-center gap-3 border-b border-ink-100 bg-white px-4 lg:hidden">
      <button class="btn-ghost btn-icon -ml-2" aria-label="Open navigation" @click="drawer = true"><MenuIcon class="h-5 w-5" /></button>
      <BrandMark size="h-8 w-8" />
      <span class="font-extrabold">{{ route.meta.title }}</span>
      <span class="ml-auto h-2.5 w-2.5 rounded-full" :class="connection === 'live' ? 'bg-emerald-500' : 'bg-amber-400'" :title="connection === 'live' ? 'Live' : 'Reconnecting'" />
    </header>

    <div v-if="drawer" class="fixed inset-0 z-40 bg-ink-900/50 lg:hidden" @click="drawer = false" />

    <!-- Sidebar -->
    <aside
      class="fixed inset-y-0 left-0 z-50 flex w-72 flex-col bg-ink-900 text-ink-300 transition-transform lg:sticky lg:top-0 lg:h-screen lg:translate-x-0"
      :class="drawer ? 'translate-x-0' : '-translate-x-full'"
    >
      <div class="flex h-16 items-center gap-2.5 px-5">
        <BrandMark />
        <div class="leading-tight">
          <p class="font-extrabold text-white">{{ settings.state.restaurant_name }}</p>
          <p class="text-[11px] font-semibold tracking-wider text-ink-400 uppercase">Restaurant console</p>
        </div>
        <button class="btn-icon ml-auto text-ink-400 hover:bg-white/10 lg:hidden" aria-label="Close navigation" @click="drawer = false"><X class="h-5 w-5" /></button>
      </div>

      <nav class="mt-4 flex-1 space-y-1 overflow-y-auto px-3">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition"
          :class="isActive(item.to) ? 'bg-brand-500 text-white shadow-brand' : 'hover:bg-white/5 hover:text-white'"
        >
          <component :is="item.icon" class="h-5 w-5" />
          {{ item.label }}
          <ExternalLink v-if="item.external" class="ml-auto h-3.5 w-3.5 opacity-50" />
        </RouterLink>
      </nav>

      <div class="m-3 rounded-2xl bg-white/5 p-3">
        <div class="flex items-center gap-3">
          <span class="grid h-10 w-10 place-items-center rounded-xl bg-brand-500 text-sm font-extrabold text-white">{{ initials(auth.user.value?.fullName) }}</span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-bold text-white">{{ auth.user.value?.fullName }}</p>
            <p class="text-xs text-ink-400 capitalize">{{ auth.role.value?.toLowerCase() }}</p>
          </div>
          <button class="btn-icon text-ink-400 hover:bg-white/10 hover:text-white" aria-label="Sign out" @click="signOut"><LogOut class="h-4.5 w-4.5" /></button>
        </div>
        <p class="mt-3 flex items-center gap-2 text-[11px] font-semibold">
          <span class="h-2 w-2 rounded-full" :class="connection === 'live' ? 'bg-emerald-400' : 'bg-amber-400'" />
          {{ connection === 'live' ? 'Live order feed connected' : 'Connecting to live feed…' }}
        </p>
      </div>
    </aside>

    <main class="min-w-0 flex-1">
      <div class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
        <slot />
      </div>
    </main>
  </div>
</template>
