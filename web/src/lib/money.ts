const formatter = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

export function euros(cents: number): string {
  return formatter.format(cents / 100)
}

export function percent(rate: number | string): string {
  return `${Math.round(Number(rate) * 1000) / 10} %`
}
