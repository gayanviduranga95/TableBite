<script setup>
import { nextTick, ref, watch } from 'vue'
import { TriangleAlert, CircleCheck } from 'lucide-vue-next'
import { useConfirm } from '../composables/useConfirm'

const { dialog, close } = useConfirm()
const inputRef = ref(null)
const confirmRef = ref(null)

watch(() => dialog.open, async (open) => {
  if (!open) return
  await nextTick()
  ;(inputRef.value || confirmRef.value)?.focus()
})
</script>

<template>
  <Transition name="fade">
    <div v-if="dialog.open" class="fixed inset-0 z-[80] grid place-items-end bg-ink-900/50 p-0 backdrop-blur-sm sm:place-items-center sm:p-4" @click.self="close(false)" @keydown.esc="close(false)">
      <div class="w-full max-w-md animate-rise rounded-t-3xl bg-white p-6 shadow-lift sm:rounded-3xl" role="alertdialog" aria-modal="true" :aria-label="dialog.title">
        <div class="flex items-start gap-4">
          <span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl" :class="dialog.danger ? 'bg-red-50 text-red-600' : 'bg-brand-50 text-brand-600'">
            <TriangleAlert v-if="dialog.danger" class="h-5 w-5" />
            <CircleCheck v-else class="h-5 w-5" />
          </span>
          <div class="min-w-0 flex-1">
            <h2 class="text-lg font-extrabold">{{ dialog.title }}</h2>
            <p v-if="dialog.message" class="mt-1 text-sm leading-relaxed text-ink-500">{{ dialog.message }}</p>
          </div>
        </div>
        <label v-if="dialog.input" class="mt-5 block">
          <span class="field-label">{{ dialog.inputLabel || 'Reason (optional)' }}</span>
          <input ref="inputRef" v-model="dialog.inputValue" class="input" maxlength="255" @keydown.enter="close(true)">
        </label>
        <div class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button class="btn-light" @click="close(false)">{{ dialog.cancelText }}</button>
          <button ref="confirmRef" :class="dialog.danger ? 'btn-danger' : 'btn-primary'" @click="close(true)">{{ dialog.confirmText }}</button>
        </div>
      </div>
    </div>
  </Transition>
</template>
