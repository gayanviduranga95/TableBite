<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import BottomNav from '../components/BottomNav.vue'
import SiteFooter from '../components/SiteFooter.vue'
import ActiveOrderBanner from '../components/ActiveOrderBanner.vue'
import { useAuth } from '../stores/authStore'
import { useToast } from '../composables/useToast'
import { connectUserSocket } from '../services/socketService'
import { showSystemNotification } from '../composables/useNotifications'

const auth = useAuth()
const toast = useToast()
const banner = ref(null)
let socket = null

// Signed-in customers get live toasts for all of their orders on every page.
watch(() => auth.isCustomer.value, (isCustomer) => {
  socket?.close()
  socket = null
  if (!isCustomer) return
  socket = connectUserSocket((event) => {
    if (event.type === 'ORDER_STATUS_CHANGED' || event.type === 'PAYMENT_UPDATED') {
      if (!location.pathname.startsWith(`/orders/${event.orderId}`)) {
        toast.info(event.message, { title: 'Order update' })
      }
      showSystemNotification('TableBite order update', event.message)
      banner.value?.refresh()
    }
  })
}, { immediate: true })

onBeforeUnmount(() => socket?.close())
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <AppHeader />
    <main class="flex-1 pb-24 md:pb-0">
      <slot />
    </main>
    <SiteFooter />
    <ActiveOrderBanner ref="banner" />
    <BottomNav />
  </div>
</template>
