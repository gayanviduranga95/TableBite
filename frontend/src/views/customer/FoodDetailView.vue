<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleAlert, Clock, Flame, Leaf, Minus, Plus, ShoppingBag, Star } from 'lucide-vue-next'
import { getMenu } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'
import { useToast } from '../../composables/useToast'
import { money } from '../../utils/formatters'
import { dishImage, onImageError } from '../../utils/images'
import EmptyState from '../../components/EmptyState.vue'
import FoodCard from '../../components/FoodCard.vue'

const route = useRoute()
const router = useRouter()
const cart = useOrderStore()
const toast = useToast()

const menu = ref([])
const loading = ref(true)
const error = ref('')
const quantity = ref(1)
const notes = ref('')

const item = computed(() => menu.value.find((dish) => dish.id === Number(route.params.id)))
const related = computed(() => item.value
  ? menu.value.filter((dish) => dish.category === item.value.category && dish.id !== item.value.id && dish.available).slice(0, 4)
  : [])
const inCart = computed(() => (item.value ? cart.quantityOf(item.value.id) : 0))

async function load() {
  loading.value = true
  error.value = ''
  try {
    menu.value = await getMenu()
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

function addToCart() {
  if (!item.value?.available) return
  cart.add(item.value, quantity.value, notes.value.trim() || undefined)
  toast.success(`${quantity.value} × ${item.value.name} added to your cart`)
  router.push('/menu')
}

watch(() => route.params.id, () => { quantity.value = 1; notes.value = '' })
onMounted(load)
</script>

<template>
  <div class="container-app py-6 sm:py-10">
    <button class="btn-ghost -ml-3 mb-4" @click="router.back()"><ArrowLeft class="h-4 w-4" /> Back</button>

    <div v-if="loading" class="grid gap-8 lg:grid-cols-2">
      <div class="skeleton aspect-[4/3] rounded-[2rem]" />
      <div class="space-y-4 pt-4"><div class="skeleton h-4 w-24" /><div class="skeleton h-10 w-3/4" /><div class="skeleton h-20 w-full" /><div class="skeleton h-12 w-1/2" /></div>
    </div>

    <EmptyState v-else-if="error || !item" :icon="CircleAlert" tone="error" :title="error ? 'Could not load this dish' : 'Dish not found'" :message="error || 'It may have been removed from the menu.'">
      <RouterLink to="/menu" class="btn-primary">Back to menu</RouterLink>
    </EmptyState>

    <template v-else>
      <div class="grid gap-8 lg:grid-cols-2 lg:gap-12">
        <div class="relative overflow-hidden rounded-[2rem] bg-ink-50 shadow-lift">
          <img :src="dishImage(item)" :alt="item.name" class="aspect-[4/3] h-full w-full object-cover" :class="{ grayscale: !item.available }" @error="onImageError">
          <div class="absolute top-4 left-4 flex gap-2">
            <span v-if="item.popular" class="inline-flex items-center gap-1 rounded-full bg-white px-3 py-1.5 text-xs font-extrabold text-brand-700 shadow"><Star class="h-3.5 w-3.5 fill-current" /> Popular</span>
            <span v-if="item.isNew" class="rounded-full bg-ink-900 px-3 py-1.5 text-xs font-extrabold text-white">New</span>
          </div>
        </div>

        <div class="flex flex-col">
          <p class="eyebrow">{{ item.category }}</p>
          <h1 class="mt-2 font-display text-4xl leading-tight font-normal sm:text-5xl">{{ item.name }}</h1>
          <div class="mt-4 flex flex-wrap gap-2">
            <span class="inline-flex items-center gap-1.5 rounded-full bg-ink-100 px-3 py-1 text-xs font-bold text-ink-700"><Clock class="h-3.5 w-3.5" /> {{ item.prepMinutes }} min</span>
            <span v-if="item.vegetarian" class="inline-flex items-center gap-1.5 rounded-full bg-leaf-50 px-3 py-1 text-xs font-bold text-leaf-500"><Leaf class="h-3.5 w-3.5" /> Vegetarian</span>
            <span v-if="item.spicy" class="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1 text-xs font-bold text-red-600"><Flame class="h-3.5 w-3.5" /> Spicy</span>
            <span :class="item.available ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'" class="rounded-full px-3 py-1 text-xs font-bold">{{ item.available ? 'Available now' : 'Sold out today' }}</span>
          </div>
          <p class="mt-5 text-base leading-relaxed text-ink-600">{{ item.description || 'Freshly prepared by our kitchen.' }}</p>
          <p class="mt-6 text-3xl font-extrabold">{{ money(item.price) }}</p>

          <label class="mt-6 block">
            <span class="field-label">Special request (optional)</span>
            <textarea v-model="notes" class="input min-h-20 resize-none" maxlength="255" placeholder="e.g. no onions, extra spicy, sauce on the side"></textarea>
          </label>

          <div class="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center">
            <div class="inline-flex items-center self-start rounded-2xl border border-ink-200 bg-white p-1.5">
              <button class="btn-icon btn-ghost" aria-label="Decrease" :disabled="quantity <= 1" @click="quantity--"><Minus class="h-4 w-4" /></button>
              <span class="w-10 text-center text-lg font-extrabold tabular-nums">{{ quantity }}</span>
              <button class="btn-icon btn-ghost" aria-label="Increase" :disabled="quantity >= 50" @click="quantity++"><Plus class="h-4 w-4" /></button>
            </div>
            <button class="btn-primary btn-lg flex-1" :disabled="!item.available" @click="addToCart">
              <ShoppingBag class="h-5 w-5" />
              {{ item.available ? `Add to cart · ${money(item.price * quantity)}` : 'Currently unavailable' }}
            </button>
          </div>
          <p v-if="inCart" class="mt-3 text-sm text-ink-500">You already have {{ inCart }} in your cart. <RouterLink to="/cart" class="font-bold text-brand-600">View cart</RouterLink></p>
        </div>
      </div>

      <section v-if="related.length" class="mt-16">
        <h2 class="text-2xl font-extrabold">More {{ item.category?.toLowerCase() }} you'll love</h2>
        <div class="mt-5 grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
          <FoodCard v-for="dish in related" :key="dish.id" :item="dish" />
        </div>
      </section>
    </template>
  </div>
</template>
