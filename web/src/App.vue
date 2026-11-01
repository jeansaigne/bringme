<script setup lang="ts">
import { session } from './lib/auth'
import AccountMenu from './components/AccountMenu.vue'
</script>

<template>
  <header class="topbar">
    <div class="topbar-left">
      <RouterLink
        to="/"
        class="brand"
      >
        BringMe
      </RouterLink>
      <nav v-if="session">
        <RouterLink to="/courses/new">
          Nouvelle course
        </RouterLink>
        <RouterLink to="/courier">
          Espace coursier
        </RouterLink>
      </nav>
    </div>
    <AccountMenu />
  </header>
  <main class="container">
    <RouterView />
  </main>
  <footer class="footer">
    Mode test Stripe : aucun paiement réel.
  </footer>
</template>
