<script setup>
import { ref } from 'vue'
import { Eye, EyeOff } from 'lucide-vue-next'

defineProps({ invalid: Boolean, autocomplete: { type: String, default: 'current-password' }, id: String })
const model = defineModel({ type: String, default: '' })
const visible = ref(false)
</script>

<template>
  <div class="relative">
    <input :id="id" v-model="model" :type="visible ? 'text' : 'password'" class="input pr-11" :class="{ 'input-error': invalid }" :autocomplete="autocomplete" maxlength="128">
    <button type="button" class="absolute inset-y-0 right-0 grid w-11 place-items-center text-ink-400 hover:text-ink-700" :aria-label="visible ? 'Hide password' : 'Show password'" @click="visible = !visible">
      <EyeOff v-if="visible" class="h-4.5 w-4.5" /><Eye v-else class="h-4.5 w-4.5" />
    </button>
  </div>
</template>
