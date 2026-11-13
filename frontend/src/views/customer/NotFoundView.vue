<script setup>
import { CookingPot } from 'lucide-vue-next'
import EmptyState from '../../components/EmptyState.vue'
</script>

<template>
  <div class="container-app py-16">
    <EmptyState :icon="CookingPot" title="This page isn't on the menu" message="The link may be broken or the page may have moved.">
      <RouterLink to="/" class="btn-primary">Go home</RouterLink>
      <RouterLink to="/menu" class="btn-light">Browse the menu</RouterLink>
    </EmptyState>
  </div>
</template>
