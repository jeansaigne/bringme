import { describe, expect, it } from 'vitest'
import { euros, percent } from './money'

describe('money', () => {
  it('formate les centimes en euros', () => {
    expect(euros(450).replace(/\s/g, ' ')).toBe('4,50 €')
  })
  it('formate le taux de commission', () => {
    expect(percent('0.2000')).toBe('20 %')
    expect(percent(0.155)).toBe('15.5 %')
  })
})
