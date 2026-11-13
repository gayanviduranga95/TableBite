<script setup>
// Print-ready sheet of table QR cards (2 per A4 row). Opens the print dialog automatically.
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { LoaderCircle, Printer } from 'lucide-vue-next'
import { getTables } from '../../services/orderService'
import { useSettings } from '../../stores/settingsStore'
import { qrDataUrl, tableUrl } from '../../utils/qr'
import BrandMark from '../../components/BrandMark.vue'

const route = useRoute()
const settings = useSettings()
const cards = ref([])
const loading = ref(true)
const error = ref('')
const print = () => window.print()

onMounted(async () => {
  try {
    await settings.load()
    const ids = String(route.query.ids || '').split(',').filter(Boolean).map(Number)
    const tables = (await getTables()).filter((t) => !ids.length || ids.includes(t.id))
    cards.value = await Promise.all(tables.map(async (t) => ({ ...t, url: tableUrl(settings.qrBaseUrl, t.qrToken), qr: await qrDataUrl(tableUrl(settings.qrBaseUrl, t.qrToken), 600) })))
    loading.value = false
    await nextTick()
    setTimeout(() => window.print(), 400)
  } catch (exception) {
    error.value = exception.message
    loading.value = false
  }
})
</script>

<template>
  <div class="min-h-screen bg-ink-100 print:bg-white">
    <div class="no-print sticky top-0 z-10 flex items-center justify-between gap-3 bg-white px-6 py-3 shadow-sm">
      <p class="font-bold">{{ cards.length }} table QR card{{ cards.length === 1 ? '' : 's' }} · {{ settings.qrBaseUrl }}</p>
      <button class="btn-primary" @click="print"><Printer class="h-4 w-4" /> Print</button>
    </div>
    <div v-if="loading" class="grid min-h-[60vh] place-items-center"><LoaderCircle class="h-8 w-8 animate-spin text-brand-500" /></div>
    <p v-else-if="error" class="p-10 text-center font-semibold text-red-700">{{ error }}</p>
    <div v-else class="mx-auto grid max-w-4xl grid-cols-2 gap-6 p-6 print:max-w-none print:gap-0 print:p-0">
      <section v-for="card in cards" :key="card.id" class="flex break-inside-avoid flex-col items-center rounded-3xl border-2 border-dashed border-ink-200 bg-cream p-8 text-center print:h-[13.5cm] print:rounded-none print:border-ink-300">
        <div class="flex items-center gap-2"><BrandMark size="h-8 w-8" /><span class="text-xl font-extrabold">{{ settings.state.restaurant_name }}</span></div>
        <p class="mt-2 text-xs font-extrabold tracking-[0.2em] text-brand-600 uppercase">Scan · Order · Enjoy</p>
        <img :src="card.qr" :alt="`QR code for table ${card.tableNumber}`" class="mt-4 w-48 rounded-2xl bg-white p-2">
        <p class="mt-4 text-4xl font-extrabold">Table {{ card.tableNumber }}</p>
        <p class="mt-1 text-sm text-ink-500">Point your phone camera at the code to see the menu and order.</p>
      </section>
    </div>
  </div>
</template>
