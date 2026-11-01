import { ref } from 'vue'
import { api, type PricingGrid } from './api'

/** Grille tarifaire, chargée une fois depuis l'API (seule source de vérité). */
export const pricingGrid = ref<PricingGrid | null>(null)
let loading: Promise<void> | null = null

export function loadPricing(): Promise<void> {
  loading ??= api.pricing().then((grid) => {
    pricingGrid.value = grid
  })
  return loading
}
