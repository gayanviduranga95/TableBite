<script setup>
defineProps({ size: { type: String, default: 'h-9 w-9' } })
</script>

<template>
  <span class="grid shrink-0 place-items-center rounded-xl bg-gradient-to-br from-brand-400 to-brand-600 text-white shadow-brand" :class="size" aria-hidden="true">
    <svg viewBox="0 0 24 24" class="h-[58%] w-[58%]" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M7 3v7a2 2 0 0 0 4 0V3" />
      <path d="M9 3v18" />
      <path d="M17 3c-2 1.2-2.6 5-2.6 7.2H17V21" />
    </svg>
  </span>
</template>
