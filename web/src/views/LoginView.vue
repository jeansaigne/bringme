<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { supabase } from '../lib/supabase'
import { errorMessage } from '../lib/errors'
import AppLogo from '../components/AppLogo.vue'

const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'signup'>('login')
const email = ref('')
const password = ref('')
const error = ref('')
const info = ref('')
const busy = ref(false)

const tabs = [
  { label: 'Connexion', value: 'login' },
  { label: 'Inscription', value: 'signup' },
]

const steps = [
  { icon: 'i-lucide-pencil-line', title: 'Vous publiez', text: 'Quoi récupérer, où, et où le livrer.' },
  { icon: 'i-lucide-bike', title: 'Un voisin s’en charge', text: 'N’importe quel membre peut accepter la course.' },
  { icon: 'i-lucide-hand-coins', title: 'Il est payé à la remise', text: 'Avec votre code, pas avant.' },
]

const submitLabel = computed(() => (mode.value === 'login' ? 'Se connecter' : 'Créer mon compte'))

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
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="grid gap-10 py-4 md:grid-cols-2 md:items-center md:py-12">
    <section>
      <AppLogo />
      <h1 class="mt-6 font-display text-4xl leading-tight font-bold text-highlighted sm:text-5xl">
        Quelqu’un passe le chercher.<br>
        <span class="text-primary">Vous, vous restez là.</span>
      </h1>
      <p class="mt-4 text-lg text-muted">
        Un colis en point relais, un objet déjà payé : publiez la course, un membre de GivtMe vous l’apporte.
      </p>
      <ol class="mt-8 space-y-4">
        <li
          v-for="(s, i) in steps"
          :key="s.title"
          class="flex gap-3"
        >
          <span class="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <UIcon
              :name="s.icon"
              class="size-5"
            />
          </span>
          <div>
            <p class="font-semibold text-highlighted">
              {{ i + 1 }}. {{ s.title }}
            </p>
            <p class="text-sm text-muted">
              {{ s.text }}
            </p>
          </div>
        </li>
      </ol>
    </section>

    <UCard :ui="{ body: 'sm:p-6' }">
      <UTabs
        v-model="mode"
        :items="tabs"
        :content="false"
        class="mb-6"
      />
      <form
        class="space-y-4"
        @submit.prevent="submit"
      >
        <UFormField
          label="E-mail"
          name="email"
        >
          <UInput
            v-model="email"
            type="email"
            autocomplete="email"
            required
            icon="i-lucide-mail"
            size="lg"
            class="w-full"
          />
        </UFormField>
        <UFormField
          label="Mot de passe"
          name="password"
          :hint="mode === 'signup' ? '8 caractères minimum' : undefined"
        >
          <UInput
            v-model="password"
            type="password"
            minlength="8"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            required
            icon="i-lucide-lock"
            size="lg"
            class="w-full"
          />
        </UFormField>
        <UAlert
          v-if="info"
          color="success"
          variant="subtle"
          icon="i-lucide-mail-check"
          :description="info"
        />
        <UAlert
          v-if="error"
          color="error"
          variant="subtle"
          icon="i-lucide-circle-alert"
          :description="error"
        />
        <UButton
          type="submit"
          :label="submitLabel"
          :loading="busy"
          size="lg"
          block
        />
      </form>
    </UCard>
  </div>
</template>
