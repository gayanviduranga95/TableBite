<script setup>
import { Clock, MapPin, Phone } from 'lucide-vue-next'
import { useSettings } from '../stores/settingsStore'
import BrandMark from './BrandMark.vue'

const settings = useSettings()
const year = new Date().getFullYear()
</script>

<template>
  <footer class="no-print mt-20 bg-ink-900 pb-24 text-ink-300 md:pb-0">
    <div class="container-app grid gap-10 py-14 md:grid-cols-4">
      <div class="md:col-span-2">
        <div class="flex items-center gap-2.5">
          <BrandMark />
          <span class="text-xl font-extrabold text-white">{{ settings.state.restaurant_name }}</span>
        </div>
        <p class="mt-4 max-w-sm text-sm leading-relaxed">{{ settings.state.tagline }}. Scan the QR code on your table, order in seconds and watch your food come to life — live.</p>
      </div>
      <div>
        <h3 class="text-sm font-bold text-white">Visit us</h3>
        <ul class="mt-4 space-y-3 text-sm">
          <li v-if="settings.state.address" class="flex gap-2"><MapPin class="mt-0.5 h-4 w-4 shrink-0 text-brand-400" />{{ settings.state.address }}</li>
          <li v-if="settings.state.phone" class="flex gap-2"><Phone class="mt-0.5 h-4 w-4 shrink-0 text-brand-400" />{{ settings.state.phone }}</li>
          <li v-if="settings.state.opening_hours" class="flex gap-2"><Clock class="mt-0.5 h-4 w-4 shrink-0 text-brand-400" />{{ settings.state.opening_hours }}</li>
        </ul>
      </div>
      <div>
        <h3 class="text-sm font-bold text-white">Explore</h3>
        <ul class="mt-4 space-y-2.5 text-sm">
          <li><RouterLink to="/menu" class="hover:text-white">Full menu</RouterLink></li>
          <li><RouterLink to="/scan" class="hover:text-white">Scan table QR</RouterLink></li>
          <li><RouterLink to="/orders" class="hover:text-white">Track my order</RouterLink></li>
          <li><RouterLink to="/staff/login" class="hover:text-white">Staff portal</RouterLink></li>
        </ul>
      </div>
    </div>
    <div class="border-t border-white/10">
      <div class="container-app flex flex-col gap-2 py-5 text-xs text-ink-400 sm:flex-row sm:justify-between">
        <p>© {{ year }} {{ settings.state.restaurant_name }}. All rights reserved.</p>
        <p>Food photography from Unsplash.</p>
      </div>
    </div>
  </footer>
</template>
