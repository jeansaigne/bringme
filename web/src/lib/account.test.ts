import { describe, expect, it } from 'vitest'
import { accountInitial } from './account'

describe('accountInitial', () => {
  it("prend la première lettre de l'e-mail, en majuscule", () => {
    expect(accountInitial('aymeric+client@gmail.com')).toBe('A')
  })
  it('renvoie un repli sans e-mail', () => {
    expect(accountInitial(undefined)).toBe('?')
    expect(accountInitial('  ')).toBe('?')
  })
})
