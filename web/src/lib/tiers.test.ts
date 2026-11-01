import { describe, expect, it } from 'vitest'
import { tierOption, tierRange } from './tiers'

const tiers = [
  { code: 'S', label: 'Petit', maxKg: 2, surchargeCents: 100 },
  { code: 'M', label: 'Moyen', maxKg: 5, surchargeCents: 300 },
]

describe('tranches de poids', () => {
  it('décrit la plage de chaque tranche', () => {
    expect(tierRange(tiers, 'S')).toBe("Petit · jusqu'à 2 kg")
    expect(tierRange(tiers, 'M')).toBe('Moyen · 2 à 5 kg')
  })
  it('garde le code brut pour une tranche inconnue', () => {
    expect(tierRange(tiers, 'XL')).toBe('XL')
  })
  it('affiche le supplément dans les options', () => {
    expect(tierOption(tiers, tiers[1]).replace(/\s/g, ' ')).toBe('Moyen · 2 à 5 kg (+3,00 €)')
  })
})
