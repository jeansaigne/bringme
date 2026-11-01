import { ref } from 'vue'
import { api, type WeightTier } from './api'

/** Tranches de poids, chargées une fois depuis l'API (seule source de vérité de la grille). */
export const weightTiers = ref<WeightTier[]>([])
let loading: Promise<void> | null = null

export function loadPricing(): Promise<void> {
  loading ??= api.pricing().then((grid) => {
    weightTiers.value = grid.weightTiers
  })
  return loading
}
