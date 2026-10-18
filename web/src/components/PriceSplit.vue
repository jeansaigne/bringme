<script setup lang="ts">
import { euros, percent } from '../lib/money'

defineProps<{
  priceCents: number
  commissionRate: number
  commissionCents: number
  courierAmountCents: number
  distanceKm: number
  weightKg: number
}>()
</script>

<template>
  <table class="split">
    <tbody>
      <tr>
        <td>Distance (vol d'oiseau)</td>
        <td>{{ Number(distanceKm).toFixed(2) }} km</td>
      </tr>
      <tr>
        <td>Poids déclaré</td>
        <td>{{ weightKg }} kg</td>
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
