<script setup>
// Floating shortcut to the customer's most recent live order.
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRight, ChefHat } from 'lucide-vue-next'
import { getOrder } from '../services/orderService'
import { useOrderStore } from '../stores/orderStore'
import { ACTIVE_STATUSES, STATUS } from '../utils/status'

const route = useRoute()
const cart = useOrderStore()
const order = ref(null)

async function refresh() {
  const last = cart.state.lastOrder
  if (!last) { order.value = null; return }
  try {
    const fresh = await getOrder(last.id, last.token)
    order.value = ACTIVE_STATUSES.includes(fresh.status) ? fresh : null
  } catch {
    order.value = null
  }
}

const visible = computed(() => order.value && !route.path.startsWith('/orders') && route.path !== '/cart')
onMounted(refresh)
watch(() => route.path, refresh)
defineExpose({ refresh })
</script>

<template>
  <Transition name="slide-up">
    <RouterLink
      v-if="visible"
      :to="`/orders/${order.id}`"
      class="no-print fixed inset-x-3 bottom-20 z-30 mx-auto flex max-w-md items-center gap-3 rounded-2xl bg-ink-900 p-3 pr-4 text-white shadow-lift md:bottom-6"
    >
      <span class="grid h-11 w-11 animate-pulse-ring place-items-center rounded-xl bg-brand-500"><ChefHat class="h-5.5 w-5.5" /></span>
      <span class="min-w-0 flex-1">
        <span class="block text-xs text-ink-300">Order {{ order.orderNumber }}</span>
        <span class="block truncate font-bold">{{ STATUS[order.status].customer }} — tap to track</span>
      </span>
      <ChevronRight class="h-5 w-5 text-ink-300" />
    </RouterLink>
  </Transition>
</template>
