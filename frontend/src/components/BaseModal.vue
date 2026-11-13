<script setup>
import { X } from 'lucide-vue-next'

defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  width: { type: String, default: 'max-w-lg' },
})
const emit = defineEmits(['close'])
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="fixed inset-0 z-[70] grid place-items-end bg-ink-900/50 backdrop-blur-sm sm:place-items-center sm:p-4" @click.self="emit('close')" @keydown.esc="emit('close')">
        <div class="flex max-h-[92vh] w-full animate-rise flex-col rounded-t-3xl bg-white shadow-lift sm:rounded-3xl" :class="width" role="dialog" aria-modal="true" :aria-label="title">
          <header class="flex items-start justify-between gap-4 border-b border-ink-100 px-6 py-4">
            <div>
              <h2 class="text-lg font-extrabold">{{ title }}</h2>
              <p v-if="subtitle" class="text-sm text-ink-500">{{ subtitle }}</p>
            </div>
            <button class="btn-ghost btn-icon -mr-2" aria-label="Close" @click="emit('close')"><X class="h-5 w-5" /></button>
          </header>
          <div class="overflow-y-auto px-6 py-5"><slot /></div>
          <footer v-if="$slots.footer" class="flex flex-col-reverse gap-2 border-t border-ink-100 px-6 py-4 sm:flex-row sm:justify-end">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
