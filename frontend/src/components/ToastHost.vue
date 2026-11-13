<script setup>
import { CircleAlert, CircleCheck, Info, X } from 'lucide-vue-next'
import { useToast } from '../composables/useToast'

const { toasts, dismiss } = useToast()
const icons = { success: CircleCheck, error: CircleAlert, info: Info }
const tones = {
  success: 'text-leaf-500 bg-leaf-50',
  error: 'text-red-600 bg-red-50',
  info: 'text-brand-600 bg-brand-50',
}
</script>

<template>
  <div class="no-print pointer-events-none fixed inset-x-0 top-3 z-[90] flex flex-col items-center gap-2 px-3 sm:top-5 sm:right-5 sm:left-auto sm:items-end" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border border-ink-100 bg-white/95 p-3.5 shadow-lift backdrop-blur"
        role="status"
      >
        <span class="grid h-8 w-8 shrink-0 place-items-center rounded-xl" :class="tones[toast.type]">
          <component :is="icons[toast.type]" class="h-4.5 w-4.5" />
        </span>
        <div class="min-w-0 flex-1 pt-0.5">
          <p v-if="toast.title" class="text-sm font-bold text-ink-900">{{ toast.title }}</p>
          <p class="text-sm leading-snug text-ink-600">{{ toast.message }}</p>
        </div>
        <button class="rounded-lg p-1 text-ink-300 hover:bg-ink-50 hover:text-ink-700" aria-label="Dismiss" @click="dismiss(toast.id)">
          <X class="h-4 w-4" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
