<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { session } from './lib/auth'
import { env } from './env'
import AppLogo from './components/AppLogo.vue'

const route = useRoute()
const testMode = env.stripePublishableKey.startsWith('pk_test')

const tabs = [
  { label: 'Demander', icon: 'i-lucide-package-search', to: '/', match: (p: string) => p === '/' || p.startsWith('/courses/new') },
  { label: 'Livrer', icon: 'i-lucide-bike', to: '/livrer', match: (p: string) => p.startsWith('/livrer') },
  { label: 'Compte', icon: 'i-lucide-circle-user-round', to: '/compte', match: (p: string) => p.startsWith('/compte') },
]

const showNav = computed(() => !!session.value && !route.meta.public)
</script>

<template>
  <UApp :toaster="{ position: 'top-center' }">
    <UBanner
      v-if="testMode"
      id="stripe-test-mode"
      icon="i-lucide-flask-conical"
      color="warning"
      title="Mode test · carte 4242 4242 4242 4242"
      close
    />

    <header
      v-if="showNav"
      class="sticky top-0 z-30 border-b border-default bg-default/85 backdrop-blur"
    >
      <div class="mx-auto flex h-14 max-w-3xl items-center justify-between px-4">
        <RouterLink
          to="/"
          aria-label="GivtMe, accueil"
        >
          <AppLogo />
        </RouterLink>
        <nav class="hidden items-center gap-1 md:flex">
          <UButton
            v-for="t in tabs"
            :key="t.to"
            :to="t.to"
            :icon="t.icon"
            :label="t.label"
            variant="ghost"
            :color="t.match(route.path) ? 'primary' : 'neutral'"
            :class="t.match(route.path) ? 'bg-primary/10' : ''"
          />
        </nav>
      </div>
    </header>

    <main
      class="mx-auto max-w-3xl px-4 py-6"
      :class="showNav ? 'pb-28 md:pb-10' : ''"
    >
      <RouterView />
    </main>

    <nav
      v-if="showNav"
      class="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-default bg-default/95 backdrop-blur md:hidden"
      aria-label="Navigation principale"
    >
      <ul class="mx-auto grid max-w-md grid-cols-3">
        <li
          v-for="t in tabs"
          :key="t.to"
        >
          <RouterLink
            :to="t.to"
            class="flex flex-col items-center gap-0.5 py-2 text-xs font-medium"
            :class="t.match(route.path) ? 'text-primary' : 'text-muted'"
            :aria-current="t.match(route.path) ? 'page' : undefined"
          >
            <UIcon
              :name="t.icon"
              class="size-6"
            />
            {{ t.label }}
          </RouterLink>
        </li>
      </ul>
    </nav>
  </UApp>
</template>
