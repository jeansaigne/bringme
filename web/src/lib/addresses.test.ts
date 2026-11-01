import { describe, expect, it } from 'vitest'
import { suggestionLabels } from './addresses'

describe('suggestionLabels', () => {
  it('ne garde que les adresses précises, sans doublon', () => {
    const body = {
      features: [
        { properties: { label: '12 Rue Faidherbe 59800 Lille', type: 'housenumber' } },
        { properties: { label: 'Rue Faidherbe 59800 Lille', type: 'street' } },
        { properties: { label: '12 Rue Faidherbe 59800 Lille', type: 'housenumber' } },
        { properties: { label: 'Lille', type: 'municipality' } },
      ],
    }
    expect(suggestionLabels(body)).toEqual(['12 Rue Faidherbe 59800 Lille'])
  })
  it('tolère une réponse vide ou inattendue', () => {
    expect(suggestionLabels(null)).toEqual([])
    expect(suggestionLabels({})).toEqual([])
  })
})
