<script setup>
import { computed } from 'vue'
import { DOT_CLASSES, STATUS, TONE_CLASSES } from '../utils/status'

const props = defineProps({
  status: { type: String, required: true },
  customer: { type: Boolean, default: false },
  size: { type: String, default: 'md' },
})
const meta = computed(() => STATUS[props.status] || { label: props.status, customer: props.status, tone: 'stone' })
const live = computed(() => ['PENDING', 'CONFIRMED', 'PREPARING', 'READY'].includes(props.status))
</script>

<template>
  <span
    class="inline-flex items-center gap-1.5 rounded-full font-bold whitespace-nowrap ring-1 ring-inset"
    :class="[TONE_CLASSES[meta.tone], size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs']"
  >
    <span class="relative flex h-1.5 w-1.5">
      <span v-if="live" class="absolute inline-flex h-full w-full animate-ping rounded-full opacity-60" :class="DOT_CLASSES[meta.tone]" />
      <span class="relative inline-flex h-1.5 w-1.5 rounded-full" :class="DOT_CLASSES[meta.tone]" />
    </span>
    {{ customer ? meta.customer : meta.label }}
  </span>
</template>
