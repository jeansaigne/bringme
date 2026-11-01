<script setup lang="ts">
import { onMounted } from 'vue'
import { euros, kilometers } from '../lib/money'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tierLabel } from '../lib/tiers'

/**
 * Encart de prix. Le client voit ce qu'il paie ; le coursier voit ce qu'il touche.
 * La répartition plateforme / coursier n'est pas affichée.
 */
defineProps<{
  priceCents: number
  courierAmountCents: number
  distanceKm: number
  sizeTier: string
  highlight?: 'client' | 'courier'
}>()

onMounted(() => {
  loadPricing().catch(() => undefined)
})
</script>

<template>
  <div class="rounded-lg border border-dashed border-accented bg-default p-4 font-mono text-sm">
    <dl class="space-y-1.5">
      <div class="flex justify-between gap-4">
        <dt class="text-muted">
          Distance (vol d'oiseau)
        </dt>
        <dd class="tabular">
          {{ kilometers(distanceKm) }}
        </dd>
      </div>
      <div class="flex justify-between gap-4">
        <dt class="text-muted">
          Gabarit
        </dt>
        <dd>{{ tierLabel(pricingGrid?.sizeTiers ?? [], sizeTier) }}</dd>
      </div>
    </dl>

    <div class="my-3 border-t border-dashed border-accented" />

    <div class="flex items-baseline justify-between gap-4">
      <span class="font-sans font-semibold text-highlighted">
        {{ highlight === 'courier' ? 'Vous touchez' : 'Prix de la course' }}
      </span>
      <span
        class="tabular font-display text-2xl font-bold"
        :class="highlight === 'courier' ? 'text-primary' : 'text-highlighted'"
      >{{ euros(highlight === 'courier' ? courierAmountCents : priceCents) }}</span>
    </div>
    <p class="mt-3 font-sans text-xs text-muted">
      {{ highlight === 'courier'
        ? 'Versé une fois la livraison confirmée par le code de remise.'
        : 'Payé à la commande. En cas d’annulation avant acceptation, vous êtes remboursé intégralement.' }}
    </p>
  </div>
</template>
