<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Course } from '../lib/api'
import { euros } from '../lib/money'
import { statusLabels } from '../lib/status'

const mine = ref<Course[]>([])
const available = ref<Course[]>([])
const error = ref('')

async function load() {
  try {
    ;[mine.value, available.value] = await Promise.all([api.myCourses(), api.availableCourses()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(load)
</script>

<template>
  <p
    v-if="error"
    class="error"
  >
    {{ error }}
  </p>

  <section class="card">
    <h2>Mes courses</h2>
    <p
      v-if="!mine.length"
      class="muted"
    >
      Aucune course pour l'instant. <RouterLink to="/courses/new">
        Publier une course
      </RouterLink>
    </p>
    <ul>
      <li
        v-for="c in mine"
        :key="c.id"
      >
        <RouterLink :to="`/courses/${c.id}`">
          {{ c.itemDescription }}
        </RouterLink>
        · {{ c.role === 'client' ? 'je suis client' : 'je suis coursier' }}
        · <span class="badge">{{ statusLabels[c.status] }}</span>
        · {{ euros(c.priceCents) }}
      </li>
    </ul>
  </section>

  <section class="card">
    <h2>Courses disponibles</h2>
    <p
      v-if="!available.length"
      class="muted"
    >
      Aucune course à prendre pour le moment.
    </p>
    <ul>
      <li
        v-for="c in available"
        :key="c.id"
      >
        <RouterLink :to="`/courses/${c.id}`">
          {{ c.itemDescription }}
        </RouterLink>
        · {{ c.pickupAddress }} → {{ c.dropoffAddress }}
        · vous touchez {{ euros(c.courierAmountCents) }}
      </li>
    </ul>
  </section>
</template>
