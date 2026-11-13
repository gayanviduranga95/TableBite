<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeft, Banknote, CreditCard, LoaderCircle, MapPin, MessageSquareText, ScanLine,
  ShoppingBag, Store, Trash2, TriangleAlert, UtensilsCrossed,
} from 'lucide-vue-next'
import { createOrder, getMenu } from '../../services/orderService'
import { useAuth } from '../../stores/authStore'
import { useOrderStore } from '../../stores/orderStore'
import { useSettings } from '../../stores/settingsStore'
import { useToast } from '../../composables/useToast'
import { useConfirm } from '../../composables/useConfirm'
import { requestNotificationPermission } from '../../composables/useNotifications'
import { money } from '../../utils/formatters'
import { dishImage, onImageError } from '../../utils/images'
import EmptyState from '../../components/EmptyState.vue'
import QuantityStepper from '../../components/QuantityStepper.vue'

const router = useRouter()
const cart = useOrderStore()
const auth = useAuth()
const settings = useSettings()
const toast = useToast()
const { confirm } = useConfirm()

const form = reactive({
  paymentMethod: 'PAY_AT_COUNTER',
  specialInstructions: '',
  customerName: auth.user.value?.fullName || '',
  customerPhone: auth.user.value?.phone || '',
})
const errors = reactive({})
const placing = ref(false)
const serverError = ref('')
const editingNote = ref(null)
// One idempotency key per checkout attempt: a double tap or network retry never creates two orders.
let idempotencyKey = newKey()

function newKey() {
  return (crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`).replace(/[^A-Za-z0-9_-]/g, '')
}

const isDineIn = computed(() => cart.state.orderType === 'DINE_IN')
const canPlace = computed(() => cart.state.items.length && !cart.hasUnavailable.value && (!isDineIn.value || cart.state.table))

const paymentOptions = computed(() => [
  { value: 'PAY_AT_COUNTER', label: 'Pay at counter', hint: 'Settle the bill at the counter when you leave', icon: Store },
  { value: 'CASH', label: 'Cash', hint: isDineIn.value ? 'Pay cash to your server at the table' : 'Pay cash on pick-up', icon: Banknote },
  { value: 'ONLINE', label: 'Card / online', hint: settings.state.online_payment_enabled ? 'Pay securely online' : 'Not available at this restaurant yet', icon: CreditCard, disabled: !settings.state.online_payment_enabled },
])

watch(() => cart.state.items.length, () => { idempotencyKey = newKey() })

function validate() {
  Object.keys(errors).forEach((key) => delete errors[key])
  if (!isDineIn.value) {
    if (!form.customerName.trim()) errors.customerName = 'Please enter your name'
    if (!/^[+0-9 ()-]{7,20}$/.test(form.customerPhone.trim())) errors.customerPhone = 'Enter a valid phone number so we can reach you'
  }
  return !Object.keys(errors).length
}

async function placeOrder() {
  serverError.value = ''
  if (!validate() || !canPlace.value) return
  placing.value = true
  try {
    const result = await createOrder({
      orderType: cart.state.orderType,
      tableToken: isDineIn.value ? cart.state.table.token : undefined,
      items: cart.state.items.map((item) => ({ menuItemId: item.id, quantity: item.quantity, notes: item.notes || undefined })),
      specialInstructions: form.specialInstructions.trim() || undefined,
      customerName: form.customerName.trim() || undefined,
      customerPhone: form.customerPhone.trim() || undefined,
      paymentMethod: form.paymentMethod,
      idempotencyKey,
    })
    cart.rememberOrder(result.order, result.trackingToken)
    cart.clear()
    idempotencyKey = newKey()
    requestNotificationPermission()
    toast.success(`Order ${result.orderNumber} sent to the kitchen!`, { title: 'Order placed' })
    router.push({ path: `/orders/${result.orderId}`, query: { placed: '1' } })
  } catch (exception) {
    serverError.value = exception.message
    if (exception.status === 400) {
      // Prices or availability may have changed: refresh the cart from the live menu.
      try { cart.syncWithMenu(await getMenu()) } catch { /* ignore */ }
    }
  } finally {
    placing.value = false
  }
}

async function clearCart() {
  if (await confirm({ title: 'Clear your cart?', message: 'All items will be removed.', confirmText: 'Clear cart', danger: true })) {
    cart.clear()
  }
}

onMounted(async () => {
  try { cart.syncWithMenu(await getMenu()) } catch { /* offline: keep cart as-is */ }
  if (!cart.state.table && !settings.takeaway) cart.state.orderType = 'DINE_IN'
})
</script>

<template>
  <div class="container-app py-6 sm:py-10">
    <button class="btn-ghost -ml-3 mb-2" @click="router.push('/menu')"><ArrowLeft class="h-4 w-4" /> Continue browsing</button>
    <div class="flex items-end justify-between">
      <div>
        <p class="eyebrow">Almost there</p>
        <h1 class="mt-1 font-display text-4xl font-normal sm:text-5xl">Your order</h1>
      </div>
      <button v-if="cart.state.items.length" class="btn-ghost btn-sm text-red-600 hover:bg-red-50 hover:text-red-700" @click="clearCart"><Trash2 class="h-4 w-4" /> Clear cart</button>
    </div>

    <EmptyState v-if="!cart.state.items.length" class="mt-8" :icon="ShoppingBag" title="Your cart is empty" message="Add something delicious from the menu and it will show up here.">
      <RouterLink to="/menu" class="btn-primary">Browse the menu</RouterLink>
    </EmptyState>

    <div v-else class="mt-8 grid gap-6 lg:grid-cols-[1fr_380px] lg:items-start">
      <div class="space-y-6">
        <!-- Order type -->
        <div class="card-pad">
          <h2 class="font-extrabold">How would you like it?</h2>
          <div class="mt-4 grid grid-cols-2 gap-3">
            <button
              class="flex items-center gap-3 rounded-2xl border-2 p-4 text-left transition"
              :class="isDineIn ? 'border-brand-500 bg-brand-50' : 'border-ink-100 hover:border-ink-200'"
              @click="cart.state.orderType = 'DINE_IN'"
            >
              <UtensilsCrossed class="h-6 w-6 text-brand-600" />
              <span><span class="block font-bold">Dine in</span><span class="text-xs text-ink-500">Served at your table</span></span>
            </button>
            <button
              class="flex items-center gap-3 rounded-2xl border-2 p-4 text-left transition disabled:opacity-50"
              :class="!isDineIn ? 'border-brand-500 bg-brand-50' : 'border-ink-100 hover:border-ink-200'"
              :disabled="!settings.takeaway"
              @click="cart.state.orderType = 'TAKEAWAY'"
            >
              <ShoppingBag class="h-6 w-6 text-brand-600" />
              <span><span class="block font-bold">Takeaway</span><span class="text-xs text-ink-500">{{ settings.takeaway ? 'Pick up at the counter' : 'Not available today' }}</span></span>
            </button>
          </div>

          <div v-if="isDineIn" class="mt-4">
            <div v-if="cart.state.table" class="flex items-center gap-3 rounded-2xl bg-ink-900 p-3 text-white">
              <span class="grid h-10 w-10 place-items-center rounded-xl bg-brand-500"><MapPin class="h-5 w-5" /></span>
              <div class="flex-1"><p class="text-xs text-ink-300">Ordering for</p><p class="font-extrabold">Table {{ cart.state.table.tableNumber }}</p></div>
              <RouterLink to="/scan" class="text-xs font-bold text-brand-300 hover:text-white">Change</RouterLink>
            </div>
            <div v-else class="flex flex-col gap-3 rounded-2xl bg-amber-50 p-4 ring-1 ring-amber-200 sm:flex-row sm:items-center">
              <TriangleAlert class="h-5 w-5 shrink-0 text-amber-600" />
              <p class="flex-1 text-sm text-amber-900">Scan the QR code on your table so we know where to bring your food.</p>
              <RouterLink to="/scan" class="btn-primary btn-sm"><ScanLine class="h-4 w-4" /> Scan QR</RouterLink>
            </div>
          </div>

          <div v-else class="mt-4 grid gap-3 sm:grid-cols-2">
            <label>
              <span class="field-label">Name for pick-up</span>
              <input v-model="form.customerName" class="input" :class="{ 'input-error': errors.customerName }" autocomplete="name" maxlength="120">
              <span v-if="errors.customerName" class="field-error">{{ errors.customerName }}</span>
            </label>
            <label>
              <span class="field-label">Phone</span>
              <input v-model="form.customerPhone" class="input" :class="{ 'input-error': errors.customerPhone }" type="tel" autocomplete="tel" maxlength="20">
              <span v-if="errors.customerPhone" class="field-error">{{ errors.customerPhone }}</span>
            </label>
          </div>
        </div>

        <!-- Items -->
        <div class="card overflow-hidden">
          <div class="flex items-center justify-between border-b border-ink-100 px-5 py-4 sm:px-6">
            <h2 class="font-extrabold">Items <span class="text-ink-400">({{ cart.count.value }})</span></h2>
          </div>
          <TransitionGroup name="list" tag="ul" class="relative divide-y divide-ink-100">
            <li v-for="item in cart.state.items" :key="item.id" class="px-4 py-4 sm:px-6">
              <div class="flex gap-3 sm:gap-4">
                <img :src="dishImage(item)" :alt="item.name" class="h-20 w-20 shrink-0 rounded-2xl object-cover" :class="{ grayscale: item.available === false }" @error="onImageError">
                <div class="flex min-w-0 flex-1 flex-col">
                  <div class="flex items-start justify-between gap-2">
                    <h3 class="font-bold leading-snug">{{ item.name }}</h3>
                    <strong class="shrink-0">{{ money(item.price * item.quantity) }}</strong>
                  </div>
                  <p class="text-xs text-ink-500">{{ money(item.price) }} each</p>
                  <p v-if="item.available === false" class="mt-1 inline-flex items-center gap-1 text-xs font-bold text-red-600"><TriangleAlert class="h-3.5 w-3.5" /> No longer available — please remove it</p>
                  <div class="mt-auto flex items-center justify-between gap-2 pt-2">
                    <button class="inline-flex items-center gap-1 text-xs font-semibold text-ink-500 hover:text-brand-600" @click="editingNote = editingNote === item.id ? null : item.id">
                      <MessageSquareText class="h-3.5 w-3.5" /> {{ item.notes ? 'Edit note' : 'Add note' }}
                    </button>
                    <QuantityStepper :quantity="item.quantity" @increase="cart.increase(item.id)" @decrease="cart.decrease(item.id)" />
                  </div>
                </div>
              </div>
              <p v-if="item.notes && editingNote !== item.id" class="mt-2 rounded-xl bg-ink-50 px-3 py-2 text-xs text-ink-600">“{{ item.notes }}”</p>
              <input
                v-if="editingNote === item.id"
                class="input mt-3 text-xs"
                :value="item.notes"
                maxlength="255"
                placeholder="e.g. no onions, well done"
                @input="cart.setNotes(item.id, $event.target.value)"
                @keydown.enter="editingNote = null"
              >
            </li>
          </TransitionGroup>
        </div>

        <!-- Instructions & payment -->
        <div class="card-pad">
          <label class="block">
            <span class="field-label">Special instructions for the kitchen</span>
            <textarea v-model="form.specialInstructions" class="input min-h-20 resize-none" maxlength="500" placeholder="Allergies, celebrations, timing…"></textarea>
          </label>
          <h2 class="mt-6 font-extrabold">Payment method</h2>
          <div class="mt-3 grid gap-2">
            <label
              v-for="option in paymentOptions"
              :key="option.value"
              class="flex cursor-pointer items-center gap-3 rounded-2xl border-2 p-3.5 transition"
              :class="[form.paymentMethod === option.value ? 'border-brand-500 bg-brand-50' : 'border-ink-100 hover:border-ink-200', option.disabled ? 'cursor-not-allowed opacity-50' : '']"
            >
              <input v-model="form.paymentMethod" type="radio" :value="option.value" :disabled="option.disabled" class="sr-only">
              <span class="grid h-10 w-10 place-items-center rounded-xl bg-white text-brand-600 shadow-sm"><component :is="option.icon" class="h-5 w-5" /></span>
              <span class="flex-1"><span class="block text-sm font-bold">{{ option.label }}</span><span class="text-xs text-ink-500">{{ option.hint }}</span></span>
              <span class="h-5 w-5 rounded-full border-2" :class="form.paymentMethod === option.value ? 'border-[6px] border-brand-500' : 'border-ink-200'" />
            </label>
          </div>
        </div>
      </div>

      <!-- Summary -->
      <aside class="card-pad lg:sticky lg:top-24">
        <h2 class="text-lg font-extrabold">Order summary</h2>
        <dl class="mt-4 space-y-2.5 text-sm">
          <div class="flex justify-between"><dt class="text-ink-500">Subtotal</dt><dd class="font-semibold">{{ money(cart.subtotal.value) }}</dd></div>
          <div v-if="settings.taxRate" class="flex justify-between"><dt class="text-ink-500">Tax ({{ settings.taxRate }}%)</dt><dd class="font-semibold">{{ money(cart.tax.value) }}</dd></div>
          <div class="flex justify-between">
            <dt class="text-ink-500">Service charge <span v-if="isDineIn">({{ settings.serviceRate }}%)</span></dt>
            <dd class="font-semibold">{{ isDineIn ? money(cart.serviceCharge.value) : 'None' }}</dd>
          </div>
          <div class="flex items-end justify-between border-t border-dashed border-ink-200 pt-3">
            <dt class="font-bold">Total</dt><dd class="text-2xl font-extrabold">{{ money(cart.total.value) }}</dd>
          </div>
        </dl>
        <p v-if="serverError" class="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700" role="alert">{{ serverError }}</p>
        <p v-else-if="cart.hasUnavailable.value" class="mt-4 rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700">Remove sold-out items to continue.</p>
        <button class="btn-primary btn-lg mt-5 w-full" :disabled="!canPlace || placing" @click="placeOrder">
          <LoaderCircle v-if="placing" class="h-5 w-5 animate-spin" />
          {{ placing ? 'Sending to kitchen…' : `Place order · ${money(cart.total.value)}` }}
        </button>
        <p class="mt-3 text-center text-xs text-ink-400">Final amount is confirmed by the restaurant. You'll be able to track your order live.</p>
      </aside>
    </div>
  </div>
</template>
