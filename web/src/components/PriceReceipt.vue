<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { euros, percent, kilometers } from '../lib/money'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tierLabel } from '../lib/tiers'
import { brand } from '../lib/theme'

/**
 * Reçu transparent : ce que paie le client, et où va chaque euro.
 * `highlight` met en avant la ligne qui concerne la personne qui regarde.
 */
const props = withDefaults(
  defineProps<{
    priceCents: number
    commissionRate: number
    commissionCents: number
    courierAmountCents: number
    distanceKm: number
    sizeTier: string
    highlight?: 'client' | 'courier'
  }>(),
  { highlight: 'client' },
)

const courierShare = computed(() =>
  props.priceCents > 0 ? Math.round((props.courierAmountCents / props.priceCents) * 100) : 0,
)

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
      <span class="font-sans font-semibold text-highlighted">Payé par le client</span>
      <span
        class="tabular font-display font-bold text-highlighted"
        :class="highlight === 'client' ? 'text-2xl' : 'text-lg'"
      >{{ euros(priceCents) }}</span>
    </div>

    <div
      class="mt-3 flex h-2.5 overflow-hidden rounded-full bg-accented"
      role="img"
      :aria-label="`${courierShare} % pour le coursier, ${100 - courierShare} % pour ${brand.name}`"
    >
      <div
        class="bg-primary"
        :style="{ width: `${courierShare}%` }"
      />
      <div class="flex-1 bg-neutral-400 dark:bg-neutral-600" />
    </div>

    <dl class="mt-3 space-y-1.5">
      <div class="flex justify-between gap-4">
        <dt class="flex items-center gap-2">
          <span class="size-2.5 rounded-full bg-primary" />
          Coursier · {{ courierShare }} %
        </dt>
        <dd
          class="tabular"
          :class="highlight === 'courier' ? 'font-display text-2xl font-bold text-primary' : 'font-semibold'"
        >
          {{ euros(courierAmountCents) }}
        </dd>
      </div>
      <div class="flex justify-between gap-4">
        <dt class="flex items-center gap-2">
          <span class="size-2.5 rounded-full bg-neutral-400 dark:bg-neutral-600" />
          {{ brand.name }} · {{ percent(commissionRate) }}
        </dt>
        <dd class="tabular">
          {{ euros(commissionCents) }}
        </dd>
      </div>
    </dl>
    <p class="mt-3 font-sans text-xs text-muted">
      Le coursier n'est payé qu'une fois la livraison confirmée. En cas d'annulation, le client est remboursé.
    </p>
  </div>
</template>
