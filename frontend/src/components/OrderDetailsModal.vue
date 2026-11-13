<script setup>
import { ref, watch } from 'vue'
import { Banknote, CreditCard, LoaderCircle, MapPin, MessageSquareText, Phone, ShoppingBag, Store, User } from 'lucide-vue-next'
import { getOrder } from '../services/orderService'
import { useAuth } from '../stores/authStore'
import { useOrderActions } from '../composables/useOrderActions'
import { dateTime, money } from '../utils/formatters'
import { nextActions, PAYMENT_LABELS, STATUS } from '../utils/status'
import { dishImage, onImageError } from '../utils/images'
import BaseModal from './BaseModal.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps({ orderId: { type: Number, default: null } })
const emit = defineEmits(['close', 'changed'])
const auth = useAuth()
const order = ref(null)
const loading = ref(false)
const error = ref('')

async function load() {
  if (!props.orderId) { order.value = null; return }
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrder(props.orderId)
  } catch (exception) {
    error.value = exception.message
  } finally {
    loading.value = false
  }
}

const { busy, setStatus, markPaid } = useOrderActions(async (updated) => {
  emit('changed', updated)
  await load()
})

const canTakePayment = () => ['ADMIN', 'WAITER', 'STAFF'].includes(auth.role.value)
watch(() => props.orderId, load, { immediate: true })
defineExpose({ reload: load })
</script>

<template>
  <BaseModal :open="!!orderId" :title="order ? `Order ${order.orderNumber}` : 'Order'" :subtitle="order ? dateTime(order.createdAt) : ''" width="max-w-2xl" @close="emit('close')">
    <div v-if="loading && !order" class="grid place-items-center py-16"><LoaderCircle class="h-8 w-8 animate-spin text-brand-500" /></div>
    <p v-else-if="error" class="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700">{{ error }}</p>
    <div v-else-if="order" class="space-y-5">
      <div class="flex flex-wrap items-center gap-2">
        <StatusBadge :status="order.status" />
        <span class="inline-flex items-center gap-1.5 rounded-full bg-ink-100 px-2.5 py-1 text-xs font-bold text-ink-700">
          <MapPin v-if="order.orderType === 'DINE_IN'" class="h-3.5 w-3.5" /><ShoppingBag v-else class="h-3.5 w-3.5" />
          {{ order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : 'Takeaway' }}
        </span>
        <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="order.paymentStatus === 'PAID' ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">
          {{ PAYMENT_LABELS[order.paymentMethod] }} · {{ order.paymentStatus === 'PAID' ? 'Paid' : 'Unpaid' }}
        </span>
      </div>

      <div v-if="order.customerName || order.customerPhone" class="grid gap-2 rounded-2xl bg-ink-50 p-4 text-sm sm:grid-cols-2">
        <p v-if="order.customerName" class="flex items-center gap-2"><User class="h-4 w-4 text-ink-400" /> {{ order.customerName }}</p>
        <p v-if="order.customerPhone" class="flex items-center gap-2"><Phone class="h-4 w-4 text-ink-400" /> <a :href="`tel:${order.customerPhone}`" class="font-semibold text-brand-700">{{ order.customerPhone }}</a></p>
      </div>

      <ul class="divide-y divide-ink-100 rounded-2xl border border-ink-100">
        <li v-for="item in order.items" :key="`${item.menuItemId}-${item.notes}`" class="flex items-center gap-3 p-3">
          <img :src="dishImage(item)" :alt="item.name" class="h-11 w-11 rounded-xl object-cover" @error="onImageError">
          <div class="min-w-0 flex-1">
            <p class="font-semibold"><span class="text-brand-600">{{ item.quantity }}×</span> {{ item.name }}</p>
            <p v-if="item.notes" class="text-xs text-amber-700">“{{ item.notes }}”</p>
          </div>
          <span class="text-sm font-bold">{{ money(item.lineTotal) }}</span>
        </li>
      </ul>
      <p v-if="order.specialInstructions" class="flex gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-900"><MessageSquareText class="mt-0.5 h-4 w-4 shrink-0" /> {{ order.specialInstructions }}</p>

      <dl class="space-y-1.5 text-sm">
        <div class="flex justify-between"><dt class="text-ink-500">Subtotal</dt><dd>{{ money(order.subtotal) }}</dd></div>
        <div v-if="Number(order.taxAmount)" class="flex justify-between"><dt class="text-ink-500">Tax</dt><dd>{{ money(order.taxAmount) }}</dd></div>
        <div v-if="Number(order.serviceCharge)" class="flex justify-between"><dt class="text-ink-500">Service charge</dt><dd>{{ money(order.serviceCharge) }}</dd></div>
        <div class="flex justify-between border-t border-ink-100 pt-2 text-base font-extrabold"><dt>Total</dt><dd>{{ money(order.totalAmount) }}</dd></div>
      </dl>

      <div v-if="order.history?.length">
        <h3 class="mb-2 text-xs font-extrabold tracking-wider text-ink-400 uppercase">Timeline</h3>
        <ol class="space-y-1.5 text-sm">
          <li v-for="entry in order.history" :key="entry.changedAt + entry.toStatus" class="flex gap-2">
            <span class="font-semibold">{{ STATUS[entry.toStatus]?.label }}</span>
            <span v-if="entry.changedBy" class="text-ink-500">by {{ entry.changedBy }}</span>
            <span v-if="entry.note" class="text-ink-500">— {{ entry.note }}</span>
            <span class="ml-auto text-xs text-ink-400">{{ dateTime(entry.changedAt) }}</span>
          </li>
        </ol>
      </div>
    </div>

    <template v-if="order" #footer>
      <template v-if="order.paymentStatus !== 'PAID' && order.status !== 'CANCELLED' && canTakePayment()">
        <button class="btn-light" :disabled="busy[order.id]" @click="markPaid(order, 'CASH')"><Banknote class="h-4 w-4" /> Cash paid</button>
        <button class="btn-light" :disabled="busy[order.id]" @click="markPaid(order, 'CARD')"><CreditCard class="h-4 w-4" /> Card paid</button>
        <button class="btn-light" :disabled="busy[order.id]" @click="markPaid(order, 'PAY_AT_COUNTER')"><Store class="h-4 w-4" /> Paid at counter</button>
      </template>
      <button
        v-for="action in nextActions(order, auth.role.value)"
        :key="action.status"
        :class="action.style"
        :disabled="busy[order.id]"
        @click="setStatus(order, action.status)"
      >{{ action.label }}</button>
    </template>
  </BaseModal>
</template>
