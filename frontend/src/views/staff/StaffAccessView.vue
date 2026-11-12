<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const code = ref('')
const error = ref('')

function enter() {
  if (!import.meta.env.VITE_STAFF_ACCESS_CODE) {
    error.value = 'Staff access is not configured. Set VITE_STAFF_ACCESS_CODE for the frontend.'
    return
  }
  if (code.value !== import.meta.env.VITE_STAFF_ACCESS_CODE) {
    error.value = 'That staff access code is not valid.'
    return
  }
  sessionStorage.setItem('tablebite_staff_access', code.value)
  router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/internal/kitchen')
}
</script>

<template>
  <section class="staff-access">
    <div class="staff-access-card">
      <span class="brand-mark">T</span>
      <p class="eyebrow">Internal workspace</p>
      <h1>Staff sign in</h1>
      <p>Kitchen, orders, and admin tools are only available to restaurant staff.</p>
      <form @submit.prevent="enter">
        <label for="staff-code">Access code</label>
        <input id="staff-code" v-model="code" type="password" autocomplete="current-password" required>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="button" type="submit">Enter workspace</button>
      </form>
    </div>
  </section>
</template>
