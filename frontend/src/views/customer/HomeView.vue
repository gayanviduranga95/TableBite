<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight, BellRing, ChefHat, CircleAlert, Clock, MapPin, QrCode, Receipt, RefreshCw,
  ScanLine, Search, ShieldCheck, Sparkles, Star, TrendingUp,
} from 'lucide-vue-next'
import { getCategories, getMenu } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'
import { useSettings } from '../../stores/settingsStore'
import CategoryIcon from '../../components/CategoryIcon.vue'
import EmptyState from '../../components/EmptyState.vue'
import FoodCard from '../../components/FoodCard.vue'
import FoodCardSkeleton from '../../components/FoodCardSkeleton.vue'

const router = useRouter()
const cart = useOrderStore()
const settings = useSettings()
const menu = ref([])
const categories = ref([])
const loading = ref(true)
const error = ref('')
const search = ref('')

const popular = computed(() => menu.value.filter((item) => item.popular).sort((a, b) => b.orderCount - a.orderCount).slice(0, 8))
const recommended = computed(() => menu.value.filter((item) => item.recommended && item.available).slice(0, 4))
const newArrivals = computed(() => menu.value.filter((item) => item.isNew).slice(0, 4))

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

function submitSearch() {
  router.push({ path: '/menu', query: search.value.trim() ? { q: search.value.trim() } : {} })
}

onMounted(load)
</script>

<template>
  <div>
    <!-- ================= HERO ================= -->
    <section class="relative overflow-hidden bg-ink-900 text-white">
      <img src="/images/hero/food-spread.jpg" alt="" class="absolute inset-0 h-full w-full object-cover opacity-35">
      <div class="absolute inset-0 bg-gradient-to-r from-ink-900 via-ink-900/85 to-ink-900/30" />
      <div class="absolute -top-24 -right-24 h-80 w-80 rounded-full bg-brand-500/30 blur-3xl" />
      <div class="container-app relative grid items-center gap-10 py-14 sm:py-20 lg:grid-cols-[1.15fr_0.85fr] lg:py-24">
        <div class="animate-rise">
          <p class="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-xs font-bold text-brand-200 ring-1 ring-white/15 backdrop-blur">
            <Sparkles class="h-3.5 w-3.5" /> {{ settings.state.tagline }}
          </p>
          <h1 class="mt-5 font-display text-5xl leading-[1.02] font-normal text-white sm:text-6xl lg:text-7xl">
            Great food,<br><em class="text-brand-400">straight to your table.</em>
          </h1>
          <p class="mt-5 max-w-lg text-base leading-relaxed text-ink-200 sm:text-lg">
            Scan the QR code on your table, pick your favourites and we'll start cooking — no waiting for a menu, no waving for a waiter.
          </p>

          <form class="mt-8 flex max-w-xl items-center gap-2 rounded-2xl bg-white p-2 shadow-lift" role="search" @submit.prevent="submitSearch">
            <Search class="ml-2 h-5 w-5 shrink-0 text-ink-400" />
            <input v-model="search" type="search" class="min-w-0 flex-1 bg-transparent py-2 text-sm text-ink-900 outline-none placeholder:text-ink-400 sm:text-base" placeholder="Search burgers, pizza, biryani…" aria-label="Search the menu">
            <button class="btn-primary shrink-0" type="submit">Search</button>
          </form>

          <div class="mt-5 flex flex-wrap items-center gap-3">
            <RouterLink to="/scan" class="btn btn-lg bg-white text-ink-900 shadow-lift hover:bg-brand-50">
              <ScanLine class="h-5 w-5 text-brand-600" /> Scan table QR
            </RouterLink>
            <RouterLink to="/menu" class="btn btn-lg text-white ring-1 ring-white/25 hover:bg-white/10">
              Browse menu <ArrowRight class="h-5 w-5" />
            </RouterLink>
          </div>
          <p v-if="cart.state.table" class="mt-5 inline-flex items-center gap-2 rounded-full bg-leaf-500/15 px-3 py-1.5 text-sm font-semibold text-emerald-200 ring-1 ring-emerald-400/30">
            <MapPin class="h-4 w-4" /> You're ordering for Table {{ cart.state.table.tableNumber }}
          </p>
        </div>

        <!-- Hero visual -->
        <div class="relative hidden lg:block">
          <div class="relative mx-auto aspect-square w-full max-w-md">
            <div class="absolute inset-6 rounded-full bg-gradient-to-br from-brand-400/40 to-transparent blur-2xl" />
            <img src="/images/menu/margherita-pizza.jpg" alt="Wood-fired margherita pizza" class="relative h-full w-full rounded-full object-cover shadow-2xl ring-8 ring-white/10">
            <div class="absolute -bottom-2 -left-6 flex items-center gap-3 rounded-2xl bg-white p-3 pr-5 text-ink-900 shadow-lift">
              <span class="grid h-11 w-11 place-items-center rounded-xl bg-brand-50 text-brand-600"><ChefHat class="h-6 w-6" /></span>
              <div><p class="text-xs text-ink-500">Average prep</p><p class="font-extrabold">~15 minutes</p></div>
            </div>
            <div class="absolute top-6 -right-4 flex items-center gap-2 rounded-2xl bg-white p-3 text-ink-900 shadow-lift">
              <Star class="h-5 w-5 fill-amber-400 text-amber-400" />
              <div><p class="font-extrabold leading-none">4.9</p><p class="text-[11px] text-ink-500">guest rating</p></div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ================= QUICK ACTIONS ================= -->
    <section class="container-app relative z-10 -mt-8 grid gap-3 sm:grid-cols-3">
      <RouterLink to="/scan" class="card group flex items-center gap-4 p-4 transition hover:-translate-y-0.5 hover:shadow-lift">
        <span class="grid h-12 w-12 place-items-center rounded-2xl bg-brand-500 text-white shadow-brand"><QrCode class="h-6 w-6" /></span>
        <span class="flex-1"><span class="block font-bold">Scan & order</span><span class="text-xs text-ink-500">Your table is set automatically</span></span>
        <ArrowRight class="h-5 w-5 text-ink-300 transition group-hover:translate-x-1 group-hover:text-brand-500" />
      </RouterLink>
      <RouterLink to="/orders" class="card group flex items-center gap-4 p-4 transition hover:-translate-y-0.5 hover:shadow-lift">
        <span class="grid h-12 w-12 place-items-center rounded-2xl bg-ink-900 text-white"><Receipt class="h-6 w-6" /></span>
        <span class="flex-1"><span class="block font-bold">Track your order</span><span class="text-xs text-ink-500">Live status from the kitchen</span></span>
        <ArrowRight class="h-5 w-5 text-ink-300 transition group-hover:translate-x-1 group-hover:text-brand-500" />
      </RouterLink>
      <div class="card flex items-center gap-4 p-4">
        <span class="grid h-12 w-12 place-items-center rounded-2xl bg-leaf-50 text-leaf-500"><Clock class="h-6 w-6" /></span>
        <span class="flex-1"><span class="block font-bold">Open today</span><span class="text-xs text-ink-500">{{ settings.state.opening_hours || 'See you soon' }}</span></span>
      </div>
    </section>

    <div class="container-app">
      <div v-if="error" class="mt-12">
        <EmptyState :icon="CircleAlert" tone="error" title="We couldn't load the menu" :message="error">
          <button class="btn-primary" @click="load"><RefreshCw class="h-4 w-4" /> Try again</button>
        </EmptyState>
      </div>

      <template v-else>
        <!-- ================= CATEGORIES ================= -->
        <section class="mt-14">
          <div class="flex items-end justify-between">
            <div><p class="eyebrow">Explore</p><h2 class="mt-1 text-2xl font-extrabold sm:text-3xl">What are you craving?</h2></div>
            <RouterLink to="/menu" class="hidden text-sm font-bold text-brand-600 hover:text-brand-700 sm:block">View full menu →</RouterLink>
          </div>
          <div class="no-scrollbar -mx-4 mt-6 flex gap-3 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-5 sm:px-0 lg:grid-cols-9">
            <template v-if="loading">
              <div v-for="n in 9" :key="n" class="skeleton h-28 w-24 shrink-0 rounded-3xl sm:w-auto" />
            </template>
            <RouterLink
              v-for="category in categories"
              v-else
              :key="category.id"
              :to="{ path: '/menu', query: { category: category.name } }"
              class="group flex w-24 shrink-0 flex-col items-center gap-2 rounded-3xl border border-ink-100 bg-white p-4 text-center shadow-sm transition hover:-translate-y-1 hover:border-brand-200 hover:shadow-card sm:w-auto"
            >
              <span class="grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600 transition group-hover:bg-brand-500 group-hover:text-white">
                <CategoryIcon :icon="category.icon" :name="category.name" class="h-6 w-6" />
              </span>
              <span class="text-xs leading-tight font-bold text-ink-700">{{ category.name }}</span>
            </RouterLink>
          </div>
        </section>

        <!-- ================= POPULAR ================= -->
        <section class="mt-14">
          <div class="flex items-end justify-between">
            <div><p class="eyebrow inline-flex items-center gap-1.5"><TrendingUp class="h-3.5 w-3.5" /> Most ordered</p><h2 class="mt-1 text-2xl font-extrabold sm:text-3xl">Popular right now</h2></div>
            <RouterLink to="/menu" class="text-sm font-bold text-brand-600 hover:text-brand-700">See all</RouterLink>
          </div>
          <div class="mt-6 grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
            <template v-if="loading"><FoodCardSkeleton v-for="n in 8" :key="n" /></template>
            <FoodCard v-for="item in popular" v-else :key="item.id" :item="item" />
          </div>
        </section>

        <!-- ================= RECOMMENDED (feature) ================= -->
        <section v-if="loading || recommended.length" class="mt-16 overflow-hidden rounded-[2rem] bg-gradient-to-br from-brand-50 via-white to-amber-50 p-5 ring-1 ring-brand-100 sm:p-8">
          <div class="flex items-end justify-between">
            <div><p class="eyebrow">Chef's picks</p><h2 class="mt-1 text-2xl font-extrabold sm:text-3xl">Recommended for you</h2></div>
          </div>
          <div class="mt-6 grid grid-cols-2 gap-3 sm:gap-5 lg:grid-cols-4">
            <template v-if="loading"><FoodCardSkeleton v-for="n in 4" :key="n" /></template>
            <FoodCard v-for="item in recommended" v-else :key="item.id" :item="item" />
          </div>
        </section>

        <!-- ================= NEW ================= -->
        <section v-if="!loading && newArrivals.length" class="mt-16">
          <p class="eyebrow">Just landed</p>
          <h2 class="mt-1 text-2xl font-extrabold sm:text-3xl">New arrivals</h2>
          <div class="mt-6 grid gap-3 sm:grid-cols-2">
            <FoodCard v-for="item in newArrivals" :key="item.id" :item="item" layout="row" />
          </div>
        </section>

        <!-- ================= HOW IT WORKS ================= -->
        <section class="mt-20">
          <div class="text-center">
            <p class="eyebrow">How it works</p>
            <h2 class="mt-1 text-2xl font-extrabold sm:text-3xl">From table to tasty in three taps</h2>
          </div>
          <div class="mt-8 grid gap-4 sm:grid-cols-3">
            <div v-for="(step, index) in [
              { icon: ScanLine, title: 'Scan the table QR', text: 'Your table number is detected automatically — nothing to type.' },
              { icon: ChefHat, title: 'Order your favourites', text: 'Customise quantities, add notes and send it straight to the kitchen.' },
              { icon: BellRing, title: 'Track it live', text: 'Watch your order go from the pan to your plate in real time.' },
            ]" :key="step.title" class="card-pad relative">
              <span class="absolute top-5 right-6 font-display text-5xl text-ink-100">0{{ index + 1 }}</span>
              <span class="grid h-12 w-12 place-items-center rounded-2xl bg-ink-900 text-white"><component :is="step.icon" class="h-6 w-6" /></span>
              <h3 class="mt-4 font-extrabold">{{ step.title }}</h3>
              <p class="mt-1 text-sm text-ink-500">{{ step.text }}</p>
            </div>
          </div>
          <p class="mt-6 flex items-center justify-center gap-2 text-xs text-ink-400"><ShieldCheck class="h-4 w-4" /> Pay with cash or at the counter — no card details needed.</p>
        </section>
      </template>
    </div>
  </div>
</template>
