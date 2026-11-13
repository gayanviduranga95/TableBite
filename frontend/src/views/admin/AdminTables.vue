<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Armchair, Copy, Download, ExternalLink, History, LoaderCircle, Pencil, Plus, Printer,
  RefreshCw, Trash2, TriangleAlert,
} from 'lucide-vue-next'
import * as service from '../../services/orderService'
import { useSettings } from '../../stores/settingsStore'
import { useToast } from '../../composables/useToast'
import { useConfirm } from '../../composables/useConfirm'
import { downloadDataUrl, qrDataUrl, tableCardPng, tableUrl } from '../../utils/qr'
import { dateTime, money } from '../../utils/formatters'
import BaseModal from '../../components/BaseModal.vue'
import EmptyState from '../../components/EmptyState.vue'
import StatusBadge from '../../components/StatusBadge.vue'

const settings = useSettings()
const toast = useToast()
const { confirm } = useConfirm()
const tables = ref([])
const qr = ref({})
const loading = ref(true)

const STATUSES = { AVAILABLE: 'Available', OCCUPIED: 'Occupied', RESERVED: 'Reserved', OUT_OF_SERVICE: 'Out of service' }
const tone = {
  AVAILABLE: 'bg-emerald-50 text-emerald-700', OCCUPIED: 'bg-brand-50 text-brand-700',
  RESERVED: 'bg-sky-50 text-sky-700', OUT_OF_SERVICE: 'bg-stone-100 text-stone-500',
}
const localOnly = computed(() => /\/\/(localhost|127\.0\.0\.1)(:|\/|$)/.test(settings.qrBaseUrl))

const urlFor = (table) => tableUrl(settings.qrBaseUrl, table.qrToken)

async function renderQr() {
  const entries = await Promise.all(tables.value.map(async (t) => [t.id, await qrDataUrl(urlFor(t), 320)]))
  qr.value = Object.fromEntries(entries)
}

async function load() {
  try {
    tables.value = await service.getTables()
    await renderQr()
  } catch (exception) {
    toast.error(exception)
  } finally {
    loading.value = false
  }
}
watch(() => settings.qrBaseUrl, renderQr)
onMounted(async () => { await settings.load(); load() })

// ---------- edit
const modal = ref(false)
const saving = ref(false)
const formError = ref('')
const form = reactive({ id: null, tableNumber: '', seats: 4, status: 'AVAILABLE', location: '' })
function openForm(table) {
  Object.assign(form, { id: null, tableNumber: '', seats: 4, status: 'AVAILABLE', location: '' }, table || {})
  formError.value = ''
  modal.value = true
}
async function save() {
  saving.value = true
  formError.value = ''
  try {
    if (form.id) await service.updateTable(form.id, form)
    else await service.createTable(form)
    toast.success(form.id ? 'Table updated' : `Table ${form.tableNumber.toUpperCase()} created with a new QR code`)
    modal.value = false
    await load()
  } catch (exception) {
    formError.value = exception.message
  } finally {
    saving.value = false
  }
}

async function setStatus(table, status) {
  try {
    await service.updateTable(table.id, { ...table, status })
    table.status = status
    toast.success(`Table ${table.tableNumber} is now ${STATUSES[status].toLowerCase()}`)
  } catch (exception) {
    toast.error(exception)
  }
}

async function regenerate(table) {
  if (!(await confirm({ title: `New QR code for ${table.tableNumber}?`, message: 'The current printed code will stop working immediately. Reprint the new code afterwards.', confirmText: 'Generate new code', danger: true }))) return
  try {
    await service.regenerateTableToken(table.id)
    toast.success('New QR code generated — remember to reprint it')
    await load()
  } catch (exception) {
    toast.error(exception)
  }
}

async function remove(table) {
  if (!(await confirm({ title: `Delete table ${table.tableNumber}?`, message: 'Its QR code stops working. Order history is kept.', confirmText: 'Delete table', danger: true }))) return
  try {
    await service.deleteTable(table.id)
    toast.success('Table deleted')
    await load()
  } catch (exception) {
    toast.error(exception)
  }
}

async function download(table) {
  try {
    const png = await tableCardPng({ restaurant: settings.state.restaurant_name, tableNumber: table.tableNumber, url: urlFor(table) })
    downloadDataUrl(png, `table-${table.tableNumber}-qr.png`)
  } catch (exception) {
    toast.error('Could not create the QR image')
  }
}

async function copyLink(table) {
  try {
    await navigator.clipboard.writeText(urlFor(table))
    toast.success('Table link copied')
  } catch {
    toast.info(urlFor(table), { title: 'Table link' })
  }
}

// ---------- orders per table
const ordersFor = ref(null)
const tableOrders = ref([])
const ordersLoading = ref(false)
async function showOrders(table) {
  ordersFor.value = table
  ordersLoading.value = true
  try { tableOrders.value = await service.getTableOrders(table.id) } catch (exception) { toast.error(exception) } finally { ordersLoading.value = false }
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <p class="eyebrow">Dining room</p>
        <h1 class="mt-1 text-3xl font-extrabold">Tables & QR codes</h1>
        <p class="text-sm text-ink-500">Each table has its own secure QR code. Guests scan it and their orders are linked to the table automatically.</p>
      </div>
      <div class="flex gap-2">
        <RouterLink to="/admin/tables/print" target="_blank" class="btn-light"><Printer class="h-4 w-4" /> Print all</RouterLink>
        <button class="btn-primary" @click="openForm()"><Plus class="h-4 w-4" /> Add table</button>
      </div>
    </div>

    <div v-if="localOnly" class="mt-5 flex gap-3 rounded-2xl bg-amber-50 p-4 text-sm text-amber-900 ring-1 ring-amber-200">
      <TriangleAlert class="h-5 w-5 shrink-0 text-amber-600" />
      <p>QR codes currently point to <strong>{{ settings.qrBaseUrl }}</strong>, which phones can't open. Set the <RouterLink to="/admin/settings" class="font-bold underline">public ordering URL</RouterLink> (for example <code>http://192.168.1.20:5173</code>) before printing.</p>
    </div>

    <div v-if="loading" class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><div v-for="n in 8" :key="n" class="skeleton h-80 rounded-3xl" /></div>
    <EmptyState v-else-if="!tables.length" class="mt-6" :icon="Armchair" title="No tables yet" message="Add your first table to generate its QR code."><button class="btn-primary" @click="openForm()">Add table</button></EmptyState>

    <div v-else class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <article v-for="table in tables" :key="table.id" class="card flex flex-col p-4">
        <div class="flex items-start justify-between">
          <div>
            <h2 class="text-2xl font-extrabold">{{ table.tableNumber }}</h2>
            <p class="text-xs text-ink-500">{{ table.seats }} seats<span v-if="table.location"> · {{ table.location }}</span></p>
          </div>
          <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="tone[table.status]">{{ STATUSES[table.status] }}</span>
        </div>
        <div class="mx-auto mt-3 w-full max-w-[200px] rounded-2xl border border-ink-100 bg-white p-2">
          <img v-if="qr[table.id]" :src="qr[table.id]" :alt="`QR code for table ${table.tableNumber}`" class="aspect-square w-full">
          <div v-else class="skeleton aspect-square" />
        </div>
        <p class="mt-2 truncate text-center text-[11px] text-ink-400" :title="urlFor(table)">{{ urlFor(table) }}</p>
        <button v-if="table.activeOrders" class="mt-2 text-xs font-bold text-brand-600" @click="showOrders(table)">{{ table.activeOrders }} active order{{ table.activeOrders > 1 ? 's' : '' }} →</button>

        <select :value="table.status" class="input mt-3 py-2 text-xs" aria-label="Table status" @change="setStatus(table, $event.target.value)">
          <option v-for="(label, value) in STATUSES" :key="value" :value="value">{{ label }}</option>
        </select>
        <div class="mt-3 grid grid-cols-2 gap-2">
          <button class="btn-dark btn-sm" @click="download(table)"><Download class="h-3.5 w-3.5" /> PNG</button>
          <RouterLink :to="{ path: '/admin/tables/print', query: { ids: table.id } }" target="_blank" class="btn-light btn-sm"><Printer class="h-3.5 w-3.5" /> Print</RouterLink>
        </div>
        <div class="mt-2 flex justify-between border-t border-ink-100 pt-2">
          <button class="btn-ghost btn-icon" title="Copy link" aria-label="Copy link" @click="copyLink(table)"><Copy class="h-4 w-4" /></button>
          <a :href="urlFor(table)" target="_blank" rel="noopener" class="btn-ghost btn-icon" title="Open as customer" aria-label="Open as customer"><ExternalLink class="h-4 w-4" /></a>
          <button class="btn-ghost btn-icon" title="Order history" aria-label="Order history" @click="showOrders(table)"><History class="h-4 w-4" /></button>
          <button class="btn-ghost btn-icon" title="New QR code" aria-label="Generate new QR code" @click="regenerate(table)"><RefreshCw class="h-4 w-4" /></button>
          <button class="btn-ghost btn-icon" title="Edit" aria-label="Edit" @click="openForm(table)"><Pencil class="h-4 w-4" /></button>
          <button class="btn-ghost btn-icon text-red-600 hover:bg-red-50" title="Delete" aria-label="Delete" @click="remove(table)"><Trash2 class="h-4 w-4" /></button>
        </div>
      </article>
    </div>

    <BaseModal :open="modal" :title="form.id ? `Edit table ${form.tableNumber}` : 'Add a table'" @close="modal = false">
      <form id="table-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="save">
        <label><span class="field-label">Table number</span><input v-model="form.tableNumber" class="input uppercase" required maxlength="12" placeholder="T09"></label>
        <label><span class="field-label">Seats</span><input v-model.number="form.seats" type="number" min="1" max="50" class="input"></label>
        <label><span class="field-label">Location</span><input v-model="form.location" class="input" maxlength="60" placeholder="Main hall, Terrace…"></label>
        <label><span class="field-label">Status</span><select v-model="form.status" class="input"><option v-for="(label, value) in STATUSES" :key="value" :value="value">{{ label }}</option></select></label>
        <p v-if="!form.id" class="text-xs text-ink-500 sm:col-span-2">A secure, unguessable QR token is generated automatically.</p>
        <p v-if="formError" class="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700 sm:col-span-2">{{ formError }}</p>
      </form>
      <template #footer>
        <button class="btn-light" @click="modal = false">Cancel</button>
        <button class="btn-primary" form="table-form" :disabled="saving"><LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" /> Save table</button>
      </template>
    </BaseModal>

    <BaseModal :open="!!ordersFor" :title="`Orders · Table ${ordersFor?.tableNumber || ''}`" width="max-w-xl" @close="ordersFor = null">
      <div v-if="ordersLoading" class="grid place-items-center py-10"><LoaderCircle class="h-6 w-6 animate-spin text-brand-500" /></div>
      <p v-else-if="!tableOrders.length" class="py-8 text-center text-sm text-ink-500">No orders for this table yet.</p>
      <ul v-else class="divide-y divide-ink-100">
        <li v-for="order in tableOrders" :key="order.id" class="flex items-center gap-3 py-3">
          <div class="flex-1">
            <p class="font-bold">{{ order.orderNumber }}</p>
            <p class="text-xs text-ink-500">{{ dateTime(order.createdAt) }} · {{ order.items.map((i) => `${i.quantity}× ${i.name}`).join(', ') }}</p>
          </div>
          <div class="text-right"><StatusBadge :status="order.status" size="sm" /><p class="mt-1 text-xs font-bold">{{ money(order.totalAmount) }}</p></div>
        </li>
      </ul>
    </BaseModal>
  </div>
</template>
