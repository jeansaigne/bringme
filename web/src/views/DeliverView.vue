<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type Course } from '../lib/api'
import { loadPricing } from '../lib/pricing'
import { isActive } from '../lib/status'
import { me, refreshMe, courierReady } from '../lib/me'
import { vehicleLabels } from '../lib/tiers'
import { errorMessage } from '../lib/errors'
import CourseCard from '../components/CourseCard.vue'
import EmptyState from '../components/EmptyState.vue'

const mine = ref<Course[] | null>(null)
const available = ref<Course[] | null>(null)
const error = ref('')
const loading = ref(true)

const deliveries = computed(() => (mine.value ?? []).filter((c) => c.role === 'courier'))
const ongoing = computed(() => deliveries.value.filter((c) => isActive(c.status)))
const ready = computed(() => courierReady(me.value))

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[mine.value, available.value] = await Promise.all([api.myCourses(), api.availableCourses(), refreshMe()])
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPricing().catch(() => undefined)
  load()
})
</script>

<template>
  <div class="space-y-8">
    <section class="flex items-end justify-between gap-4">
      <div>
        <h1 class="font-display text-3xl font-bold text-highlighted">
          Livrer
        </h1>
        <p class="text-muted">
          <template v-if="me?.vehicle">
            Courses compatibles avec : {{ vehicleLabels[me.vehicle].toLowerCase() }}.
          </template>
          <template v-else>
            Rendez service près de chez vous, et soyez payé à la remise.
          </template>
        </p>
      </div>
      <UButton
        icon="i-lucide-refresh-cw"
        color="neutral"
        variant="ghost"
        aria-label="Actualiser"
        :loading="loading"
        @click="load"
      />
    </section>

    <UAlert
      v-if="me && !ready"
      color="primary"
      variant="subtle"
      icon="i-lucide-rocket"
      title="Devenez coursier en deux étapes"
      description="Déclarez votre moyen de transport, puis créez votre compte de versement pour être payé."
      :actions="[{ label: 'Commencer', to: '/compte', color: 'primary', trailingIcon: 'i-lucide-arrow-right' }]"
    />

    <UAlert
      v-if="error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="error"
    />

    <section v-if="ongoing.length">
      <h2 class="mb-3 text-sm font-semibold tracking-wide text-muted uppercase">
        Mes livraisons en cours
      </h2>
      <div class="grid grid-cols-1 gap-3">
        <CourseCard
          v-for="c in ongoing"
          :key="c.id"
          :course="c"
          perspective="courier"
        />
      </div>
    </section>

    <section v-if="me?.vehicle">
      <h2 class="mb-3 text-sm font-semibold tracking-wide text-muted uppercase">
        À prendre
      </h2>
      <div
        v-if="loading && !available"
        class="space-y-3"
      >
        <USkeleton
          v-for="i in 3"
          :key="i"
          class="h-32 w-full"
        />
      </div>
      <EmptyState
        v-else-if="!available?.length"
        icon="i-lucide-coffee"
        title="Rien à prendre pour le moment"
        description="Revenez un peu plus tard : les nouvelles courses apparaissent ici."
      />
      <div
        v-else
        class="grid grid-cols-1 gap-3"
      >
        <CourseCard
          v-for="c in available"
          :key="c.id"
          :course="c"
          perspective="courier"
        />
      </div>
    </section>
  </div>
</template>
