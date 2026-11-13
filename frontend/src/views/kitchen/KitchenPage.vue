<script setup>
// Kitchen entry point: unlocks with the kitchen code (no account needed).
// Admins / kitchen accounts that are already signed in go straight in.
import { computed, ref } from 'vue'
import { ChefHat, LoaderCircle, LockKeyhole } from 'lucide-vue-next'
import { getKitchenOrders } from '../../services/orderService'
import { getKitchenCode, setKitchenCode } from '../../services/api'
import { useAuth } from '../../stores/authStore'
import KitchenDashboard from './KitchenDashboard.vue'

const auth = useAuth()
const hasAccount = computed(() => ['ADMIN', 'KITCHEN'].includes(auth.role.value))
const unlocked = ref(hasAccount.value || !!getKitchenCode())
const code = ref('')
const error = ref('')
const checking = ref(false)

async function unlock() {
  error.value = ''
  if (!code.value.trim()) {
    error.value = 'Enter the kitchen code'
    return
  }
  checking.value = true
  setKitchenCode(code.value.trim())
  try {
    await getKitchenOrders() // the server checks the code
    unlocked.value = true
    code.value = ''
  } catch (exception) {
    setKitchenCode('')
    error.value = exception.status === 401 ? 'That kitchen code is not correct' : exception.message
  } finally {
    checking.value = false
  }
}

function lock(message) {
  unlocked.value = false
  error.value = typeof message === 'string' ? message : ''
}
</script>

<template>
  <KitchenDashboard v-if="unlocked" @locked="lock" />

  <div v-else class="grid min-h-screen place-items-center bg-[#120d0a] p-6 text-white">
    <form class="w-full max-w-sm animate-rise text-center" @submit.prevent="unlock">
      <span class="mx-auto grid h-16 w-16 place-items-center rounded-3xl bg-brand-500 shadow-brand"><ChefHat class="h-8 w-8" /></span>
      <h1 class="mt-6 text-3xl font-extrabold text-white">Kitchen display</h1>
      <p class="mt-2 text-ink-300">Enter the kitchen code to see incoming orders.</p>
      <label class="mt-8 block text-left">
        <span class="mb-1.5 block text-xs font-bold tracking-wide text-ink-300 uppercase">Kitchen code</span>
        <span class="flex items-center gap-2 rounded-2xl border border-white/15 bg-white/5 px-4 focus-within:border-brand-400">
          <LockKeyhole class="h-5 w-5 text-ink-400" />
          <input v-model="code" type="password" inputmode="text" autocomplete="off" autofocus class="w-full bg-transparent py-3.5 text-lg tracking-widest text-white outline-none" aria-label="Kitchen code">
        </span>
      </label>
      <p v-if="error" class="mt-3 rounded-xl bg-red-500/15 p-3 text-sm font-semibold text-red-300" role="alert">{{ error }}</p>
      <button class="btn-primary btn-lg mt-5 w-full" :disabled="checking">
        <LoaderCircle v-if="checking" class="h-5 w-5 animate-spin" /> Open kitchen
      </button>
    </form>
  </div>
</template>
