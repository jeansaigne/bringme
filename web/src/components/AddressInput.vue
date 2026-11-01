<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { suggestAddresses } from '../lib/addresses'

/**
 * Champ d'adresse à choisir obligatoirement dans les suggestions de la Base Adresse Nationale.
 * Le v-model ne contient une valeur que lorsqu'une suggestion a été sélectionnée ; toute frappe ultérieure
 * l'invalide. L'API re-vérifie l'adresse de toute façon.
 */
const model = defineModel<string>({ default: '' })
defineProps<{ icon?: string; placeholder?: string }>()

const searchTerm = ref(model.value)
const items = ref<string[]>(model.value ? [model.value] : [])
const loading = ref(false)
const failed = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined
let controller: AbortController | undefined

// À la sélection, le composant vide le terme de recherche puis affiche la valeur choisie :
// un terme vide ne doit donc pas invalider la sélection.
function select(value: string | null | undefined) {
  model.value = value ?? ''
}

watch(searchTerm, (term) => {
  if (term === '' || term === model.value) return
  model.value = ''
  clearTimeout(timer)
  controller?.abort()
  if (term.trim().length < 3) {
    items.value = []
    return
  }
  timer = setTimeout(() => search(term), 250)
})

async function search(term: string) {
  controller = new AbortController()
  loading.value = true
  failed.value = false
  try {
    items.value = await suggestAddresses(term, controller.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') {
      items.value = []
      failed.value = true
    }
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  clearTimeout(timer)
  controller?.abort()
})
</script>

<template>
  <div class="space-y-1">
    <UInputMenu
      v-model:search-term="searchTerm"
      :model-value="model"
      :items="items"
      :loading="loading"
      :icon="icon"
      :placeholder="placeholder"
      ignore-filter
      size="lg"
      class="w-full"
      @update:model-value="select"
    >
      <template #empty>
        {{ searchTerm.trim().length < 3 ? 'Tapez au moins 3 caractères' : 'Aucune adresse précise trouvée : ajoutez le numéro' }}
      </template>
    </UInputMenu>
    <p
      v-if="failed"
      class="text-xs text-error"
    >
      Suggestions d’adresses indisponibles, réessayez dans un instant.
    </p>
    <p
      v-else-if="searchTerm.trim() && !model"
      class="text-xs text-muted"
    >
      Choisissez une adresse dans la liste (avec numéro).
    </p>
  </div>
</template>
