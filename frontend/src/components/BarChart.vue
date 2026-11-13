<script setup>
// Single-series vertical bar chart (SVG). Thin bars with rounded data-ends anchored to the
// baseline, recessive grid, per-bar hover tooltip and a screen-reader table.
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  data: { type: Array, required: true }, // [{ label, value, sub? }]
  format: { type: Function, default: (v) => String(v) },
  axisFormat: { type: Function, default: (v) => String(v) },
  height: { type: Number, default: 240 },
  title: { type: String, default: 'Chart' },
})

const root = ref(null)
const width = ref(720)
let observer = null
onMounted(() => {
  observer = new ResizeObserver(([entry]) => { width.value = Math.max(240, Math.round(entry.contentRect.width)) })
  observer.observe(root.value)
})
onBeforeUnmount(() => observer?.disconnect())
const PAD = { top: 12, right: 8, bottom: 28, left: 48 }
const hover = ref(-1)

const max = computed(() => {
  const raw = Math.max(1, ...props.data.map((d) => d.value))
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => raw / s <= 4) || magnitude * 10
  return Math.ceil(raw / step) * step
})
const ticks = computed(() => [0, 0.25, 0.5, 0.75, 1].map((f) => f * max.value))
const plotW = computed(() => width.value - PAD.left - PAD.right)
const plotH = computed(() => props.height - PAD.top - PAD.bottom)
const slot = computed(() => plotW.value / Math.max(1, props.data.length))
const barW = computed(() => Math.max(4, Math.min(28, slot.value - 2 - slot.value * 0.35)))
const y = (v) => PAD.top + plotH.value - (v / max.value) * plotH.value
// Leave ~56px per x-axis label so labels never collide.
const labelEvery = computed(() => Math.max(1, Math.ceil(56 / slot.value)))

function barPath(i, value) {
  const x = PAD.left + i * slot.value + (slot.value - barW.value) / 2
  const top = y(value)
  const bottom = PAD.top + plotH.value
  const h = bottom - top
  if (h <= 0) return ''
  const r = Math.min(4, barW.value / 2, h)
  return `M${x},${bottom} L${x},${top + r} Q${x},${top} ${x + r},${top} L${x + barW.value - r},${top} Q${x + barW.value},${top} ${x + barW.value},${top + r} L${x + barW.value},${bottom} Z`
}

const tip = computed(() => {
  if (hover.value < 0) return null
  const d = props.data[hover.value]
  const left = ((PAD.left + hover.value * slot.value + slot.value / 2) / width.value) * 100
  return { d, left, top: (y(d.value) / props.height) * 100 }
})
</script>

<template>
  <div ref="root" class="relative">
    <svg :viewBox="`0 0 ${width} ${height}`" :width="width" :height="height" class="h-auto w-full overflow-visible" role="img" :aria-label="title" @mouseleave="hover = -1">
      <g>
        <template v-for="t in ticks" :key="t">
          <line :x1="PAD.left" :x2="width - PAD.right" :y1="y(t)" :y2="y(t)" :stroke="t === 0 ? '#ddd2c9' : '#efe9e4'" stroke-width="1" :stroke-dasharray="t === 0 ? '' : '3 4'" />
          <text :x="PAD.left - 8" :y="y(t) + 4" text-anchor="end" class="fill-ink-400 text-[11px] font-semibold">{{ axisFormat(t) }}</text>
        </template>
      </g>
      <g v-for="(d, i) in data" :key="d.label">
        <path :d="barPath(i, d.value)" :fill="hover === i ? '#bc3d0d' : '#e3520d'" class="transition-colors" />
        <!-- Hit target wider/taller than the mark -->
        <rect :x="PAD.left + i * slot" :y="PAD.top" :width="slot" :height="plotH" fill="transparent" @mouseenter="hover = i" @focus="hover = i" @blur="hover = -1" tabindex="0" :aria-label="`${d.label}: ${format(d.value)}`" />
        <text v-if="i % labelEvery === 0" :x="PAD.left + i * slot + slot / 2" :y="height - 8" text-anchor="middle" class="fill-ink-400 text-[11px] font-semibold">{{ d.label }}</text>
      </g>
    </svg>
    <div
      v-if="tip"
      class="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-[calc(100%+10px)] rounded-xl bg-ink-900 px-3 py-2 text-xs whitespace-nowrap text-white shadow-lift"
      :style="{ left: `${tip.left}%`, top: `${tip.top}%` }"
    >
      <p class="font-semibold text-ink-300">{{ tip.d.label }}</p>
      <p class="text-sm font-extrabold">{{ format(tip.d.value) }}</p>
      <p v-if="tip.d.sub" class="text-ink-300">{{ tip.d.sub }}</p>
    </div>
    <table class="sr-only">
      <caption>{{ title }}</caption>
      <tbody><tr v-for="d in data" :key="d.label"><th>{{ d.label }}</th><td>{{ format(d.value) }}</td></tr></tbody>
    </table>
  </div>
</template>
