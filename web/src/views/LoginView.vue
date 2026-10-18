<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { supabase } from '../lib/supabase'

const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'signup'>('login')
const email = ref('')
const password = ref('')
const error = ref('')
const info = ref('')
const busy = ref(false)

async function submit() {
  error.value = ''
  info.value = ''
  busy.value = true
  try {
    if (mode.value === 'signup') {
      const { data, error: e } = await supabase.auth.signUp({
        email: email.value,
        password: password.value,
        options: { emailRedirectTo: window.location.origin + '/' },
      })
      if (e) throw e
      if (!data.session) {
        info.value = 'Compte créé. Confirmez votre adresse via le lien reçu par e-mail, puis connectez-vous.'
        mode.value = 'login'
        return
      }
    } else {
      const { error: e } = await supabase.auth.signInWithPassword({ email: email.value, password: password.value })
      if (e) throw e
    }
    await router.push(typeof route.query.next === 'string' ? route.query.next : '/')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="card">
    <h1>{{ mode === 'login' ? 'Connexion' : 'Inscription' }}</h1>
    <form @submit.prevent="submit">
      <label for="email">E-mail</label>
      <input
        id="email"
        v-model="email"
        type="email"
        autocomplete="email"
        required
      >
      <label for="password">Mot de passe</label>
      <input
        id="password"
        v-model="password"
        type="password"
        minlength="8"
        autocomplete="current-password"
        required
      >
      <div class="row">
        <button
          type="submit"
          :disabled="busy"
        >
          {{ mode === 'login' ? 'Se connecter' : "S'inscrire" }}
        </button>
        <button
          type="button"
          class="secondary"
          @click="mode = mode === 'login' ? 'signup' : 'login'"
        >
          {{ mode === 'login' ? 'Créer un compte' : "J'ai déjà un compte" }}
        </button>
      </div>
    </form>
    <p
      v-if="info"
      class="muted"
    >
      {{ info }}
    </p>
    <p
      v-if="error"
      class="error"
    >
      {{ error }}
    </p>
  </div>
</template>
