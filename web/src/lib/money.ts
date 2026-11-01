const formatter = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

export function euros(cents: number): string {
  return formatter.format(cents / 100)
}

export function percent(rate: number | string): string {
  return `${Math.round(Number(rate) * 1000) / 10} %`
}

const km = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** « 3,4 km » */
export function kilometers(distanceKm: number | string): string {
  return `${km.format(Number(distanceKm))} km`
}
