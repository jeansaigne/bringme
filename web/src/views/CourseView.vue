<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useToast } from '@nuxt/ui/composables'
import { api, type Course } from '../lib/api'
import { euros, kilometers } from '../lib/money'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tierLabel } from '../lib/tiers'
import { errorMessage } from '../lib/errors'
import PriceReceipt from '../components/PriceReceipt.vue'
import StatusBadge from '../components/StatusBadge.vue'
import StatusTimeline from '../components/StatusTimeline.vue'

const props = defineProps<{ id: string }>()
const route = useRoute()
const toast = useToast()
const course = ref<Course | null>(null)
const error = ref('')
const busy = ref(false)
const code = ref<(number | undefined)[]>([])
const codeValue = computed(() => code.value.map((d) => d ?? '').join(''))
const confirmCancel = ref(false)
let timer: ReturnType<typeof setInterval> | undefined

const waitingForWebhook = computed(
  () => route.query.paid === '1' && course.value?.status === 'PENDING_PAYMENT',
)
const perspective = computed(() => (course.value?.role === 'client' ? 'client' : 'courier'))
const can = (action: string) => course.value?.actions.includes(action) ?? false
const tier = computed(() => (course.value ? tierLabel(pricingGrid.value?.sizeTiers ?? [], course.value.sizeTier) : ''))

async function load() {
  try {
    course.value = await api.course(props.id)
    if (!waitingForWebhook.value && timer) {
      clearInterval(timer)
      timer = undefined
    }
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function run(action: () => Promise<Course>, success?: string) {
  error.value = ''
  busy.value = true
  try {
    course.value = await action()
    if (success) toast.add({ title: success, color: 'success', icon: 'i-lucide-circle-check' })
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

const accept = () => run(() => api.accept(props.id), 'Course acceptée : direction le point de retrait !')
const retryPayout = () => run(() => api.retryPayout(props.id), 'Versement relancé')
const deliver = () => run(() => api.deliver(props.id, codeValue.value), 'Livraison confirmée, merci !')
async function cancel() {
  await run(() => api.cancel(props.id), 'Course annulée')
  confirmCancel.value = false
}

onMounted(async () => {
  loadPricing().catch(() => undefined)
  await load()
  if (waitingForWebhook.value) {
    timer = setInterval(load, 2000)
  }
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<template>
  <div class="space-y-6">
    <UButton
      :to="perspective === 'client' ? '/' : '/livrer'"
      icon="i-lucide-arrow-left"
      :label="perspective === 'client' ? 'Mes demandes' : 'Livrer'"
      variant="link"
      color="neutral"
      class="-ml-2.5"
    />

    <div
      v-if="!course && !error"
      class="space-y-4"
    >
      <USkeleton class="h-10 w-2/3" />
      <USkeleton class="h-16 w-full" />
      <USkeleton class="h-56 w-full" />
    </div>

    <template v-if="course">
      <header class="space-y-3">
        <StatusBadge :status="course.status" />
        <h1 class="font-display text-3xl font-bold break-words text-highlighted">
          {{ course.itemDescription }}
        </h1>
      </header>

      <UAlert
        v-if="waitingForWebhook"
        color="info"
        variant="subtle"
        icon="i-lucide-loader-circle"
        :ui="{ icon: 'animate-spin' }"
        title="Confirmation du paiement en cours…"
        description="Cela prend quelques secondes."
      />

      <UCard :ui="{ body: 'py-5' }">
        <StatusTimeline :status="course.status" />
      </UCard>

      <!-- Client : le code de remise est l'élément principal de l'écran. -->
      <section
        v-if="course.handoverCode"
        class="rounded-xl bg-primary p-6 text-center text-inverted"
      >
        <p class="text-sm font-medium opacity-90">
          Votre code de remise
        </p>
        <p
          class="tabular my-3 font-mono text-5xl font-bold tracking-[0.25em]"
          :aria-label="course.handoverCode.split('').join(' ')"
        >
          {{ course.handoverCode }}
        </p>
        <p class="mx-auto max-w-xs text-sm opacity-90">
          Donnez-le au coursier <strong>uniquement</strong> quand il vous remet l’objet : c’est ce qui déclenche son paiement.
        </p>
      </section>

      <!-- Coursier : saisie du code donné par le client. -->
      <UCard v-if="can('deliver')">
        <form
          class="flex flex-col items-center gap-4 text-center"
          @submit.prevent="deliver"
        >
          <div>
            <p class="font-display text-lg font-semibold text-highlighted">
              Remise de l’objet
            </p>
            <p class="text-sm text-muted">
              Demandez le code à 6 chiffres au client au moment de la remise.
            </p>
          </div>
          <UPinInput
            v-model="code"
            :length="6"
            type="number"
            otp
            size="xl"
            autofocus
          />
          <UButton
            type="submit"
            size="xl"
            block
            icon="i-lucide-package-check"
            label="Confirmer la livraison"
            :loading="busy"
            :disabled="codeValue.length !== 6"
          />
        </form>
      </UCard>

      <!-- Coursier potentiel : le gain d'abord, puis l'action. -->
      <UCard
        v-if="can('accept')"
        :ui="{ body: 'flex flex-col items-center gap-4 text-center' }"
      >
        <div>
          <p class="text-sm text-muted">
            Vous touchez
          </p>
          <p class="tabular font-display text-5xl font-bold text-primary">
            {{ euros(course.courierAmountCents) }}
          </p>
          <p class="text-sm text-muted">
            versés à la livraison confirmée
          </p>
        </div>
        <UButton
          size="xl"
          block
          icon="i-lucide-hand"
          label="Accepter la course"
          :loading="busy"
          @click="accept"
        />
      </UCard>

      <UAlert
        v-if="course.payoutError"
        color="warning"
        variant="subtle"
        icon="i-lucide-triangle-alert"
        title="Versement au coursier en attente"
        :description="course.payoutError"
        :actions="can('retry-payout') ? [{ label: 'Relancer le versement', color: 'warning', loading: busy, onClick: retryPayout }] : []"
      />

      <UAlert
        v-if="error"
        color="error"
        variant="subtle"
        icon="i-lucide-circle-alert"
        :description="error"
      />

      <section class="space-y-3">
        <h2 class="text-sm font-semibold tracking-wide text-muted uppercase">
          Trajet
        </h2>
        <ol class="relative space-y-4 rounded-lg border border-default bg-default p-4">
          <li class="flex gap-3">
            <UIcon
              name="i-lucide-circle-dot"
              class="mt-0.5 size-5 shrink-0 text-muted"
            />
            <div>
              <p class="text-xs text-muted">
                Retrait
              </p>
              <p class="text-highlighted">
                {{ course.pickupAddress }}
              </p>
            </div>
          </li>
          <li class="flex gap-3">
            <UIcon
              name="i-lucide-map-pin"
              class="mt-0.5 size-5 shrink-0 text-primary"
            />
            <div>
              <p class="text-xs text-muted">
                Livraison
              </p>
              <p class="text-highlighted">
                {{ course.dropoffAddress }}
              </p>
            </div>
          </li>
          <li class="flex gap-2 border-t border-default pt-3 text-sm text-muted">
            <span class="tabular">{{ kilometers(course.distanceKm) }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ tier }}</span>
          </li>
        </ol>
      </section>

      <section class="space-y-3">
        <h2 class="text-sm font-semibold tracking-wide text-muted uppercase">
          Répartition du prix
        </h2>
        <PriceReceipt
          v-bind="course"
          :highlight="perspective"
        />
      </section>

      <div
        v-if="can('cancel')"
        class="pt-2 text-center"
      >
        <UButton
          color="error"
          variant="ghost"
          icon="i-lucide-ban"
          :label="course.status === 'PENDING_PAYMENT' ? 'Annuler la course' : 'Annuler et être remboursé'"
          @click="confirmCancel = true"
        />
      </div>

      <UModal
        v-model:open="confirmCancel"
        title="Annuler cette course ?"
        :description="course.status === 'PENDING_PAYMENT'
          ? 'La course ne sera pas publiée.'
          : `Vous serez remboursé de ${euros(course.priceCents)} sur votre moyen de paiement.`"
      >
        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              color="neutral"
              variant="ghost"
              label="Garder la course"
              @click="confirmCancel = false"
            />
            <UButton
              color="error"
              label="Oui, annuler"
              :loading="busy"
              @click="cancel"
            />
          </div>
        </template>
      </UModal>
    </template>

    <UAlert
      v-else-if="error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="error"
    />
  </div>
</template>
