<script setup>
import { Minus, Plus, Trash2 } from 'lucide-vue-next'

defineProps({
  quantity: { type: Number, required: true },
  size: { type: String, default: 'md' },
  max: { type: Number, default: 50 },
})
const emit = defineEmits(['increase', 'decrease'])
</script>

<template>
  <div class="inline-flex items-center rounded-full bg-ink-900 text-white shadow-md" :class="size === 'lg' ? 'p-1.5' : 'p-1'">
    <button
      class="grid place-items-center rounded-full transition hover:bg-white/15 active:scale-90"
      :class="size === 'lg' ? 'h-10 w-10' : 'h-7 w-7'"
      :aria-label="quantity === 1 ? 'Remove item' : 'Decrease quantity'"
      @click.stop="emit('decrease')"
    >
      <Trash2 v-if="quantity === 1" :class="size === 'lg' ? 'h-4.5 w-4.5' : 'h-3.5 w-3.5'" />
      <Minus v-else :class="size === 'lg' ? 'h-4.5 w-4.5' : 'h-3.5 w-3.5'" />
    </button>
    <span class="min-w-7 text-center font-extrabold tabular-nums" :class="size === 'lg' ? 'text-lg' : 'text-sm'">{{ quantity }}</span>
    <button
      class="grid place-items-center rounded-full transition hover:bg-white/15 active:scale-90 disabled:opacity-40"
      :class="size === 'lg' ? 'h-10 w-10' : 'h-7 w-7'"
      :disabled="quantity >= max"
      aria-label="Increase quantity"
      @click.stop="emit('increase')"
    >
      <Plus :class="size === 'lg' ? 'h-4.5 w-4.5' : 'h-3.5 w-3.5'" />
    </button>
  </div>
</template>
