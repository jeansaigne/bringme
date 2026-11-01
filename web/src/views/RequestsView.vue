<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type Course } from '../lib/api'
import { loadPricing } from '../lib/pricing'
import { isActive } from '../lib/status'
import { errorMessage } from '../lib/errors'
import CourseCard from '../components/CourseCard.vue'
import EmptyState from '../components/EmptyState.vue'

const courses = ref<Course[] | null>(null)
const error = ref('')

const mine = computed(() => (courses.value ?? []).filter((c) => c.role === 'client'))
const ongoing = computed(() => mine.value.filter((c) => isActive(c.status)))
const past = computed(() => mine.value.filter((c) => !isActive(c.status)))

onMounted(async () => {
  loadPricing().catch(() => undefined)
  try {
    courses.value = await api.myCourses()
  } catch (e) {
    error.value = errorMessage(e)
  }
})
</script>

<template>
  <div class="space-y-8">
    <section class="flex items-end justify-between gap-4">
      <div>
        <h1 class="font-display text-3xl font-bold text-highlighted">
          Mes demandes
        </h1>
        <p class="text-muted">
          Ce que vous avez demandé qu’on vous apporte.
        </p>
      </div>
      <UButton
        to="/courses/new"
        icon="i-lucide-plus"
        label="Nouvelle course"
        size="lg"
        class="hidden sm:inline-flex"
      />
    </section>

    <UAlert
      v-if="error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="error"
    />

    <div
      v-if="!courses && !error"
      class="space-y-3"
    >
      <USkeleton
        v-for="i in 2"
        :key="i"
        class="h-32 w-full"
      />
    </div>

    <EmptyState
      v-else-if="courses && !mine.length"
      icon="i-lucide-package-search"
      title="Aucune course pour l’instant"
      description="Un colis à récupérer en point relais, un objet à aller chercher ? Publiez une course, quelqu’un vous l’apporte."
    >
      <UButton
        to="/courses/new"
        icon="i-lucide-plus"
        label="Publier ma première course"
        size="lg"
      />
    </EmptyState>

    <template v-else-if="courses">
      <section v-if="ongoing.length">
        <h2 class="mb-3 text-sm font-semibold tracking-wide text-muted uppercase">
          En cours
        </h2>
        <div class="grid grid-cols-1 gap-3">
          <CourseCard
            v-for="c in ongoing"
            :key="c.id"
            :course="c"
            perspective="client"
          />
        </div>
      </section>
      <section v-if="past.length">
        <h2 class="mb-3 text-sm font-semibold tracking-wide text-muted uppercase">
          Terminées
        </h2>
        <div class="grid grid-cols-1 gap-3 opacity-90">
          <CourseCard
            v-for="c in past"
            :key="c.id"
            :course="c"
            perspective="client"
          />
        </div>
      </section>
    </template>

    <UButton
      to="/courses/new"
      icon="i-lucide-plus"
      label="Nouvelle course"
      size="xl"
      class="fixed right-4 bottom-24 z-20 rounded-full shadow-lg sm:hidden"
    />
  </div>
</template>
