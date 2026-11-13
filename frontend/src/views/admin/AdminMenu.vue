<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ImagePlus, LoaderCircle, Pencil, Plus, Search, Star, ThumbsUp, Trash2, UtensilsCrossed } from 'lucide-vue-next'
import * as service from '../../services/orderService'
import { useToast } from '../../composables/useToast'
import { useConfirm } from '../../composables/useConfirm'
import { money } from '../../utils/formatters'
import { dishImage, onImageError } from '../../utils/images'
import BaseModal from '../../components/BaseModal.vue'
import CategoryIcon from '../../components/CategoryIcon.vue'
import EmptyState from '../../components/EmptyState.vue'

const toast = useToast()
const { confirm } = useConfirm()
const tab = ref('dishes')
const items = ref([])
const categories = ref([])
const loading = ref(true)
const search = ref('')
const categoryFilter = ref('')

const filtered = computed(() => items.value.filter((item) =>
  (!categoryFilter.value || String(item.categoryId) === categoryFilter.value)
  && `${item.name} ${item.category}`.toLowerCase().includes(search.value.toLowerCase())))

async function load() {
  try {
    ;[items.value, categories.value] = await Promise.all([service.getAdminMenu(), service.getAdminCategories()])
  } catch (exception) {
    toast.error(exception)
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ---------------------------------------------------------------- dishes
const dishModal = ref(false)
const saving = ref(false)
const uploading = ref(false)
const formError = ref('')
const blankDish = () => ({ id: null, name: '', description: '', price: '', categoryId: categories.value[0]?.id ?? null, imageUrl: '', available: true, popular: false, recommended: false, vegetarian: false, spicy: false, prepMinutes: 15 })
const dish = reactive(blankDish())

function openDish(item) {
  Object.assign(dish, blankDish(), item ? { ...item, price: Number(item.price) } : {})
  formError.value = ''
  dishModal.value = true
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { toast.error('Please choose a JPG, PNG or WebP image'); return }
  if (file.size > 5 * 1024 * 1024) { toast.error('Images must be smaller than 5 MB'); return }
  uploading.value = true
  try {
    dish.imageUrl = (await service.uploadImage(file)).url
    toast.success('Image uploaded')
  } catch (exception) {
    toast.error(exception)
  } finally {
    uploading.value = false
  }
}

async function saveDish() {
  formError.value = ''
  if (dish.name.trim().length < 2) { formError.value = 'Enter a dish name'; return }
  if (dish.price === '' || Number(dish.price) < 0) { formError.value = 'Enter a valid price'; return }
  saving.value = true
  const payload = { ...dish, price: Number(dish.price), prepMinutes: Number(dish.prepMinutes), imageUrl: dish.imageUrl || null }
  try {
    if (dish.id) await service.updateMenuItem(dish.id, payload)
    else await service.createMenuItem(payload)
    toast.success(dish.id ? 'Dish updated' : 'Dish added to the menu')
    dishModal.value = false
    await load()
  } catch (exception) {
    formError.value = exception.message
  } finally {
    saving.value = false
  }
}

async function toggleAvailable(item) {
  try {
    const updated = await service.setAvailability(item.id, !item.available)
    Object.assign(item, updated)
    toast.success(`${item.name} is now ${updated.available ? 'available' : 'sold out'}`)
  } catch (exception) {
    toast.error(exception)
  }
}

async function removeDish(item) {
  if (!(await confirm({ title: `Delete ${item.name}?`, message: 'It will be removed from the menu. Past orders keep their history.', confirmText: 'Delete dish', danger: true }))) return
  try {
    await service.deleteMenuItem(item.id)
    toast.success('Dish deleted')
    await load()
  } catch (exception) {
    toast.error(exception)
  }
}

// ---------------------------------------------------------------- categories
const ICONS = ['starter', 'burger', 'pizza', 'rice', 'noodles', 'curry', 'salad', 'dessert', 'drink', 'coffee', 'icecream', 'sandwich', 'breakfast', 'special']
const categoryModal = ref(false)
const category = reactive({ id: null, name: '', icon: 'special', sortOrder: 100, active: true })
const categoryError = ref('')

function openCategory(item) {
  Object.assign(category, { id: null, name: '', icon: 'special', sortOrder: (categories.value.length + 1) * 10, active: true }, item || {})
  categoryError.value = ''
  categoryModal.value = true
}

async function saveCategory() {
  categoryError.value = ''
  saving.value = true
  try {
    if (category.id) await service.updateCategory(category.id, category)
    else await service.createCategory(category)
    toast.success('Category saved')
    categoryModal.value = false
    await load()
  } catch (exception) {
    categoryError.value = exception.message
  } finally {
    saving.value = false
  }
}

async function removeCategory(item) {
  if (!(await confirm({ title: `Delete “${item.name}”?`, message: item.itemCount ? `It still has ${item.itemCount} dish(es); move them first.` : 'This cannot be undone.', confirmText: 'Delete', danger: true }))) return
  try {
    await service.deleteCategory(item.id)
    toast.success('Category deleted')
    await load()
  } catch (exception) {
    toast.error(exception)
  }
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="eyebrow">Catalogue</p>
        <h1 class="mt-1 text-3xl font-extrabold">Menu</h1>
        <p class="text-sm text-ink-500">{{ items.length }} dishes · {{ items.filter((i) => !i.available).length }} sold out · {{ categories.length }} categories</p>
      </div>
      <button v-if="tab === 'dishes'" class="btn-primary" @click="openDish()"><Plus class="h-4 w-4" /> Add dish</button>
      <button v-else class="btn-primary" @click="openCategory()"><Plus class="h-4 w-4" /> Add category</button>
    </div>

    <div class="mt-6 inline-flex rounded-xl bg-ink-100 p-1">
      <button v-for="t in [['dishes', 'Dishes'], ['categories', 'Categories']]" :key="t[0]" class="rounded-lg px-4 py-1.5 text-sm font-bold transition" :class="tab === t[0] ? 'bg-white shadow-sm' : 'text-ink-500'" @click="tab = t[0]">{{ t[1] }}</button>
    </div>

    <div v-if="loading" class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><div v-for="n in 6" :key="n" class="skeleton h-64 rounded-3xl" /></div>

    <!-- Dishes -->
    <template v-else-if="tab === 'dishes'">
      <div class="mt-4 flex flex-col gap-2 sm:flex-row">
        <label class="flex flex-1 items-center gap-2 rounded-xl border border-ink-200 bg-white px-3">
          <Search class="h-4 w-4 text-ink-400" /><input v-model="search" type="search" class="flex-1 bg-transparent py-2.5 text-sm outline-none" placeholder="Search dishes" aria-label="Search dishes">
        </label>
        <select v-model="categoryFilter" class="input sm:w-56" aria-label="Category"><option value="">All categories</option><option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option></select>
      </div>
      <EmptyState v-if="!filtered.length" class="mt-6" :icon="UtensilsCrossed" title="No dishes found" message="Add your first dish or change the filters."><button class="btn-primary" @click="openDish()">Add dish</button></EmptyState>
      <div v-else class="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article v-for="item in filtered" :key="item.id" class="card overflow-hidden">
          <div class="relative aspect-[16/9] bg-ink-50">
            <img :src="dishImage(item)" :alt="item.name" class="h-full w-full object-cover" :class="{ 'opacity-50 grayscale': !item.available }" loading="lazy" @error="onImageError">
            <div class="absolute top-2 left-2 flex gap-1">
              <span v-if="item.popular" class="inline-flex items-center gap-1 rounded-full bg-white px-2 py-0.5 text-[11px] font-bold text-brand-700"><Star class="h-3 w-3 fill-current" /> Popular</span>
              <span v-if="item.recommended" class="inline-flex items-center gap-1 rounded-full bg-white px-2 py-0.5 text-[11px] font-bold text-ink-700"><ThumbsUp class="h-3 w-3" /> Chef's pick</span>
            </div>
          </div>
          <div class="p-4">
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0">
                <h3 class="truncate font-bold">{{ item.name }}</h3>
                <p class="text-xs text-ink-500">{{ item.category || 'Uncategorised' }} · {{ item.orderCount }} sold</p>
              </div>
              <strong class="shrink-0">{{ money(item.price) }}</strong>
            </div>
            <div class="mt-4 flex items-center justify-between">
              <label class="inline-flex cursor-pointer items-center gap-2 text-sm font-semibold">
                <button
                  role="switch"
                  :aria-checked="item.available"
                  class="relative h-6 w-11 rounded-full transition"
                  :class="item.available ? 'bg-leaf-500' : 'bg-ink-200'"
                  @click="toggleAvailable(item)"
                ><span class="absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition" :class="{ 'translate-x-5': item.available }" /></button>
                {{ item.available ? 'Available' : 'Sold out' }}
              </label>
              <div class="flex gap-1">
                <button class="btn-ghost btn-icon" aria-label="Edit" @click="openDish(item)"><Pencil class="h-4 w-4" /></button>
                <button class="btn-ghost btn-icon text-red-600 hover:bg-red-50" aria-label="Delete" @click="removeDish(item)"><Trash2 class="h-4 w-4" /></button>
              </div>
            </div>
          </div>
        </article>
      </div>
    </template>

    <!-- Categories -->
    <div v-else class="card mt-6 overflow-x-auto">
      <table class="table-base">
        <thead><tr><th>Category</th><th>Dishes</th><th>Order</th><th>Status</th><th class="text-right">Actions</th></tr></thead>
        <tbody>
          <tr v-for="c in categories" :key="c.id">
            <td><span class="inline-flex items-center gap-3 font-bold"><span class="grid h-9 w-9 place-items-center rounded-xl bg-brand-50 text-brand-600"><CategoryIcon :icon="c.icon" :name="c.name" class="h-4.5 w-4.5" /></span>{{ c.name }}</span></td>
            <td>{{ c.itemCount }}</td>
            <td>{{ c.sortOrder }}</td>
            <td><span class="text-xs font-bold" :class="c.active ? 'text-emerald-600' : 'text-ink-400'">{{ c.active ? 'Visible' : 'Hidden' }}</span></td>
            <td class="text-right">
              <button class="btn-ghost btn-icon" aria-label="Edit" @click="openCategory(c)"><Pencil class="h-4 w-4" /></button>
              <button class="btn-ghost btn-icon text-red-600 hover:bg-red-50" aria-label="Delete" @click="removeCategory(c)"><Trash2 class="h-4 w-4" /></button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Dish modal -->
    <BaseModal :open="dishModal" :title="dish.id ? 'Edit dish' : 'Add a dish'" width="max-w-2xl" @close="dishModal = false">
      <form id="dish-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="saveDish">
        <div class="sm:col-span-2">
          <span class="field-label">Photo</span>
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
            <img :src="dishImage(dish)" alt="Dish preview" class="aspect-[4/3] w-full rounded-2xl object-cover sm:w-48" @error="onImageError">
            <div class="flex-1 space-y-2">
              <label class="btn-light cursor-pointer">
                <LoaderCircle v-if="uploading" class="h-4 w-4 animate-spin" /><ImagePlus v-else class="h-4 w-4" />
                {{ uploading ? 'Uploading…' : 'Upload image' }}
                <input type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="upload">
              </label>
              <p class="text-xs text-ink-500">JPG, PNG or WebP up to 5 MB. Landscape 4:3 photos look best.</p>
              <input v-model="dish.imageUrl" class="input text-xs" placeholder="…or paste an image path / https URL" maxlength="500">
            </div>
          </div>
        </div>
        <label class="sm:col-span-2"><span class="field-label">Name</span><input v-model="dish.name" class="input" required maxlength="150"></label>
        <label class="sm:col-span-2"><span class="field-label">Description</span><textarea v-model="dish.description" class="input min-h-20 resize-none" maxlength="1000"></textarea></label>
        <label><span class="field-label">Price</span><input v-model="dish.price" type="number" min="0" step="0.01" class="input" required></label>
        <label><span class="field-label">Category</span><select v-model="dish.categoryId" class="input"><option :value="null">Uncategorised</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label>
        <label><span class="field-label">Prep time (minutes)</span><input v-model="dish.prepMinutes" type="number" min="0" max="240" class="input"></label>
        <div class="grid grid-cols-2 gap-2 self-end text-sm font-semibold">
          <label class="flex items-center gap-2"><input v-model="dish.available" type="checkbox" class="h-4 w-4 accent-brand-500"> Available</label>
          <label class="flex items-center gap-2"><input v-model="dish.popular" type="checkbox" class="h-4 w-4 accent-brand-500"> Popular</label>
          <label class="flex items-center gap-2"><input v-model="dish.recommended" type="checkbox" class="h-4 w-4 accent-brand-500"> Recommended</label>
          <label class="flex items-center gap-2"><input v-model="dish.vegetarian" type="checkbox" class="h-4 w-4 accent-brand-500"> Vegetarian</label>
          <label class="flex items-center gap-2"><input v-model="dish.spicy" type="checkbox" class="h-4 w-4 accent-brand-500"> Spicy</label>
        </div>
        <p v-if="formError" class="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700 sm:col-span-2">{{ formError }}</p>
      </form>
      <template #footer>
        <button class="btn-light" @click="dishModal = false">Cancel</button>
        <button class="btn-primary" form="dish-form" :disabled="saving || uploading"><LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" /> {{ dish.id ? 'Save changes' : 'Add dish' }}</button>
      </template>
    </BaseModal>

    <!-- Category modal -->
    <BaseModal :open="categoryModal" :title="category.id ? 'Edit category' : 'Add a category'" @close="categoryModal = false">
      <form id="category-form" class="space-y-4" @submit.prevent="saveCategory">
        <label class="block"><span class="field-label">Name</span><input v-model="category.name" class="input" required maxlength="100"></label>
        <div>
          <span class="field-label">Icon</span>
          <div class="grid grid-cols-7 gap-2">
            <button v-for="icon in ICONS" :key="icon" type="button" class="grid aspect-square place-items-center rounded-xl border-2 transition" :class="category.icon === icon ? 'border-brand-500 bg-brand-50 text-brand-600' : 'border-ink-100 text-ink-500 hover:border-ink-200'" :aria-label="icon" @click="category.icon = icon">
              <CategoryIcon :icon="icon" class="h-5 w-5" />
            </button>
          </div>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <label><span class="field-label">Display order</span><input v-model.number="category.sortOrder" type="number" min="0" class="input"></label>
          <label class="flex items-center gap-2 self-end pb-2.5 text-sm font-semibold"><input v-model="category.active" type="checkbox" class="h-4 w-4 accent-brand-500"> Visible to customers</label>
        </div>
        <p v-if="categoryError" class="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700">{{ categoryError }}</p>
      </form>
      <template #footer>
        <button class="btn-light" @click="categoryModal = false">Cancel</button>
        <button class="btn-primary" form="category-form" :disabled="saving">Save category</button>
      </template>
    </BaseModal>
  </div>
</template>
