<script setup lang="ts">
import { computed } from 'vue'
import type { Course } from '../lib/api'
import { euros, kilometers } from '../lib/money'
import { pricingGrid } from '../lib/pricing'
import { tierLabel } from '../lib/tiers'
import StatusBadge from './StatusBadge.vue'

/**
 * Carte de course. Côté client on lit le statut et le prix payé ;
 * côté coursier, le gain passe au premier plan.
 */
const props = defineProps<{ course: Course; perspective: 'client' | 'courier' }>()

const tier = computed(() => tierLabel(pricingGrid.value?.sizeTiers ?? [], props.course.sizeTier))
</script>

<template>
  <RouterLink
    :to="`/courses/${course.id}`"
    class="group block rounded-lg border border-default bg-default p-4 transition hover:border-primary/50 hover:shadow-sm focus-visible:outline-2 focus-visible:outline-primary"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="truncate font-semibold text-highlighted">
          {{ course.itemDescription }}
        </p>
        <StatusBadge
          v-if="perspective === 'client' || course.role === 'courier'"
          :status="course.status"
          size="sm"
          class="mt-1.5"
        />
      </div>
      <div class="shrink-0 text-right">
        <template v-if="perspective === 'courier'">
          <p class="tabular font-display text-2xl leading-none font-bold text-primary">
            {{ euros(course.courierAmountCents) }}
          </p>
          <p class="mt-1 text-xs text-muted">
            pour vous
          </p>
        </template>
        <p
          v-else
          class="tabular font-display text-lg font-semibold text-highlighted"
        >
          {{ euros(course.priceCents) }}
        </p>
      </div>
    </div>

    <ol class="mt-3 space-y-1 text-sm">
      <li class="flex items-center gap-2 text-toned">
        <UIcon
          name="i-lucide-circle-dot"
          class="size-4 shrink-0 text-muted"
        />
        <span class="truncate">{{ course.pickupAddress }}</span>
      </li>
      <li class="flex items-center gap-2 text-toned">
        <UIcon
          name="i-lucide-map-pin"
          class="size-4 shrink-0 text-primary"
        />
        <span class="truncate">{{ course.dropoffAddress }}</span>
      </li>
    </ol>

    <div class="mt-3 flex items-center gap-2 text-xs text-muted">
      <span class="tabular">{{ kilometers(course.distanceKm) }}</span>
      <span aria-hidden="true">·</span>
      <span>{{ tier }}</span>
      <UIcon
        name="i-lucide-chevron-right"
        class="ml-auto size-4 transition group-hover:translate-x-0.5"
      />
    </div>
  </RouterLink>
</template>
