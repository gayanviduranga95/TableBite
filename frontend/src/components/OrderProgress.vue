<script setup>
import { computed } from 'vue'
import { Check, ChefHat, ClipboardList, BellRing, UtensilsCrossed, PartyPopper } from 'lucide-vue-next'
import { progressSteps, STATUS } from '../utils/status'

const props = defineProps({
  status: { type: String, required: true },
  orderType: { type: String, default: 'DINE_IN' },
})
const steps = computed(() => progressSteps(props.orderType))
const icons = { PENDING: ClipboardList, CONFIRMED: Check, PREPARING: ChefHat, READY: BellRing, SERVED: UtensilsCrossed, COMPLETED: PartyPopper }
const current = computed(() => {
  if (props.status === 'COMPLETED') return steps.value.length - 1
  return steps.value.indexOf(props.status)
})
const percent = computed(() => Math.max(0, current.value) / (steps.value.length - 1) * 100)
</script>

<template>
  <div class="relative">
    <!-- Tablet / desktop: horizontal -->
    <div class="relative hidden sm:block">
      <div class="absolute top-6 right-[10%] left-[10%] h-1 rounded-full bg-ink-100">
        <div class="h-full rounded-full bg-gradient-to-r from-brand-400 to-brand-600 transition-all duration-700" :style="{ width: `${percent}%` }" />
      </div>
      <ol class="relative grid" :style="{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }">
        <li v-for="(step, index) in steps" :key="step" class="flex flex-col items-center text-center">
          <span
            class="grid h-12 w-12 place-items-center rounded-2xl border-2 transition-all duration-500"
            :class="index < current ? 'border-brand-500 bg-brand-500 text-white'
              : index === current ? 'animate-pulse-ring border-brand-500 bg-white text-brand-600'
              : 'border-ink-100 bg-white text-ink-300'"
          >
            <Check v-if="index < current" class="h-5 w-5" />
            <component :is="icons[step]" v-else class="h-5 w-5" />
          </span>
          <span class="mt-2 text-xs font-bold" :class="index <= current ? 'text-ink-900' : 'text-ink-400'">{{ STATUS[step].customer }}</span>
        </li>
      </ol>
    </div>
    <!-- Mobile: vertical -->
    <ol class="sm:hidden">
      <li v-for="(step, index) in steps" :key="step" class="relative flex gap-3 pb-5 last:pb-0">
        <span v-if="index < steps.length - 1" class="absolute top-10 left-[19px] h-[calc(100%-2.5rem)] w-0.5" :class="index < current ? 'bg-brand-500' : 'bg-ink-100'" />
        <span
          class="relative grid h-10 w-10 shrink-0 place-items-center rounded-xl border-2"
          :class="index < current ? 'border-brand-500 bg-brand-500 text-white'
            : index === current ? 'animate-pulse-ring border-brand-500 bg-white text-brand-600'
            : 'border-ink-100 bg-white text-ink-300'"
        >
          <Check v-if="index < current" class="h-4.5 w-4.5" />
          <component :is="icons[step]" v-else class="h-4.5 w-4.5" />
        </span>
        <div class="pt-1">
          <p class="text-sm font-bold" :class="index <= current ? 'text-ink-900' : 'text-ink-400'">{{ STATUS[step].customer }}</p>
          <p v-if="index === current" class="text-xs text-ink-500">{{ STATUS[step].description }}</p>
        </div>
      </li>
    </ol>
  </div>
</template>
