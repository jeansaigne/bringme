<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, type Quote } from '../lib/api'
import PriceSplit from '../components/PriceSplit.vue'

const itemDescription = ref('')
const pickupAddress = ref('')
const dropoffAddress = ref('')
const weightKg = ref(1)
const quote = ref<Quote | null>(null)
const error = ref('')
const busy = ref(false)

watch([pickupAddress, dropoffAddress, weightKg], () => {
  quote.value = null
})

async function estimate() {
  error.value = ''
  busy.value = true
  try {
    quote.value = await api.quote({
      pickupAddress: pickupAddress.value,
      dropoffAddress: dropoffAddress.value,
      weightKg: weightKg.value,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function payAndPublish() {
  error.value = ''
  busy.value = true
  try {
    const { checkoutUrl } = await api.createCourse({
      itemDescription: itemDescription.value,
      pickupAddress: pickupAddress.value,
      dropoffAddress: dropoffAddress.value,
      weightKg: weightKg.value,
    })
    window.location.assign(checkoutUrl)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    busy.value = false
  }
}
</script>

<template>
  <div class="card">
    <h1>Nouvelle course</h1>
    <p class="muted">
      L'objet doit déjà être payé : le coursier ne fait que le récupérer et vous l'apporter.
    </p>
    <form @submit.prevent="estimate">
      <label for="item">Quoi ?</label>
      <input
        id="item"
        v-model="itemDescription"
        maxlength="500"
        required
        placeholder="Colis Vinted au nom de Dupont"
      >
      <label for="pickup">Adresse de retrait</label>
      <input
        id="pickup"
        v-model="pickupAddress"
        required
        placeholder="12 rue Faidherbe, Lille"
      >
      <label for="dropoff">Adresse de livraison</label>
      <input
        id="dropoff"
        v-model="dropoffAddress"
        required
        placeholder="5 place du Général de Gaulle, Lille"
      >
      <label for="weight">Poids (kg, max 30)</label>
      <input
        id="weight"
        v-model.number="weightKg"
        type="number"
        min="0.1"
        max="30"
        step="0.1"
        required
      >
      <div class="row">
        <button
          type="submit"
          class="secondary"
          :disabled="busy"
        >
          Estimer le prix
        </button>
      </div>
    </form>
  </div>

  <div
    v-if="quote"
    class="card"
  >
    <h2>Estimation</h2>
    <p class="muted">
      {{ quote.pickupAddress }} → {{ quote.dropoffAddress }}
    </p>
    <PriceSplit v-bind="quote" />
    <div class="row">
      <button
        :disabled="busy || !itemDescription"
        @click="payAndPublish"
      >
        Payer et publier
      </button>
    </div>
  </div>

  <p
    v-if="error"
    class="error"
  >
    {{ error }}
  </p>
</template>
