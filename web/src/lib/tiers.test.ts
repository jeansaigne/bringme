import { describe, expect, it } from 'vitest'
import type { SizeTier } from './api'
import { tierLabel, tierPricing, tiersCarriedBy } from './tiers'

const tiers: SizeTier[] = [
  { code: 'S', label: 'Petit', description: '', maxKg: 5, maxLengthCm: 50, requiredVehicle: 'FOOT_BIKE', baseCents: 400, perKmCents: 80 },
  { code: 'M', label: 'Moyen', description: '', maxKg: 15, maxLengthCm: 150, requiredVehicle: 'CAR', baseCents: 600, perKmCents: 100 },
  { code: 'L', label: 'Grand', description: '', maxKg: 30, maxLengthCm: 200, requiredVehicle: 'LARGE_CAR', baseCents: 1200, perKmCents: 120 },
]

const norm = (s: string) => s.replace(/\s/g, ' ')

describe('gabarits', () => {
  it('affiche le libellé, ou le code brut si inconnu', () => {
    expect(tierLabel(tiers, 'M')).toBe('Moyen')
    expect(tierLabel(tiers, 'XL')).toBe('XL')
  })
  it('résume le tarif du gabarit', () => {
    expect(norm(tierPricing(tiers[0], 2))).toBe('dès 4,00 € jusqu’à 2 km, puis 0,80 €/km')
  })
  it('filtre les gabarits par véhicule comme l’API', () => {
    expect(tiersCarriedBy(tiers, 'FOOT_BIKE').map((t) => t.code)).toEqual(['S'])
    expect(tiersCarriedBy(tiers, 'CAR').map((t) => t.code)).toEqual(['S', 'M'])
    expect(tiersCarriedBy(tiers, 'LARGE_CAR').map((t) => t.code)).toEqual(['S', 'M', 'L'])
  })
})
