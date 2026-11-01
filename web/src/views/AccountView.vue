<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { loadConnectAndInitialize } from '@stripe/connect-js'
import { api, type Vehicle } from '../lib/api'
import { env } from '../env'
import { supabase } from '../lib/supabase'
import { accountInitial } from '../lib/account'
import { me, refreshMe, courierReady } from '../lib/me'
import { loadPricing, pricingGrid } from '../lib/pricing'
import { tiersCarriedBy, vehicleLabels } from '../lib/tiers'
import { transfersStatusLabel } from '../lib/status'
import { brand } from '../lib/theme'
import { errorMessage } from '../lib/errors'

const router = useRouter()
const error = ref('')
const busy = ref(false)
const onboarding = ref(false)
const container = ref<HTMLDivElement | null>(null)
const step = ref<string>('vehicle')

const vehicleIcons: Record<Vehicle, string> = {
  FOOT_BIKE: 'i-lucide-bike',
  CAR: 'i-lucide-car',
  LARGE_CAR: 'i-lucide-truck',
}

const vehicleItems = computed(() =>
  (Object.keys(vehicleLabels) as Vehicle[]).map((v) => ({
    value: v,
    label: vehicleLabels[v],
    description: `Gabarits : ${tiersCarriedBy(pricingGrid.value?.sizeTiers ?? [], v).map((t) => t.label).join(', ')}`,
  })),
)

const steps = computed(() => [
  { value: 'vehicle', title: 'Transport', description: me.value?.vehicle ? vehicleLabels[me.value.vehicle] : 'À choisir', icon: 'i-lucide-bike' },
  { value: 'payout', title: 'Versements', description: transfersStatusLabel(me.value?.courier?.transfersStatus, !!me.value?.courier?.transfersActive), icon: 'i-lucide-landmark' },
  { value: 'ready', title: 'Prêt', description: courierReady(me.value) ? 'Vous pouvez livrer' : 'Bientôt', icon: 'i-lucide-rocket' },
])

/** Étape à afficher par défaut : la première qui reste à faire. */
function firstPendingStep(): string {
  if (!me.value?.vehicle) return 'vehicle'
  if (!me.value.courier?.transfersActive) return 'payout'
  return 'ready'
}

async function refresh() {
  error.value = ''
  try {
    await refreshMe()
    step.value = firstPendingStep()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

const vehicle = ref<Vehicle | undefined>()
watch(me, (m) => {
  vehicle.value = m?.vehicle ?? undefined
}, { immediate: true })

async function chooseVehicle(v: Vehicle) {
  if (v === me.value?.vehicle) return
  error.value = ''
  busy.value = true
  try {
    me.value = await api.setVehicle(v)
  } catch (e) {
    error.value = errorMessage(e)
    vehicle.value = me.value?.vehicle ?? undefined
  } finally {
    busy.value = false
  }
}

async function startOnboarding() {
  error.value = ''
  busy.value = true
  try {
    if (!me.value?.courier) {
      await api.createCourierAccount()
    }
    onboarding.value = true
    await nextTick()
    const connect = loadConnectAndInitialize({
      publishableKey: env.stripePublishableKey,
      fetchClientSecret: async () => (await api.courierSession()).clientSecret,
      locale: 'fr-FR',
      appearance: {
        variables: {
          colorPrimary: brand.primary,
          fontFamily: brand.fontFamily,
          borderRadius: brand.radius,
        },
      },
    })
    const component = connect.create('account-onboarding')
    component.setOnExit(async () => {
      onboarding.value = false
      await refresh()
    })
    container.value?.replaceChildren(component)
  } catch (e) {
    error.value = errorMessage(e)
    onboarding.value = false
  } finally {
    busy.value = false
  }
}

async function openDashboard() {
  error.value = ''
  try {
    const { url } = await api.courierLoginLink()
    window.open(url, '_blank', 'noopener')
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function logout() {
  await supabase.auth.signOut()
  await router.push('/login')
}

onMounted(() => {
  refresh()
  loadPricing().catch(() => undefined)
})
</script>

<template>
  <div class="space-y-8">
    <section class="flex items-center gap-4">
      <span class="inline-flex size-14 shrink-0 items-center justify-center rounded-full bg-primary font-display text-2xl font-bold text-inverted">
        {{ accountInitial(me?.email) }}
      </span>
      <div class="min-w-0 flex-1">
        <h1 class="font-display text-2xl font-bold text-highlighted">
          Mon compte
        </h1>
        <p class="truncate text-muted">
          {{ me?.email ?? '…' }}
        </p>
      </div>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-log-out"
        aria-label="Se déconnecter"
        class="shrink-0"
        :ui="{ label: 'hidden sm:inline' }"
        label="Déconnexion"
        @click="logout"
      />
    </section>

    <UAlert
      v-if="error"
      color="error"
      variant="subtle"
      icon="i-lucide-circle-alert"
      :description="error"
    />

    <UCard>
      <template #header>
        <div class="flex items-center justify-between gap-2">
          <div>
            <h2 class="font-display text-lg font-semibold text-highlighted">
              Profil coursier
            </h2>
            <p class="text-sm text-muted">
              Pour livrer des courses et être payé à chaque remise.
            </p>
          </div>
          <UBadge
            v-if="me"
            :color="courierReady(me) ? 'success' : 'neutral'"
            variant="subtle"
            :label="courierReady(me) ? 'Actif' : 'À compléter'"
          />
        </div>
      </template>

      <div
        v-if="!me"
        class="space-y-3"
      >
        <USkeleton class="h-12 w-full" />
        <USkeleton class="h-32 w-full" />
      </div>

      <template v-else>
        <UStepper
          v-model="step"
          :items="steps"
          size="sm"
          class="mb-6"
        />

        <div v-if="step === 'vehicle'">
          <p class="mb-3 text-sm text-muted">
            Vous ne verrez que les courses que vous pouvez transporter.
          </p>
          <URadioGroup
            v-model="vehicle"
            :items="vehicleItems"
            variant="card"
            size="lg"
            :disabled="busy"
            @update:model-value="(v) => chooseVehicle(v as Vehicle)"
          >
            <template #label="{ item }">
              <span class="flex items-center gap-2">
                <UIcon
                  :name="vehicleIcons[item.value as Vehicle]"
                  class="size-5 text-primary"
                />
                {{ item.label }}
              </span>
            </template>
          </URadioGroup>
          <div class="mt-4 flex justify-end">
            <UButton
              v-if="me.vehicle"
              trailing-icon="i-lucide-arrow-right"
              label="Étape suivante"
              @click="step = 'payout'"
            />
          </div>
        </div>

        <div
          v-else-if="step === 'payout'"
          class="space-y-4"
        >
          <div class="flex items-start gap-3 rounded-lg bg-elevated p-4">
            <UIcon
              name="i-lucide-shield-check"
              class="mt-0.5 size-5 shrink-0 text-primary"
            />
            <p class="text-sm text-toned">
              Votre identité et votre IBAN sont gérés par <strong>Stripe</strong>, notre prestataire de paiement.
              {{ brand.name }} n’y a jamais accès.
            </p>
          </div>
          <p class="text-sm">
            Statut :
            <UBadge
              :color="me.courier?.transfersActive ? 'success' : 'warning'"
              variant="subtle"
              :label="transfersStatusLabel(me.courier?.transfersStatus, !!me.courier?.transfersActive)"
            />
          </p>
          <div class="flex flex-wrap gap-2">
            <UButton
              v-if="!me.courier?.transfersActive"
              icon="i-lucide-landmark"
              :label="me.courier ? 'Compléter mon inscription' : 'Créer mon compte de versement'"
              :loading="busy"
              :disabled="onboarding"
              @click="startOnboarding"
            />
            <UButton
              v-if="me.courier"
              color="neutral"
              variant="outline"
              icon="i-lucide-refresh-cw"
              label="Actualiser"
              @click="refresh"
            />
          </div>
          <div
            v-show="onboarding"
            ref="container"
            class="rounded-lg border border-default p-2"
          />
        </div>

        <div
          v-else
          class="space-y-4 text-center"
        >
          <template v-if="courierReady(me)">
            <span class="mx-auto inline-flex size-14 items-center justify-center rounded-full bg-success/10 text-success">
              <UIcon
                name="i-lucide-party-popper"
                class="size-7"
              />
            </span>
            <p class="font-display text-xl font-semibold text-highlighted">
              Vous êtes prêt à livrer
            </p>
            <div class="flex flex-wrap justify-center gap-2">
              <UButton
                to="/livrer"
                icon="i-lucide-bike"
                label="Voir les courses"
              />
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-wallet"
                trailing-icon="i-lucide-external-link"
                label="Mes gains"
                @click="openDashboard"
              />
            </div>
          </template>
          <p
            v-else
            class="text-muted"
          >
            Terminez les étapes précédentes pour commencer à livrer.
          </p>
        </div>
      </template>
    </UCard>
  </div>
</template>
