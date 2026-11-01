import { describe, expect, it } from 'vitest'
import { isActive, timeline, transfersStatusLabel } from './status'

describe('timeline', () => {
  it('suit le parcours nominal', () => {
    expect(timeline('PENDING_PAYMENT').current).toBe(-1)
    expect(timeline('PAID').current).toBe(0)
    expect(timeline('ACCEPTED').current).toBe(1)
    expect(timeline('DELIVERED').current).toBe(2)
    const done = timeline('PAID_OUT')
    expect(done.current).toBe(done.steps.length - 1)
    expect(done.steps.map((s) => s.key)).toEqual(['paid', 'accepted', 'delivered', 'paid-out'])
  })

  it('bascule sur le parcours de remboursement', () => {
    const refunding = timeline('REFUND_PENDING')
    expect(refunding.steps.map((s) => s.key)).toEqual(['paid', 'cancelled', 'refunded'])
    expect(refunding.current).toBe(1)
    expect(timeline('REFUNDED').current).toBe(2)
  })
})

describe('isActive', () => {
  it('distingue les courses en cours des courses terminées', () => {
    expect(isActive('ACCEPTED')).toBe(true)
    expect(isActive('PAID_OUT')).toBe(false)
    expect(isActive('REFUNDED')).toBe(false)
  })
})

describe('transfersStatusLabel', () => {
  it('traduit les statuts Stripe', () => {
    expect(transfersStatusLabel('pending', false)).toBe('Vérification en cours')
    expect(transfersStatusLabel('active', true)).toBe('Habilité aux virements')
    expect(transfersStatusLabel(undefined, false)).toBe('Inscription à terminer')
  })
})
