<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, type Course } from '../lib/api'
import { statusLabels } from '../lib/status'
import PriceSplit from '../components/PriceSplit.vue'

const props = defineProps<{ id: string }>()
const route = useRoute()
const course = ref<Course | null>(null)
const error = ref('')
const busy = ref(false)
const code = ref('')
let timer: ReturnType<typeof setInterval> | undefined

const waitingForWebhook = computed(
  () => route.query.paid === '1' && course.value?.status === 'PENDING_PAYMENT',
)

async function load() {
  try {
    course.value = await api.course(props.id)
    if (!waitingForWebhook.value && timer) {
      clearInterval(timer)
      timer = undefined
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function run(action: () => Promise<Course>) {
  error.value = ''
  busy.value = true
  try {
    course.value = await action()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

const accept = () => run(() => api.accept(props.id))
const cancel = () => run(() => api.cancel(props.id))
const retryPayout = () => run(() => api.retryPayout(props.id))
const deliver = () => run(() => api.deliver(props.id, code.value))

onMounted(async () => {
  await load()
  if (waitingForWebhook.value) {
    timer = setInterval(load, 2000)
  }
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<template>
  <div
    v-if="course"
    class="card"
  >
    <h1>{{ course.itemDescription }}</h1>
    <p>
      <span class="badge">{{ statusLabels[course.status] }}</span>
      <span
        v-if="waitingForWebhook"
        class="muted"
      > Confirmation du paiement en cours…</span>
    </p>
    <p>{{ course.pickupAddress }} → {{ course.dropoffAddress }}</p>
    <PriceSplit v-bind="course" />

    <div
      v-if="course.handoverCode"
      class="card"
    >
      <p>Code de remise à donner au coursier <strong>uniquement</strong> quand il vous remet l'objet :</p>
      <p class="code">
        {{ course.handoverCode }}
      </p>
    </div>

    <p
      v-if="course.payoutError"
      class="error"
    >
      Versement au coursier en attente : {{ course.payoutError }}
    </p>

    <div class="row">
      <button
        v-if="course.actions.includes('accept')"
        :disabled="busy"
        @click="accept"
      >
        Accepter la course
      </button>
      <button
        v-if="course.actions.includes('cancel')"
        class="danger"
        :disabled="busy"
        @click="cancel"
      >
        {{ course.status === 'PENDING_PAYMENT' ? 'Annuler' : 'Annuler et être remboursé' }}
      </button>
      <button
        v-if="course.actions.includes('retry-payout')"
        class="secondary"
        :disabled="busy"
        @click="retryPayout"
      >
        Relancer le versement
      </button>
    </div>

    <form
      v-if="course.actions.includes('deliver')"
      @submit.prevent="deliver"
    >
      <label for="code">Code de remise donné par le client</label>
      <input
        id="code"
        v-model="code"
        inputmode="numeric"
        pattern="\d{6}"
        maxlength="6"
        required
      >
      <div class="row">
        <button
          type="submit"
          :disabled="busy"
        >
          Confirmer la livraison
        </button>
      </div>
    </form>
  </div>
  <p
    v-if="error"
    class="error"
  >
    {{ error }}
  </p>
</template>
