<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import CustomerLayout from './layouts/CustomerLayout.vue'
import StaffLayout from './layouts/StaffLayout.vue'
import ToastHost from './components/ToastHost.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'

const route = useRoute()
const layout = computed(() => {
  if (route.meta.layout === 'staff') return StaffLayout
  if (route.meta.layout === 'customer') return CustomerLayout
  return null
})
</script>

<template>
  <component :is="layout" v-if="layout">
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" :key="route.path" />
      </Transition>
    </RouterView>
  </component>
  <RouterView v-else />
  <ToastHost />
  <ConfirmDialog />
</template>
