import type { WeightTier } from './api'
import { euros } from './money'

/** « Moyen · 2 à 5 kg », avec la borne basse déduite de la tranche précédente. */
export function tierRange(tiers: WeightTier[], code: string): string {
  const index = tiers.findIndex((t) => t.code === code)
  if (index < 0) return code
  const tier = tiers[index]
  const range = index === 0 ? `jusqu'à ${tier.maxKg} kg` : `${tiers[index - 1].maxKg} à ${tier.maxKg} kg`
  return `${tier.label} · ${range}`
}

export function tierOption(tiers: WeightTier[], tier: WeightTier): string {
  return `${tierRange(tiers, tier.code)} (+${euros(tier.surchargeCents)})`
}
