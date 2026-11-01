import type { SizeTier, Vehicle } from './api'
import { euros } from './money'

export const vehicleLabels: Record<Vehicle, string> = {
  FOOT_BIKE: 'À pied ou à vélo',
  CAR: 'Voiture',
  LARGE_CAR: 'Grande voiture ou utilitaire (objets jusqu’à 2 m)',
}

const capacity: Record<Vehicle, number> = { FOOT_BIKE: 1, CAR: 2, LARGE_CAR: 3 }

export function tierLabel(tiers: SizeTier[], code: string): string {
  return tiers.find((t) => t.code === code)?.label ?? code
}

/** « dès 4,00 € jusqu'à 2 km, puis 0,80 €/km » */
export function tierPricing(tier: SizeTier, includedKm: number): string {
  return `dès ${euros(tier.baseCents)} jusqu’à ${includedKm} km, puis ${euros(tier.perKmCents)}/km`
}

/** Gabarits qu'un véhicule peut transporter (même règle que l'API). */
export function tiersCarriedBy(tiers: SizeTier[], vehicle: Vehicle): SizeTier[] {
  return tiers.filter((t) => capacity[vehicle] >= capacity[t.requiredVehicle])
}
