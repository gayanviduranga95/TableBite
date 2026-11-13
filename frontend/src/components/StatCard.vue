<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  hint: { type: String, default: '' },
  icon: { type: [Object, Function], default: null },
  accent: { type: String, default: 'bg-brand-50 text-brand-600' },
})
</script>

<template>
  <div class="card p-4 sm:p-5">
    <div class="flex items-start justify-between gap-3">
      <p class="text-[11px] font-bold tracking-wide text-ink-500 uppercase sm:text-xs">{{ label }}</p>
      <span v-if="icon" class="hidden h-10 w-10 shrink-0 place-items-center rounded-xl sm:grid" :class="accent"><component :is="icon" class="h-5 w-5" /></span>
    </div>
    <p class="mt-2 text-xl font-extrabold tracking-tight text-ink-900 tabular-nums sm:text-3xl">{{ value }}</p>
    <p v-if="hint" class="mt-1 text-xs text-ink-500">{{ hint }}</p>
  </div>
</template>
