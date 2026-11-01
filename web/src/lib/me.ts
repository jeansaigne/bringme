import { ref } from 'vue'
import { api, type Me } from './api'

/** Profil de l'utilisateur connecté, partagé entre les vues. */
export const me = ref<Me | null>(null)

export async function refreshMe(): Promise<Me> {
  me.value = await api.me()
  return me.value
}

/** Prêt à livrer : véhicule déclaré et compte de versement habilité. */
export function courierReady(profile: Me | null): boolean {
  return !!profile?.vehicle && !!profile.courier?.transfersActive
}
