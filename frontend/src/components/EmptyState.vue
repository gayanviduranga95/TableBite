<script setup>
defineProps({
  icon: { type: [Object, Function], default: null },
  title: { type: String, required: true },
  message: { type: String, default: '' },
  tone: { type: String, default: 'brand' }, // brand | error
})
</script>

<template>
  <div class="flex flex-col items-center rounded-3xl border border-dashed border-ink-200 bg-white/60 px-6 py-14 text-center">
    <span
      v-if="icon"
      class="mb-4 grid h-16 w-16 place-items-center rounded-3xl"
      :class="tone === 'error' ? 'bg-red-50 text-red-500' : 'bg-brand-50 text-brand-500'"
    >
      <component :is="icon" class="h-8 w-8" />
    </span>
    <h3 class="text-lg font-extrabold">{{ title }}</h3>
    <p v-if="message" class="mt-1 max-w-sm text-sm text-ink-500">{{ message }}</p>
    <div v-if="$slots.default" class="mt-5 flex flex-wrap justify-center gap-2"><slot /></div>
  </div>
</template>
