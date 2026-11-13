<script setup>
import { computed } from 'vue'
import {
  CakeSlice, Coffee, CupSoda, Drumstick, EggFried, Hamburger, IceCreamCone, LayoutGrid,
  Pizza, Salad, Sandwich, Soup, Sparkles, Star, UtensilsCrossed, Wheat,
} from 'lucide-vue-next'

const props = defineProps({ icon: { type: String, default: '' }, name: { type: String, default: '' } })

const ICONS = {
  all: LayoutGrid, starter: Drumstick, burger: Hamburger, pizza: Pizza, rice: Wheat, noodles: Soup,
  curry: Soup, salad: Salad, dessert: CakeSlice, drink: CupSoda, coffee: Coffee, icecream: IceCreamCone,
  sandwich: Sandwich, breakfast: EggFried, star: Star, special: Sparkles,
}

const component = computed(() => {
  if (ICONS[props.icon]) return ICONS[props.icon]
  const name = props.name.toLowerCase()
  const key = Object.keys(ICONS).find((k) => name.includes(k))
  return key ? ICONS[key] : UtensilsCrossed
})
</script>

<template>
  <component :is="component" />
</template>
