<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMenu, getTable } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'

const route = useRoute()
const router = useRouter()
const store = useOrderStore()
const menu = ref([])
const table = ref(null)
const search = ref('')
const activeCategory = ref('All')
const loading = ref(true)
const error = ref('')

const imageMap = {
  rice: 'https://images.unsplash.com/photo-1603133872878-684f208fb84b?auto=format&fit=crop&w=900&q=85',
  burger: 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=900&q=85',
  lime: 'https://images.unsplash.com/photo-1513558161293-cda7e4f0e2b2?auto=format&fit=crop&w=900&q=85',
  juice: 'https://images.unsplash.com/photo-1544145945-f90425340c7e?auto=format&fit=crop&w=900&q=85',
  default: 'https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=900&q=85',
}

function imageFor(item) {
  const name = `${item.name} ${item.description || ''}`.toLowerCase()
  if (name.includes('rice')) return imageMap.rice
  if (name.includes('burger')) return imageMap.burger
  if (name.includes('lime')) return imageMap.lime
  if (name.includes('juice') || name.includes('drink')) return imageMap.juice
  return imageMap.default
}

function fallbackImage(event) {
  event.target.src = imageMap.default
}

const categories = computed(() => [
  'All',
  ...new Set(menu.value.map((item) => item.category).filter(Boolean)),
])

const filteredMenu = computed(() => menu.value.filter((item) => {
  const text = `${item.name} ${item.description || ''}`.toLowerCase()
  return (activeCategory.value === 'All' || item.category === activeCategory.value)
    && text.includes(search.value.toLowerCase())
}))

onMounted(async () => {
  try {
    store.state.tableToken = route.params.token
    ;[menu.value, table.value] = await Promise.all([
      getMenu(),
      getTable(route.params.token),
    ])
    store.setTable({ token: route.params.token, tableNumber: table.value.tableNumber })
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="customer-page">
    <div class="menu-hero">
      <div>
        <p class="eyebrow eyebrow-light">Welcome to TableBite</p>
        <h1>Good food.<br><em>Good mood.</em></h1>
        <p class="hero-copy">Order your favourites from the comfort of your table.</p>
        <div class="table-chip">● Table {{ table?.tableNumber || '...' }}</div>
      </div>
      <div class="hero-plate" aria-hidden="true">
        <div class="plate-glow"></div>
        <img src="https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=900&q=85" alt="">
      </div>
    </div>

    <div class="menu-toolbar">
      <div>
        <p class="eyebrow">Our menu</p>
        <h2 class="section-title">Made fresh for you</h2>
      </div>
      <button class="cart-button" @click="router.push('/cart')">
        <span class="cart-icon">🛒</span> Cart
        <b>{{ store.state.cart.reduce((sum, item) => sum + item.quantity, 0) }}</b>
      </button>
    </div>

    <label class="search-box">
      <span>⌕</span>
      <input v-model="search" type="search" placeholder="Search dishes, drinks..." aria-label="Search menu">
    </label>

    <div class="category-scroll" aria-label="Menu categories">
      <button
        v-for="category in categories"
        :key="category"
        class="category-pill"
        :class="{ active: category === activeCategory }"
        @click="activeCategory = category"
      >
        {{ category === 'All' ? 'Everything' : `Category ${category}` }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="loading" class="food-grid">
      <div v-for="n in 4" :key="n" class="food-card skeleton-card">
        <div class="skeleton skeleton-image"></div><div class="skeleton skeleton-line"></div><div class="skeleton skeleton-line short"></div>
      </div>
    </div>
    <div v-else-if="!filteredMenu.length" class="empty-state">
      <span class="empty-icon">✦</span><h2>No dishes found</h2><p>Try a different search or category.</p>
    </div>
    <div v-else class="food-grid">
      <article v-for="item in filteredMenu" :key="item.id" class="food-card">
        <div class="food-image-wrap">
          <img class="food-image" :src="imageFor(item)" :alt="item.name" loading="lazy" @error="fallbackImage">
          <span class="food-badge">Fresh</span>
        </div>
        <div class="food-content">
          <div class="food-heading">
            <h3>{{ item.name }}</h3>
            <strong>Rs {{ Number(item.price).toFixed(2) }}</strong>
          </div>
          <p>{{ item.description || 'Prepared with care by our kitchen.' }}</p>
          <button class="add-button" @click="store.add({ ...item, image: imageFor(item) })">
            <span>＋</span> Add to order
          </button>
        </div>
      </article>
    </div>

    <div class="feature-strip">
      <div><span>✦</span><strong>Fresh ingredients</strong><small>Made to order</small></div>
      <div><span>◷</span><strong>Easy ordering</strong><small>Scan, tap, enjoy</small></div>
      <div><span>♡</span><strong>Made with love</strong><small>Every single plate</small></div>
    </div>
  </section>
</template>
