<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleAlert, Leaf, MapPin, RefreshCw, ScanLine, Search, SearchX, ShoppingBag, SlidersHorizontal, X } from 'lucide-vue-next'
import { getCategories, getMenu } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'
import { money } from '../../utils/formatters'
import CategoryIcon from '../../components/CategoryIcon.vue'
import EmptyState from '../../components/EmptyState.vue'
import FoodCard from '../../components/FoodCard.vue'
import FoodCardSkeleton from '../../components/FoodCardSkeleton.vue'

const route = useRoute()
const router = useRouter()
const cart = useOrderStore()

const menu = ref([])
const categories = ref([])
const loading = ref(true)
const error = ref('')
const showFilters = ref(false)

const search = ref(String(route.query.q || ''))
const category = ref(String(route.query.category || 'All'))
const sort = ref('featured')
const maxPrice = ref(null)
const vegOnly = ref(false)
const availableOnly = ref(false)

const priceCeiling = computed(() => Math.ceil(Math.max(1000, ...menu.value.map((item) => Number(item.price))) / 100) * 100)
const activeFilterCount = computed(() => [maxPrice.value !== null && maxPrice.value < priceCeiling.value, vegOnly.value, availableOnly.value, sort.value !== 'featured'].filter(Boolean).length)

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  let items = menu.value.filter((item) => {
    if (category.value !== 'All' && item.category !== category.value) return false
    if (term && !`${item.name} ${item.description || ''} ${item.category || ''}`.toLowerCase().includes(term)) return false
    if (maxPrice.value !== null && Number(item.price) > maxPrice.value) return false
    if (vegOnly.value && !item.vegetarian) return false
    if (availableOnly.value && !item.available) return false
    return true
  })
  const sorters = {
    'price-asc': (a, b) => a.price - b.price,
    'price-desc': (a, b) => b.price - a.price,
    popular: (a, b) => b.orderCount - a.orderCount || Number(b.popular) - Number(a.popular),
    name: (a, b) => a.name.localeCompare(b.name),
  }
  if (sorters[sort.value]) items = [...items].sort(sorters[sort.value])
  return items
})

// Group into category sections when browsing everything in the default order.
const grouped = computed(() => {
  if (category.value !== 'All' || sort.value !== 'featured' || search.value.trim()) return null
  const groups = new Map()
  filtered.value.forEach((item) => {
    const key = item.category || 'More to try'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  })
  return [...groups.entries()]
})

function resetFilters() {
  search.value = ''
  category.value = 'All'
  sort.value = 'featured'
  maxPrice.value = null
  vegOnly.value = false
  availableOnly.value = false
}

watch([search, category], () => {
  router.replace({ query: { ...(search.value.trim() ? { q: search.value.trim() } : {}), ...(category.value !== 'All' ? { category: category.value } : {}) } })
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[menu.value, categories.value] = await Promise.all([getMenu(), getCategories()])
    cart.syncWithMenu(menu.value)
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
    <!-- Table context -->
    <div v-if="cart.state.table" class="mb-6 flex items-center gap-3 rounded-2xl bg-ink-900 p-3 pr-4 text-white shadow-card sm:p-4">
      <span class="grid h-11 w-11 place-items-center rounded-xl bg-brand-500"><MapPin class="h-5 w-5" /></span>
      <div class="flex-1">
        <p class="text-xs text-ink-300">You're ordering for</p>
        <p class="text-lg leading-tight font-extrabold">Table {{ cart.state.table.tableNumber }}</p>
      </div>
      <RouterLink to="/scan" class="btn btn-sm bg-white/10 text-white hover:bg-white/20"><ScanLine class="h-4 w-4" /> Change</RouterLink>
    </div>
    <div v-else class="mb-6 flex flex-col gap-3 rounded-2xl border border-brand-100 bg-brand-50 p-4 sm:flex-row sm:items-center">
      <p class="flex-1 text-sm text-ink-700"><strong class="text-ink-900">Sitting at a table?</strong> Scan its QR code so your order goes to the right table. You can also order takeaway.</p>
      <RouterLink to="/scan" class="btn-primary btn-sm self-start sm:self-auto"><ScanLine class="h-4 w-4" /> Scan table QR</RouterLink>
    </div>

    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p class="eyebrow">Our menu</p>
        <h1 class="mt-1 font-display text-4xl font-normal sm:text-5xl">Made fresh, made for you</h1>
      </div>
    </div>

    <!-- Search + filters -->
    <div class="sticky top-16 z-20 -mx-4 mt-6 border-b border-ink-100/60 bg-cream/90 px-4 py-3 backdrop-blur-lg sm:mx-0 sm:rounded-2xl sm:border sm:px-3">
      <div class="flex gap-2">
        <label class="flex flex-1 items-center gap-2 rounded-xl border border-ink-200 bg-white px-3 focus-within:border-brand-400 focus-within:ring-4 focus-within:ring-brand-100">
          <Search class="h-4.5 w-4.5 text-ink-400" />
          <input v-model="search" type="search" class="min-w-0 flex-1 bg-transparent py-2.5 text-sm outline-none" placeholder="Search dishes and drinks" aria-label="Search menu">
          <button v-if="search" class="text-ink-400 hover:text-ink-700" aria-label="Clear search" @click="search = ''"><X class="h-4 w-4" /></button>
        </label>
        <button class="btn-light relative" :class="{ 'border-brand-300 text-brand-700': showFilters }" @click="showFilters = !showFilters">
          <SlidersHorizontal class="h-4 w-4" /><span class="hidden sm:inline">Filters</span>
          <span v-if="activeFilterCount" class="grid h-5 w-5 place-items-center rounded-full bg-brand-500 text-[11px] text-white">{{ activeFilterCount }}</span>
        </button>
      </div>

      <Transition name="fade">
        <div v-if="showFilters" class="mt-3 grid gap-4 rounded-2xl bg-white p-4 shadow-card sm:grid-cols-3">
          <label>
            <span class="field-label">Sort by</span>
            <select v-model="sort" class="input">
              <option value="featured">Featured</option>
              <option value="popular">Most popular</option>
              <option value="price-asc">Price: low to high</option>
              <option value="price-desc">Price: high to low</option>
              <option value="name">Name A–Z</option>
            </select>
          </label>
          <label>
            <span class="field-label">Max price · {{ money(maxPrice ?? priceCeiling) }}</span>
            <input :value="maxPrice ?? priceCeiling" type="range" min="0" :max="priceCeiling" step="50" class="mt-3 w-full accent-brand-500" @input="maxPrice = Number($event.target.value)">
          </label>
          <div class="flex flex-col justify-end gap-2">
            <label class="flex items-center gap-2 text-sm font-semibold"><input v-model="vegOnly" type="checkbox" class="h-4 w-4 accent-brand-500"> <Leaf class="h-4 w-4 text-leaf-500" /> Vegetarian only</label>
            <label class="flex items-center gap-2 text-sm font-semibold"><input v-model="availableOnly" type="checkbox" class="h-4 w-4 accent-brand-500"> Hide sold-out dishes</label>
            <button class="self-start text-xs font-bold text-brand-600 hover:underline" @click="resetFilters">Reset all</button>
          </div>
        </div>
      </Transition>

      <div class="no-scrollbar -mx-4 mt-3 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:px-0">
        <button class="chip" :class="{ 'chip-active': category === 'All' }" @click="category = 'All'">
          <CategoryIcon icon="all" class="h-4 w-4" /> All
        </button>
        <button v-for="cat in categories" :key="cat.id" class="chip" :class="{ 'chip-active': category === cat.name }" @click="category = cat.name">
          <CategoryIcon :icon="cat.icon" :name="cat.name" class="h-4 w-4" /> {{ cat.name }}
        </button>
      </div>
    </div>

    <!-- Results -->
    <div class="mt-6">
      <EmptyState v-if="error" :icon="CircleAlert" tone="error" title="We couldn't load the menu" :message="error">
        <button class="btn-primary" @click="load"><RefreshCw class="h-4 w-4" /> Try again</button>
      </EmptyState>

      <div v-else-if="loading" class="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
        <FoodCardSkeleton v-for="n in 8" :key="n" />
      </div>

      <EmptyState v-else-if="!filtered.length" :icon="SearchX" title="No dishes match" message="Try a different search, category or price range.">
        <button class="btn-light" @click="resetFilters">Clear filters</button>
      </EmptyState>

      <template v-else-if="grouped">
        <section v-for="[name, items] in grouped" :key="name" class="mb-12">
          <h2 class="mb-4 flex items-center gap-3 text-xl font-extrabold sm:text-2xl">
            <span class="grid h-10 w-10 place-items-center rounded-xl bg-brand-50 text-brand-600"><CategoryIcon :name="name" :icon="categories.find((c) => c.name === name)?.icon" class="h-5 w-5" /></span>
            {{ name }}
            <span class="text-sm font-semibold text-ink-400">{{ items.length }}</span>
          </h2>
          <div class="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
            <FoodCard v-for="item in items" :key="item.id" :item="item" />
          </div>
        </section>
      </template>

      <template v-else>
        <p class="mb-4 text-sm text-ink-500">{{ filtered.length }} {{ filtered.length === 1 ? 'dish' : 'dishes' }}</p>
        <div class="grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
          <FoodCard v-for="item in filtered" :key="item.id" :item="item" />
        </div>
      </template>
    </div>

    <!-- Floating cart summary -->
    <Transition name="slide-up">
      <RouterLink v-if="cart.count.value" to="/cart" class="fixed inset-x-3 bottom-20 z-30 mx-auto flex max-w-md items-center gap-3 rounded-2xl bg-brand-500 p-3 pr-4 text-white shadow-brand md:bottom-6">
        <span class="relative grid h-11 w-11 place-items-center rounded-xl bg-white/20"><ShoppingBag class="h-5 w-5" /></span>
        <span class="flex-1"><span class="block text-xs text-brand-100">{{ cart.count.value }} item{{ cart.count.value > 1 ? 's' : '' }}</span><span class="block font-extrabold">View cart</span></span>
        <strong class="text-lg">{{ money(cart.subtotal.value) }}</strong>
      </RouterLink>
    </Transition>
  </div>
</template>
