<script setup>
// Landing page for table QR codes: /table/<secure-token>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCheck, LoaderCircle, QrCode, ScanLine } from 'lucide-vue-next'
import { getTable } from '../../services/orderService'
import { useOrderStore } from '../../stores/orderStore'
import { useToast } from '../../composables/useToast'

const route = useRoute()
const router = useRouter()
const cart = useOrderStore()
const toast = useToast()
const state = ref('loading')
const message = ref('')
const table = ref(null)

onMounted(async () => {
  try {
    table.value = await getTable(String(route.params.token))
    if (table.value.status === 'OUT_OF_SERVICE') {
      state.value = 'error'
      message.value = `Table ${table.value.tableNumber} is not taking orders right now. Please ask a member of staff.`
      return
    }
    cart.setTable(table.value)
    state.value = 'ok'
    toast.success(`Welcome! You're ordering for Table ${table.value.tableNumber}`)
    setTimeout(() => router.replace('/menu'), 900)
  } catch (exception) {
    state.value = 'error'
    message.value = exception.status === 404
      ? "This QR code isn't linked to a table. It may be old or damaged — try scanning again or pick your table manually."
      : exception.message
  }
})
</script>

<template>
  <div class="container-app grid min-h-[70vh] place-items-center py-12">
    <div class="w-full max-w-md text-center">
      <template v-if="state === 'loading'">
        <span class="mx-auto grid h-20 w-20 place-items-center rounded-3xl bg-brand-50 text-brand-500"><LoaderCircle class="h-9 w-9 animate-spin" /></span>
        <h1 class="mt-6 text-2xl font-extrabold">Finding your table…</h1>
        <p class="mt-2 text-ink-500">Just a moment while we set up your menu.</p>
      </template>
      <template v-else-if="state === 'ok'">
        <span class="mx-auto grid h-20 w-20 animate-pop place-items-center rounded-3xl bg-leaf-500 text-white shadow-lg"><CircleCheck class="h-10 w-10" /></span>
        <p class="eyebrow mt-6">Table found</p>
        <h1 class="mt-1 font-display text-5xl font-normal">Table {{ table.tableNumber }}</h1>
        <p class="mt-2 text-ink-500">Opening the menu…</p>
      </template>
      <template v-else>
        <span class="mx-auto grid h-20 w-20 place-items-center rounded-3xl bg-red-50 text-red-500"><QrCode class="h-9 w-9" /></span>
        <h1 class="mt-6 text-2xl font-extrabold">We couldn't recognise this table</h1>
        <p class="mt-2 text-ink-500">{{ message }}</p>
        <div class="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
          <RouterLink to="/scan" class="btn-primary"><ScanLine class="h-4 w-4" /> Scan again</RouterLink>
          <RouterLink to="/menu" class="btn-light">Just browse the menu</RouterLink>
        </div>
      </template>
    </div>
  </div>
</template>
