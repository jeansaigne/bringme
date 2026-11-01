<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { session } from '../lib/auth'
import { supabase } from '../lib/supabase'
import { accountInitial } from '../lib/account'

const router = useRouter()
const busy = ref(false)
const email = computed(() => session.value?.user.email ?? null)

async function logout() {
  busy.value = true
  try {
    await supabase.auth.signOut()
    await router.push('/login')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div
    v-if="session"
    class="account"
    data-testid="account"
  >
    <span
      class="avatar"
      aria-hidden="true"
    >{{ accountInitial(email) }}</span>
    <span
      class="account-email"
      :title="email ?? ''"
    >{{ email }}</span>
    <button
      class="secondary small"
      :disabled="busy"
      @click="logout"
    >
      Se déconnecter
    </button>
  </div>
  <RouterLink
    v-else
    to="/login"
    class="account-login"
  >
    Se connecter
  </RouterLink>
</template>
