<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, LoaderCircle, ShieldCheck } from 'lucide-vue-next'
import { useAuth } from '../../stores/authStore'
import { useToast } from '../../composables/useToast'
import BrandMark from '../../components/BrandMark.vue'
import PasswordInput from '../../components/PasswordInput.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const toast = useToast()
const form = reactive({ email: '', password: '' })
const error = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  if (!form.email.trim() || !form.password) {
    error.value = 'Enter your staff email and password'
    return
  }
  busy.value = true
  try {
    const user = await auth.login(form.email.trim(), form.password)
    if (user.role === 'CUSTOMER') {
      await auth.logout()
      error.value = 'This is a customer account. Staff accounts are created by the restaurant admin.'
      return
    }
    toast.success(`Signed in as ${user.fullName}`)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') ? route.query.redirect : null
    router.replace(redirect || auth.staffHome(user.role))
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="grid min-h-screen lg:grid-cols-2">
    <div class="relative hidden overflow-hidden bg-ink-900 lg:block">
      <img src="/images/hero/restaurant-interior.jpg" alt="" class="absolute inset-0 h-full w-full object-cover opacity-50">
      <div class="absolute inset-0 bg-gradient-to-br from-ink-900/90 via-ink-900/60 to-brand-900/50" />
      <div class="relative flex h-full flex-col justify-between p-12 text-white">
        <div class="flex items-center gap-3"><BrandMark /><span class="text-xl font-extrabold">TableBite</span></div>
        <div>
          <p class="font-display text-5xl leading-tight">Run the whole floor<br>from one screen.</p>
          <p class="mt-4 max-w-md text-ink-200">Live orders, kitchen display, table QR codes, payments and analytics — built for busy services.</p>
        </div>
      </div>
    </div>
    <div class="flex items-center justify-center bg-cream p-6">
      <div class="w-full max-w-sm animate-rise">
        <RouterLink to="/" class="btn-ghost -ml-3 mb-10"><ArrowLeft class="h-4 w-4" /> Customer site</RouterLink>
        <span class="grid h-12 w-12 place-items-center rounded-2xl bg-ink-900 text-white"><ShieldCheck class="h-6 w-6" /></span>
        <h1 class="mt-5 text-3xl font-extrabold">Staff sign in</h1>
        <p class="mt-1 text-ink-500">Admin and floor team accounts.</p>
        <form class="mt-8 space-y-4" novalidate @submit.prevent="submit">
          <label class="block"><span class="field-label">Email</span><input v-model="form.email" type="email" class="input" autocomplete="username" maxlength="160"></label>
          <label class="block"><span class="field-label">Password</span><PasswordInput v-model="form.password" /></label>
          <p v-if="error" class="rounded-xl bg-red-50 p-3 text-sm font-semibold text-red-700" role="alert">{{ error }}</p>
          <button class="btn-dark btn-lg w-full" :disabled="busy"><LoaderCircle v-if="busy" class="h-5 w-5 animate-spin" /> Sign in</button>
        </form>
        <p class="mt-6 text-center text-sm text-ink-500">Kitchen team? <RouterLink to="/kitchen" class="font-bold text-brand-600">Open the kitchen display with the kitchen code</RouterLink></p>
      </div>
    </div>
  </div>
</template>
