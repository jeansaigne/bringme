<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, type Quote } from '../lib/api'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tierPricing } from '../lib/tiers'
import { euros } from '../lib/money'
import { errorMessage } from '../lib/errors'
import PriceReceipt from '../components/PriceReceipt.vue'
import AddressInput from '../components/AddressInput.vue'

const itemDescription = ref('')
const instructions = ref('')
const pickupAddress = ref('')
const dropoffAddress = ref('')
const sizeTier = ref('S')
const quote = ref<Quote | null>(null)
const quoting = ref(false)
const quoteError = ref('')
const error = ref('')
const paying = ref(false)

const tierItems = computed(() =>
  (pricingGrid.value?.sizeTiers ?? []).map((t) => ({
    value: t.code,
    label: t.label,
    description: `${t.description} · ${tierPricing(t, pricingGrid.value?.includedKm ?? 0)}`,
  })),
)

// Une adresse n'a de valeur qu'une fois choisie dans les suggestions (voir AddressInput).
const addressesFilled = computed(() => !!pickupAddress.value && !!dropoffAddress.value)
const canPay = computed(() => !!quote.value && itemDescription.value.trim().length > 0 && !quoting.value)

// Estimation automatique : on attend que la saisie se calme, et on ignore
// les réponses arrivées après une saisie plus récente.
let timer: ReturnType<typeof setTimeout> | undefined
let requestId = 0

watch([pickupAddress, dropoffAddress, sizeTier], () => {
  quote.value = null
  quoteError.value = ''
  clearTimeout(timer)
  if (!addressesFilled.value) return
  timer = setTimeout(estimate, 700)
})

async function estimate() {
  const id = ++requestId
  quoting.value = true
  try {
    const q = await api.quote({
      pickupAddress: pickupAddress.value,
      dropoffAddress: dropoffAddress.value,
      sizeTier: sizeTier.value,
    })
    if (id === requestId) quote.value = q
  } catch (e) {
    if (id === requestId) quoteError.value = errorMessage(e)
  } finally {
    if (id === requestId) quoting.value = false
  }
}

async function payAndPublish() {
  error.value = ''
  paying.value = true
  try {
    const { checkoutUrl } = await api.createCourse({
      itemDescription: itemDescription.value,
      instructions: instructions.value.trim() || undefined,
      pickupAddress: pickupAddress.value,
      dropoffAddress: dropoffAddress.value,
      sizeTier: sizeTier.value,
    })
    window.location.assign(checkoutUrl)
  } catch (e) {
    error.value = errorMessage(e)
    paying.value = false
  }
}

onMounted(() => {
  loadPricing().catch((e) => {
    error.value = errorMessage(e)
  })
})
onUnmounted(() => clearTimeout(timer))
</script>

<template>
  <form
    class="space-y-8"
    @submit.prevent="payAndPublish"
  >
    <header>
      <UButton
        to="/"
        icon="i-lucide-arrow-left"
        label="Mes demandes"
        variant="link"
        color="neutral"
        class="-ml-2.5 mb-2"
      />
      <h1 class="font-display text-3xl font-bold text-highlighted">
        Nouvelle course
      </h1>
      <p class="text-muted">
        L’objet doit déjà être payé : le coursier ne fait que le récupérer et vous l’apporter.
      </p>
    </header>

    <section class="space-y-4">
      <h2 class="font-display text-lg font-semibold text-highlighted">
        1. Quoi ?
      </h2>
      <UFormField
        label="Ce qu’il faut récupérer"
        description="Précisez le nom ou la référence à donner sur place."
        required
      >
        <UTextarea
          v-model="itemDescription"
          :maxlength="500"
          :rows="2"
          autoresize
          placeholder="Colis Vinted au nom de Dupont, retrait n° 4821"
          class="w-full"
        />
      </UFormField>
    </section>

    <section class="space-y-4">
      <h2 class="font-display text-lg font-semibold text-highlighted">
        2. D’où, vers où ?
      </h2>
      <UFormField
        label="Adresse de retrait"
        required
      >
        <AddressInput
          v-model="pickupAddress"
          icon="i-lucide-circle-dot"
          placeholder="12 rue Faidherbe, Lille"
        />
      </UFormField>
      <UFormField
        label="Adresse de livraison"
        required
      >
        <AddressInput
          v-model="dropoffAddress"
          icon="i-lucide-map-pin"
          placeholder="5 place du Général de Gaulle, Lille"
        />
      </UFormField>
    </section>

    <section class="space-y-4">
      <h2 class="font-display text-lg font-semibold text-highlighted">
        3. Quelle taille ?
      </h2>
      <USkeleton
        v-if="!tierItems.length"
        class="h-40 w-full"
      />
      <URadioGroup
        v-else
        v-model="sizeTier"
        :items="tierItems"
        variant="card"
        size="lg"
        :ui="{ fieldset: 'gap-2' }"
      />
      <p class="text-sm text-muted">
        Plus de 2 m ou plus de 30 kg : non pris en charge pour l’instant.
      </p>
    </section>

    <section class="space-y-4">
      <h2 class="font-display text-lg font-semibold text-highlighted">
        4. Instructions supplémentaires
      </h2>
      <UFormField
        label="Pour le coursier (facultatif)"
        description="Code d’accès, étage, horaires, personne à demander… Visible uniquement par le coursier qui accepte la course."
      >
        <UTextarea
          v-model="instructions"
          :maxlength="1000"
          :rows="3"
          autoresize
          placeholder="Digicode 4521B, 3e étage gauche. Je suis là après 18 h."
          class="w-full"
        />
      </UFormField>
    </section>

    <section class="space-y-4">
      <h2 class="font-display text-lg font-semibold text-highlighted">
        5. Prix
      </h2>
      <p
        v-if="!addressesFilled"
        class="rounded-lg border border-dashed border-accented p-4 text-sm text-muted"
      >
        Choisissez les deux adresses dans les suggestions : le prix s’affiche ici.
      </p>
      <USkeleton
        v-else-if="quoting && !quote"
        class="h-56 w-full"
      />
      <UAlert
        v-else-if="quoteError"
        color="error"
        variant="subtle"
        icon="i-lucide-map-pin-off"
        title="Estimation impossible"
        :description="quoteError"
      />
      <PriceReceipt
        v-else-if="quote"
        v-bind="quote"
        highlight="client"
      />
    </section>

    <UAlert
      v-if="error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="error"
    />

    <div class="sticky bottom-20 z-10 md:bottom-4">
      <UButton
        type="submit"
        size="xl"
        block
        icon="i-lucide-lock"
        :loading="paying"
        :disabled="!canPay"
        :label="quote ? `Payer ${euros(quote.priceCents)} et publier` : 'Payer et publier'"
        class="shadow-lg"
      />
      <p
        v-if="quote && !itemDescription.trim()"
        class="mt-2 rounded bg-default/90 text-center text-xs text-muted"
      >
        Indiquez ce qu’il faut récupérer pour continuer.
      </p>
    </div>
  </form>
</template>
