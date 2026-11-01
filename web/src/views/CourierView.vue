<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { loadConnectAndInitialize } from '@stripe/connect-js'
import { api, type Me, type Vehicle } from '../lib/api'
import { env } from '../env'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tiersCarriedBy, vehicleLabels } from '../lib/tiers'

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

const vehicles = Object.keys(vehicleLabels) as Vehicle[]

async function chooseVehicle(vehicle: Vehicle) {
  error.value = ''
  busy.value = true
  try {
    me.value = await api.setVehicle(vehicle)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

function carried(vehicle: Vehicle): string {
  return tiersCarriedBy(pricingGrid.value?.sizeTiers ?? [], vehicle).map((t) => t.label).join(', ')
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

onMounted(() => {
  refresh()
  loadPricing().catch(() => undefined)
})
</script>

<template>
  <div class="card">
    <h1>Espace coursier</h1>
    <template v-if="me">
      <fieldset class="tiers">
        <legend>Mon moyen de transport</legend>
        <label
          v-for="v in vehicles"
          :key="v"
          class="tier"
          :class="{ selected: me.vehicle === v }"
        >
          <input
            type="radio"
            name="vehicle"
            :value="v"
            :checked="me.vehicle === v"
            :disabled="busy"
            @change="chooseVehicle(v)"
          >
          <span>
            <strong>{{ vehicleLabels[v] }}</strong><br>
            <span class="muted">Gabarits : {{ carried(v) }}</span>
          </span>
        </label>
        <p
          v-if="!me.vehicle"
          class="error"
        >
          Choisissez votre moyen de transport : vous ne verrez que les courses que vous pouvez transporter.
        </p>
      </fieldset>

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
