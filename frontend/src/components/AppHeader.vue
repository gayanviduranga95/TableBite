<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MapPin, Receipt, ScanLine, ShoppingBag } from 'lucide-vue-next'
import { useOrderStore } from '../stores/orderStore'
import { useSettings } from '../stores/settingsStore'
import BrandMark from './BrandMark.vue'

const route = useRoute()
const cart = useOrderStore()
const settings = useSettings()
const transparent = computed(() => route.path === '/')

const links = [
  { to: '/', label: 'Home' },
  { to: '/menu', label: 'Menu' },
  { to: '/orders', label: 'My orders' },
]
</script>

<template>
  <header
    class="sticky top-0 z-40 border-b transition-colors"
    :class="transparent ? 'border-transparent bg-ink-900 text-white' : 'border-ink-100 bg-white/85 text-ink-900 backdrop-blur-lg'"
  >
    <div class="container-app flex h-16 items-center gap-3">
      <RouterLink to="/" class="flex items-center gap-2.5" aria-label="Home">
        <BrandMark />
        <span class="text-lg font-extrabold tracking-tight">{{ settings.state.restaurant_name }}</span>
      </RouterLink>

      <nav class="ml-6 hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="rounded-xl px-3.5 py-2 text-sm font-semibold transition"
          :class="route.path === link.to
            ? (transparent ? 'bg-white/10 text-white' : 'bg-ink-100 text-ink-900')
            : (transparent ? 'text-white/70 hover:text-white' : 'text-ink-500 hover:text-ink-900')"
        >{{ link.label }}</RouterLink>
      </nav>

      <div class="ml-auto flex items-center gap-2">
        <RouterLink
          v-if="cart.state.table"
          to="/menu"
          class="hidden items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-bold sm:inline-flex"
          :class="transparent ? 'bg-white/10 text-white' : 'bg-brand-50 text-brand-700'"
        >
          <MapPin class="h-3.5 w-3.5" /> Table {{ cart.state.table.tableNumber }}
        </RouterLink>
        <RouterLink to="/scan" class="btn btn-sm hidden sm:inline-flex" :class="transparent ? 'bg-white/10 text-white hover:bg-white/20' : 'btn-light'">
          <ScanLine class="h-4 w-4" /> Scan QR
        </RouterLink>
        <RouterLink to="/orders" class="btn-icon md:hidden" :class="transparent ? 'text-white hover:bg-white/10' : 'btn-ghost'" aria-label="My orders">
          <Receipt class="h-5 w-5" />
        </RouterLink>
        <RouterLink
          to="/cart"
          class="relative btn-icon"
          :class="transparent ? 'bg-white/10 text-white hover:bg-white/20' : 'bg-ink-900 text-white hover:bg-ink-800'"
          :aria-label="`Cart, ${cart.count.value} items`"
        >
          <ShoppingBag class="h-5 w-5" />
          <span v-if="cart.count.value" :key="cart.count.value" class="absolute -top-1.5 -right-1.5 grid h-5 min-w-5 animate-pop place-items-center rounded-full bg-brand-500 px-1 text-[11px] font-extrabold text-white ring-2" :class="transparent ? 'ring-ink-900' : 'ring-white'">{{ cart.count.value }}</span>
        </RouterLink>
      </div>
    </div>
  </header>
</template>
