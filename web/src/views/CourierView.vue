<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { loadConnectAndInitialize } from '@stripe/connect-js'
import { api, type Me } from '../lib/api'
import { env } from '../env'

const me = ref<Me | null>(null)
const error = ref('')
const busy = ref(false)
const onboarding = ref(false)
const container = ref<HTMLDivElement | null>(null)

async function refresh() {
  try {
    me.value = await api.me()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function startOnboarding() {
  error.value = ''
  busy.value = true
  try {
    if (!me.value?.courier) {
      await api.createCourierAccount()
    }
    onboarding.value = true
    await nextTick()
    const connect = loadConnectAndInitialize({
      publishableKey: env.stripePublishableKey,
      fetchClientSecret: async () => (await api.courierSession()).clientSecret,
      locale: 'fr-FR',
      appearance: { variables: { colorPrimary: '#0b6e4f' } },
    })
    const component = connect.create('account-onboarding')
    component.setOnExit(async () => {
      onboarding.value = false
      await refresh()
    })
    container.value?.replaceChildren(component)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    onboarding.value = false
  } finally {
    busy.value = false
  }
}

async function openDashboard() {
  error.value = ''
  try {
    const { url } = await api.courierLoginLink()
    window.open(url, '_blank', 'noopener')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(refresh)
</script>

<template>
  <div class="card">
    <h1>Espace coursier</h1>
    <template v-if="me">
      <p v-if="!me.courier">
        Pour accepter des courses, créez votre compte de versement (identité et IBAN, gérés par Stripe).
      </p>
      <p v-else>
        Compte de versement :
        <span class="badge">{{ me.courier.transfersActive ? 'habilité aux virements' : me.courier.transfersStatus }}</span>
      </p>
      <div class="row">
        <button
          v-if="!me.courier?.transfersActive"
          :disabled="busy || onboarding"
          @click="startOnboarding"
        >
          {{ me.courier ? "Compléter l'inscription" : 'Devenir coursier' }}
        </button>
        <button
          v-if="me.courier"
          class="secondary"
          @click="refresh"
        >
          Actualiser
        </button>
        <button
          v-if="me.courier?.transfersActive"
          class="secondary"
          @click="openDashboard"
        >
          Mes gains
        </button>
      </div>
    </template>
    <div
      v-show="onboarding"
      ref="container"
      class="card"
    />
    <p
      v-if="error"
      class="error"
    >
      {{ error }}
    </p>
  </div>
</template>
