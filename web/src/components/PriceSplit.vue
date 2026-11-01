<script setup lang="ts">
import { onMounted } from 'vue'
import { euros, percent } from '../lib/money'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tierLabel } from '../lib/tiers'

defineProps<{
  priceCents: number
  commissionRate: number
  commissionCents: number
  courierAmountCents: number
  distanceKm: number
  sizeTier: string
}>()

onMounted(() => {
  loadPricing().catch(() => undefined)
})
</script>

<template>
  <table class="split">
    <tbody>
      <tr>
        <td>Distance (vol d'oiseau)</td>
        <td>{{ Number(distanceKm).toFixed(2) }} km</td>
      </tr>
      <tr>
        <td>Gabarit</td>
        <td>{{ tierLabel(pricingGrid?.sizeTiers ?? [], sizeTier) }}</td>
      </tr>
      <tr>
        <td><strong>Prix payé par le client</strong></td>
        <td><strong>{{ euros(priceCents) }}</strong></td>
      </tr>
      <tr>
        <td>Part du coursier</td>
        <td>{{ euros(courierAmountCents) }}</td>
      </tr>
      <tr>
        <td>Commission plateforme ({{ percent(commissionRate) }})</td>
        <td>{{ euros(commissionCents) }}</td>
      </tr>
    </tbody>
  </table>
</template>
