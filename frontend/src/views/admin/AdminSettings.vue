<script setup>
import { onMounted, reactive, ref } from 'vue'
import { LoaderCircle, Plus, Save, UserCog } from 'lucide-vue-next'
import * as service from '../../services/orderService'
import { useAuth } from '../../stores/authStore'
import { useSettings } from '../../stores/settingsStore'
import { useToast } from '../../composables/useToast'
import { dateTime } from '../../utils/formatters'
import BaseModal from '../../components/BaseModal.vue'
import PasswordInput from '../../components/PasswordInput.vue'

const auth = useAuth()
const settingsStore = useSettings()
const toast = useToast()
const form = reactive({})
const saving = ref(false)
const error = ref('')
const users = ref([])
const roleFilter = ref('')
const suggestedUrl = `${window.location.protocol}//${window.location.host}`

async function load() {
  try {
    Object.assign(form, await service.getAdminSettings())
    users.value = await service.getUsers(roleFilter.value || undefined)
  } catch (exception) {
    toast.error(exception)
  }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    const payload = { ...form, allow_manual_table_selection: String(form.allow_manual_table_selection), allow_takeaway: String(form.allow_takeaway) }
    Object.assign(form, await service.saveSettings(payload))
    await settingsStore.load()
    toast.success('Settings saved')
  } catch (exception) {
    error.value = exception.message
  } finally {
    saving.value = false
  }
}

// ---------- staff accounts
const modal = ref(false)
const newUser = reactive({ fullName: '', email: '', phone: '', password: '', role: 'KITCHEN' })
const userError = ref('')
async function createUser() {
  userError.value = ''
  saving.value = true
  try {
    await service.createUser(newUser)
    toast.success(`${newUser.fullName} can now sign in`)
    Object.assign(newUser, { fullName: '', email: '', phone: '', password: '', role: 'KITCHEN' })
    modal.value = false
    users.value = await service.getUsers(roleFilter.value || undefined)
  } catch (exception) {
    userError.value = exception.message
  } finally {
    saving.value = false
  }
}
async function updateAccess(user, role, active) {
  try {
    Object.assign(user, await service.updateUserAccess(user.id, role, active))
    toast.success('Access updated')
  } catch (exception) {
    toast.error(exception)
    users.value = await service.getUsers(roleFilter.value || undefined)
  }
}
async function filterUsers() {
  users.value = await service.getUsers(roleFilter.value || undefined)
}
onMounted(load)
</script>

<template>
  <div>
    <p class="eyebrow">Configuration</p>
    <h1 class="mt-1 text-3xl font-extrabold">Settings</h1>

    <form class="mt-6 grid gap-6 lg:grid-cols-2" @submit.prevent="save">
      <section class="card-pad space-y-4">
        <h2 class="font-extrabold">Restaurant</h2>
        <label class="block"><span class="field-label">Name</span><input v-model="form.restaurant_name" class="input" maxlength="200" required></label>
        <label class="block"><span class="field-label">Tagline</span><input v-model="form.tagline" class="input" maxlength="200" required></label>
        <label class="block"><span class="field-label">Address</span><input v-model="form.address" class="input" maxlength="200" required></label>
        <div class="grid gap-4 sm:grid-cols-2">
          <label class="block"><span class="field-label">Phone</span><input v-model="form.phone" class="input" maxlength="200" required></label>
          <label class="block"><span class="field-label">Opening hours</span><input v-model="form.opening_hours" class="input" maxlength="200" required></label>
        </div>
      </section>

      <section class="card-pad space-y-4">
        <h2 class="font-extrabold">Pricing & ordering</h2>
        <div class="grid gap-4 sm:grid-cols-3">
          <label class="block"><span class="field-label">Currency</span><input v-model="form.currency" class="input" maxlength="10" required></label>
          <label class="block"><span class="field-label">Tax %</span><input v-model="form.tax_rate" type="number" min="0" max="100" step="0.01" class="input" required></label>
          <label class="block"><span class="field-label">Service charge %</span><input v-model="form.service_charge_rate" type="number" min="0" max="100" step="0.01" class="input" required></label>
        </div>
        <p class="text-xs text-ink-500">Service charge applies to dine-in orders only. Totals are always recalculated on the server.</p>
        <label class="flex items-center gap-3 text-sm font-semibold"><input v-model="form.allow_takeaway" type="checkbox" true-value="true" false-value="false" class="h-4 w-4 accent-brand-500"> Accept takeaway orders</label>
        <label class="flex items-center gap-3 text-sm font-semibold"><input v-model="form.allow_manual_table_selection" type="checkbox" true-value="true" false-value="false" class="h-4 w-4 accent-brand-500"> Let guests enter their table number if they can't scan</label>
        <label class="block">
          <span class="field-label">Public ordering URL (used inside QR codes)</span>
          <input v-model="form.public_base_url" class="input" maxlength="200" placeholder="http://192.168.1.20:5173">
          <span class="mt-1 block text-xs text-ink-500">Leave empty to use the address you're on now. Phones need your computer's network address — e.g. <button type="button" class="font-bold text-brand-600" @click="form.public_base_url = suggestedUrl">{{ suggestedUrl }}</button>.</span>
        </label>
      </section>

      <div class="flex items-center gap-3 lg:col-span-2">
        <button class="btn-primary" :disabled="saving"><LoaderCircle v-if="saving" class="h-4 w-4 animate-spin" /><Save v-else class="h-4 w-4" /> Save settings</button>
        <p v-if="error" class="text-sm font-semibold text-red-600">{{ error }}</p>
      </div>
    </form>

    <section class="card mt-8 overflow-hidden">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 px-6 py-4">
        <div>
          <h2 class="flex items-center gap-2 font-extrabold"><UserCog class="h-5 w-5 text-brand-600" /> Team & accounts</h2>
          <p class="text-xs text-ink-500">Roles control access: admins manage everything, kitchen sees the kitchen display, floor staff serve and take payments.</p>
        </div>
        <div class="flex gap-2">
          <select v-model="roleFilter" class="input w-auto py-2" aria-label="Role filter" @change="filterUsers"><option value="">All roles</option><option>ADMIN</option><option>KITCHEN</option><option>WAITER</option><option>CUSTOMER</option></select>
          <button class="btn-primary" @click="modal = true"><Plus class="h-4 w-4" /> Add staff</button>
        </div>
      </div>
      <div class="overflow-x-auto">
        <table class="table-base">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Active</th><th>Last sign in</th></tr></thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td class="font-semibold">{{ user.fullName }}</td>
              <td class="text-ink-500">{{ user.email }}</td>
              <td>
                <select :value="user.role" class="input w-auto py-1.5 text-xs" :disabled="user.id === auth.user.value?.id" @change="updateAccess(user, $event.target.value, user.active)">
                  <option>ADMIN</option><option>KITCHEN</option><option>WAITER</option><option>CUSTOMER</option>
                </select>
              </td>
              <td><input type="checkbox" class="h-4 w-4 accent-brand-500" :checked="user.active" :disabled="user.id === auth.user.value?.id" :aria-label="`${user.fullName} active`" @change="updateAccess(user, user.role, $event.target.checked)"></td>
              <td class="text-xs text-ink-500">{{ user.lastLoginAt ? dateTime(user.lastLoginAt) : 'Never' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <BaseModal :open="modal" title="Add a staff account" @close="modal = false">
      <form id="user-form" class="grid gap-4 sm:grid-cols-2" @submit.prevent="createUser">
        <label class="sm:col-span-2"><span class="field-label">Full name</span><input v-model="newUser.fullName" class="input" required maxlength="120"></label>
        <label><span class="field-label">Email</span><input v-model="newUser.email" type="email" class="input" required maxlength="160"></label>
        <label><span class="field-label">Role</span><select v-model="newUser.role" class="input"><option value="KITCHEN">Kitchen</option><option value="WAITER">Floor / waiter</option><option value="ADMIN">Admin</option></select></label>
        <label class="sm:col-span-2"><span class="field-label">Temporary password</span><PasswordInput v-model="newUser.password" autocomplete="new-password" /><span class="mt-1 block text-xs text-ink-500">8+ characters with letters and numbers. Ask them to change it after signing in.</span></label>
        <p v-if="userError" class="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700 sm:col-span-2">{{ userError }}</p>
      </form>
      <template #footer>
        <button class="btn-light" @click="modal = false">Cancel</button>
        <button class="btn-primary" form="user-form" :disabled="saving">Create account</button>
      </template>
    </BaseModal>
  </div>
</template>
