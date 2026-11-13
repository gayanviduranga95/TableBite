<script setup>
import { useRoute } from 'vue-router'
import { House, Receipt, ScanLine, ShoppingBag, UtensilsCrossed } from 'lucide-vue-next'
import { useOrderStore } from '../stores/orderStore'

const route = useRoute()
const cart = useOrderStore()
const items = [
  { to: '/', label: 'Home', icon: House },
  { to: '/menu', label: 'Menu', icon: UtensilsCrossed },
  { to: '/scan', label: 'Scan', icon: ScanLine, primary: true },
  { to: '/cart', label: 'Cart', icon: ShoppingBag, badge: true },
  { to: '/orders', label: 'Orders', icon: Receipt },
]
const active = (to) => (to === '/' ? route.path === '/' : route.path.startsWith(to))
</script>

<template>
  <nav class="no-print fixed inset-x-0 bottom-0 z-40 border-t border-ink-100 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-lg md:hidden" aria-label="Main">
    <ul class="grid grid-cols-5">
      <li v-for="item in items" :key="item.to">
        <RouterLink :to="item.to" class="relative flex flex-col items-center gap-0.5 py-2 text-[11px] font-bold" :class="active(item.to) ? 'text-brand-600' : 'text-ink-400'">
          <span v-if="item.primary" class="-mt-6 grid h-13 w-13 place-items-center rounded-2xl bg-brand-500 text-white shadow-brand ring-4 ring-white">
            <component :is="item.icon" class="h-6 w-6" />
          </span>
          <component :is="item.icon" v-else class="h-5.5 w-5.5" />
          <span v-if="item.badge && cart.count.value" class="absolute top-1 left-1/2 ml-1.5 grid h-4.5 min-w-4.5 place-items-center rounded-full bg-brand-500 px-1 text-[10px] text-white">{{ cart.count.value }}</span>
          {{ item.label }}
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>
