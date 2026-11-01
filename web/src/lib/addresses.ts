/**
 * Suggestions d'adresses via la Géoplateforme IGN (Base Adresse Nationale), gratuite et sans clé.
 * On ne propose que des adresses précises (avec numéro) : ce sont les seules que l'API accepte.
 */
const ENDPOINT = 'https://data.geopf.fr/geocodage/search'

interface Feature {
  properties?: { label?: string; type?: string }
}

export function suggestionLabels(body: unknown): string[] {
  const features = (body as { features?: Feature[] } | null)?.features ?? []
  const labels = features
    .filter((f) => f.properties?.type === 'housenumber' && f.properties.label)
    .map((f) => f.properties!.label!)
  return [...new Set(labels)]
}

export async function suggestAddresses(query: string, signal?: AbortSignal): Promise<string[]> {
  const url = new URL(ENDPOINT)
  url.searchParams.set('q', query)
  url.searchParams.set('autocomplete', '1')
  url.searchParams.set('type', 'housenumber')
  url.searchParams.set('limit', '6')
  const res = await fetch(url, { signal })
  if (!res.ok) throw new Error(`Suggestions indisponibles (${res.status})`)
  return suggestionLabels(await res.json())
}
