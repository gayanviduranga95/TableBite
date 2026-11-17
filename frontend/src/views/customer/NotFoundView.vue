<script setup>
import { CookingPot } from 'lucide-vue-next'
import EmptyState from '../../components/EmptyState.vue'
</script>

<template>
  <div class="container-app py-16">
    <EmptyState :icon="CookingPot" title="Page not found" message="Check the address or return to the menu.">
      <RouterLink to="/" class="btn-primary">Go home</RouterLink>
      <RouterLink to="/menu" class="btn-light">Browse the menu</RouterLink>
    </EmptyState>
  </div>
</template>
