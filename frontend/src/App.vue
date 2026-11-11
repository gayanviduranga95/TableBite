<template>
  <header class="app-header">
    <RouterLink class="brand" to="/">
      <span class="brand-mark">T</span>
      <span>Table<span>Bite</span></span>
    </RouterLink>
    <nav>
      <RouterLink to="/">Scan QR</RouterLink>
    </nav>
  </header>
  <main class="app-shell">
    <RouterView />
  </main>
</template>
