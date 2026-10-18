<script setup lang="ts">
import { useRouter } from 'vue-router'
import { session } from './lib/auth'
import { supabase } from './lib/supabase'

const router = useRouter()

async function logout() {
  await supabase.auth.signOut()
  await router.push('/login')
}
</script>

<template>
  <header class="topbar">
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
      <button
        class="link"
        @click="logout"
      >
        Déconnexion
      </button>
    </nav>
  </header>
  <main class="container">
    <RouterView />
  </main>
  <footer class="footer">
    Mode test Stripe : aucun paiement réel.
  </footer>
</template>
