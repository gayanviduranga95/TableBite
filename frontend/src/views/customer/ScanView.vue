<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, Hash, LoaderCircle, QrCode, TriangleAlert } from 'lucide-vue-next'
import { getTable, lookupTable } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'
import { useSettings } from '../../stores/settingsStore'
import { useToast } from '../../composables/useToast'
import { extractTableToken } from '../../utils/qr'
import QrScanner from '../../components/QrScanner.vue'

const router = useRouter()
const cart = useOrderStore()
const settings = useSettings()
const toast = useToast()
const scanner = ref(null)
const status = ref('idle') // idle | checking | found | invalid
const message = ref('')
const found = ref(null)
const manualNumber = ref('')
const manualError = ref('')
const manualBusy = ref(false)
let lastText = ''
let lastAt = 0

function select(table) {
  found.value = table
  status.value = 'found'
  cart.setTable(table)
  scanner.value?.stop()
  toast.success(`You're ordering for Table ${table.tableNumber}`)
  setTimeout(() => router.push('/menu'), 900)
}

async function onDetected(text) {
  if (status.value === 'checking' || status.value === 'found') return
  // Avoid hammering the server with the same unreadable code every frame.
  if (text && text === lastText && Date.now() - lastAt < 3000) return
  lastText = text
  lastAt = Date.now()
  const token = extractTableToken(text)
  if (!token) {
    status.value = 'invalid'
    message.value = text
      ? "That QR code isn't a TableBite table code. Please scan the code on your table."
      : "We couldn't find a QR code in that photo. Try again with the code in focus."
    return
  }
  status.value = 'checking'
  try {
    const table = await getTable(token)
    if (table.status === 'OUT_OF_SERVICE') throw Object.assign(new Error(`Table ${table.tableNumber} isn't taking orders right now. Please ask our staff.`), { status: 400 })
    select(table)
  } catch (exception) {
    status.value = 'invalid'
    message.value = exception.status === 404
      ? 'This QR code is no longer valid for any table. Please ask a member of staff.'
      : exception.message
  }
}

async function submitManual() {
  manualError.value = ''
  if (!manualNumber.value.trim()) {
    manualError.value = 'Enter the table number shown on your table'
    return
  }
  manualBusy.value = true
  try {
    select(await lookupTable(manualNumber.value.trim()))
  } catch (exception) {
    manualError.value = exception.message
  } finally {
    manualBusy.value = false
  }
}
</script>

<template>
  <div class="container-app py-8 sm:py-12">
    <div class="mx-auto max-w-lg text-center">
      <span class="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-brand-500 text-white shadow-brand"><QrCode class="h-7 w-7" /></span>
      <h1 class="mt-4 font-display text-4xl font-normal sm:text-5xl">Scan your table</h1>
      <p class="mt-2 text-ink-500">Point your camera at the QR code on your table. We'll set your table number automatically.</p>
    </div>

    <div class="mx-auto mt-8 max-w-lg">
      <QrScanner ref="scanner" @detected="onDetected" />

      <Transition name="fade" mode="out-in">
        <div v-if="status === 'checking'" key="c" class="mt-5 flex items-center justify-center gap-2 rounded-2xl bg-white p-4 font-semibold shadow-card">
          <LoaderCircle class="h-5 w-5 animate-spin text-brand-500" /> Checking your table…
        </div>
        <div v-else-if="status === 'found'" key="f" class="mt-5 flex items-center gap-3 rounded-2xl bg-leaf-50 p-4 text-leaf-500 ring-1 ring-emerald-200">
          <CircleCheck class="h-6 w-6" />
          <p class="font-bold text-emerald-800">Table {{ found.tableNumber }} found! Opening the menu…</p>
        </div>
        <div v-else-if="status === 'invalid'" key="i" class="mt-5 flex items-start gap-3 rounded-2xl bg-red-50 p-4 ring-1 ring-red-200" role="alert">
          <TriangleAlert class="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
          <div class="text-left">
            <p class="font-bold text-red-800">Invalid QR code</p>
            <p class="text-sm text-red-700">{{ message }}</p>
          </div>
        </div>
      </Transition>

      <!-- Manual fallback -->
      <div v-if="settings.manualTables" class="card-pad mt-8">
        <div class="flex items-center gap-3">
          <span class="grid h-10 w-10 place-items-center rounded-xl bg-ink-100 text-ink-700"><Hash class="h-5 w-5" /></span>
          <div>
            <h2 class="font-extrabold">Camera not working?</h2>
            <p class="text-sm text-ink-500">Enter the number printed on your table instead.</p>
          </div>
        </div>
        <form class="mt-4 flex gap-2" @submit.prevent="submitManual">
          <input v-model="manualNumber" class="input flex-1 uppercase" :class="{ 'input-error': manualError }" placeholder="e.g. T05" maxlength="12" aria-label="Table number">
          <button class="btn-dark" :disabled="manualBusy">
            <LoaderCircle v-if="manualBusy" class="h-4 w-4 animate-spin" /> Continue
          </button>
        </form>
        <p v-if="manualError" class="field-error">{{ manualError }}</p>
      </div>
      <p class="mt-6 text-center text-sm text-ink-500">
        Ordering takeaway instead? <RouterLink to="/menu" class="font-bold text-brand-600">Browse the menu</RouterLink>
      </p>
    </div>
  </div>
</template>
