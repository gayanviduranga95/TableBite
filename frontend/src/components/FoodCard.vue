<script setup>
import { Clock, Flame, Leaf, Plus, Star } from 'lucide-vue-next'
import { useOrderStore } from '../stores/orderStore'
import { useToast } from '../composables/useToast'
import { money } from '../utils/formatters'
import { dishImage, onImageError } from '../utils/images'
import QuantityStepper from './QuantityStepper.vue'

const props = defineProps({
  item: { type: Object, required: true },
  layout: { type: String, default: 'grid' }, // grid | row
})

const cart = useOrderStore()
const toast = useToast()

function add() {
  if (cart.add(props.item)) toast.success(`${props.item.name} added to your cart`, { timeout: 1800 })
}
</script>

<template>
  <!-- Grid card -->
  <article
    v-if="layout === 'grid'"
    class="group relative flex flex-col overflow-hidden rounded-3xl border border-ink-100/70 bg-white shadow-card transition duration-300 hover:-translate-y-1 hover:shadow-lift"
    :class="{ 'opacity-75': !item.available }"
  >
    <RouterLink :to="`/menu/${item.id}`" class="relative block aspect-[4/3] overflow-hidden bg-ink-50" :aria-label="`View ${item.name}`">
      <img
        :src="dishImage(item)"
        :alt="item.name"
        loading="lazy"
        class="h-full w-full object-cover transition duration-500 group-hover:scale-105"
        :class="{ grayscale: !item.available }"
        @error="onImageError"
      >
      <div class="absolute inset-x-0 top-0 flex flex-wrap gap-1.5 p-3">
        <span v-if="item.popular" class="inline-flex items-center gap-1 rounded-full bg-white/95 px-2.5 py-1 text-[11px] font-extrabold text-brand-700 shadow-sm"><Star class="h-3 w-3 fill-current" /> Popular</span>
        <span v-if="item.isNew" class="rounded-full bg-ink-900/85 px-2.5 py-1 text-[11px] font-extrabold text-white">New</span>
      </div>
      <div v-if="!item.available" class="absolute inset-0 grid place-items-center bg-ink-900/45">
        <span class="rounded-full bg-white px-3 py-1.5 text-xs font-extrabold tracking-wide text-ink-900 uppercase">Sold out</span>
      </div>
    </RouterLink>
    <div class="flex flex-1 flex-col p-4">
      <div class="mb-1 flex items-center gap-2 text-[11px] font-bold text-ink-400">
        <span>{{ item.category || 'Chef special' }}</span>
        <span class="inline-flex items-center gap-1"><Clock class="h-3 w-3" />{{ item.prepMinutes }} min</span>
        <Leaf v-if="item.vegetarian" class="h-3.5 w-3.5 text-leaf-500" aria-label="Vegetarian" />
        <Flame v-if="item.spicy" class="h-3.5 w-3.5 text-red-500" aria-label="Spicy" />
      </div>
      <RouterLink :to="`/menu/${item.id}`" class="font-bold leading-snug text-ink-900 hover:text-brand-700">{{ item.name }}</RouterLink>
      <p class="mt-1 line-clamp-2 text-xs leading-relaxed text-ink-500">{{ item.description || 'Freshly prepared by our kitchen.' }}</p>
      <div class="mt-auto flex flex-wrap items-center justify-between gap-2 pt-4">
        <strong class="text-[15px] font-extrabold whitespace-nowrap text-ink-900 sm:text-base">{{ money(item.price) }}</strong>
        <QuantityStepper
          v-if="cart.quantityOf(item.id)"
          :quantity="cart.quantityOf(item.id)"
          @increase="cart.increase(item.id)"
          @decrease="cart.decrease(item.id)"
        />
        <button
          v-else
          class="grid h-9 w-9 place-items-center rounded-full bg-brand-500 text-white shadow-brand transition hover:bg-brand-600 active:scale-90 disabled:bg-ink-200 disabled:shadow-none"
          :disabled="!item.available"
          :aria-label="`Add ${item.name} to cart`"
          @click="add"
        >
          <Plus class="h-5 w-5" />
        </button>
      </div>
    </div>
  </article>

  <!-- Compact row (search results, mobile lists) -->
  <article v-else class="flex gap-3 rounded-2xl border border-ink-100/70 bg-white p-2.5 shadow-sm" :class="{ 'opacity-70': !item.available }">
    <RouterLink :to="`/menu/${item.id}`" class="relative h-24 w-24 shrink-0 overflow-hidden rounded-xl bg-ink-50">
      <img :src="dishImage(item)" :alt="item.name" loading="lazy" class="h-full w-full object-cover" :class="{ grayscale: !item.available }" @error="onImageError">
    </RouterLink>
    <div class="flex min-w-0 flex-1 flex-col py-0.5">
      <RouterLink :to="`/menu/${item.id}`" class="truncate font-bold text-ink-900">{{ item.name }}</RouterLink>
      <p class="line-clamp-2 text-xs text-ink-500">{{ item.description }}</p>
      <div class="mt-auto flex items-center justify-between">
        <strong class="text-sm font-extrabold">{{ money(item.price) }}</strong>
        <span v-if="!item.available" class="text-xs font-bold text-red-600">Sold out</span>
        <QuantityStepper v-else-if="cart.quantityOf(item.id)" :quantity="cart.quantityOf(item.id)" @increase="cart.increase(item.id)" @decrease="cart.decrease(item.id)" />
        <button v-else class="btn-primary btn-sm rounded-full" @click="add"><Plus class="h-3.5 w-3.5" /> Add</button>
      </div>
    </div>
  </article>
</template>
