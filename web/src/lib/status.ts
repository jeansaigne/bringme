export type CourseStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'ACCEPTED'
  | 'DELIVERED'
  | 'PAID_OUT'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REFUND_PENDING'
  | 'REFUNDED'

export const statusLabels: Record<CourseStatus, string> = {
  PENDING_PAYMENT: 'En attente de paiement',
  PAID: 'Payée, en attente de coursier',
  ACCEPTED: 'Acceptée par un coursier',
  DELIVERED: 'Livrée, versement en cours',
  PAID_OUT: 'Livrée, coursier payé',
  CANCELLED: 'Annulée',
  EXPIRED: 'Paiement expiré',
  REFUND_PENDING: 'Remboursement en cours',
  REFUNDED: 'Remboursée',
}

export type StatusColor = 'primary' | 'success' | 'warning' | 'info' | 'neutral' | 'error'

/** Pastille de statut : couleur sémantique Nuxt UI + icône Lucide. */
export const statusStyle: Record<CourseStatus, { color: StatusColor; icon: string }> = {
  PENDING_PAYMENT: { color: 'warning', icon: 'i-lucide-credit-card' },
  PAID: { color: 'info', icon: 'i-lucide-search' },
  ACCEPTED: { color: 'primary', icon: 'i-lucide-bike' },
  DELIVERED: { color: 'primary', icon: 'i-lucide-package-check' },
  PAID_OUT: { color: 'success', icon: 'i-lucide-circle-check' },
  CANCELLED: { color: 'neutral', icon: 'i-lucide-ban' },
  EXPIRED: { color: 'neutral', icon: 'i-lucide-clock-alert' },
  REFUND_PENDING: { color: 'warning', icon: 'i-lucide-undo-2' },
  REFUNDED: { color: 'neutral', icon: 'i-lucide-undo-2' },
}

/** Une course est « en cours » tant qu'elle n'a pas atteint un état final. */
export function isActive(status: CourseStatus): boolean {
  return ['PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'DELIVERED', 'REFUND_PENDING'].includes(status)
}

export interface TimelineStep {
  key: string
  title: string
  icon: string
}

const happyPath: TimelineStep[] = [
  { key: 'paid', title: 'Payée', icon: 'i-lucide-credit-card' },
  { key: 'accepted', title: 'Prise en charge', icon: 'i-lucide-bike' },
  { key: 'delivered', title: 'Livrée', icon: 'i-lucide-package-check' },
  { key: 'paid-out', title: 'Coursier payé', icon: 'i-lucide-hand-coins' },
]

const refundPath: TimelineStep[] = [
  { key: 'paid', title: 'Payée', icon: 'i-lucide-credit-card' },
  { key: 'cancelled', title: 'Annulée', icon: 'i-lucide-ban' },
  { key: 'refunded', title: 'Remboursée', icon: 'i-lucide-undo-2' },
]

/**
 * Frise de statut : les étapes à afficher et l'index de l'étape courante
 * (les précédentes sont faites). -1 = aucune étape atteinte.
 * Une étape « en cours » (versement, remboursement) reste courante tant qu'elle n'est pas finie.
 */
export function timeline(status: CourseStatus): { steps: TimelineStep[]; current: number } {
  switch (status) {
    case 'PENDING_PAYMENT':
      return { steps: happyPath, current: -1 }
    case 'PAID':
      return { steps: happyPath, current: 0 }
    case 'ACCEPTED':
      return { steps: happyPath, current: 1 }
    case 'DELIVERED':
      return { steps: happyPath, current: 2 }
    case 'PAID_OUT':
      return { steps: happyPath, current: 3 }
    case 'CANCELLED':
      return { steps: refundPath.slice(1, 2), current: 0 }
    case 'EXPIRED':
      return { steps: [{ key: 'expired', title: 'Paiement expiré', icon: 'i-lucide-clock-alert' }], current: 0 }
    case 'REFUND_PENDING':
      return { steps: refundPath, current: 1 }
    case 'REFUNDED':
      return { steps: refundPath, current: 2 }
  }
}

/** Statut du compte de versement Stripe, en français. */
export function transfersStatusLabel(status: string | null | undefined, active: boolean): string {
  if (active) return 'Habilité aux virements'
  switch (status) {
    case 'pending':
      return 'Vérification en cours'
    case 'restricted':
    case 'inactive':
      return 'Informations manquantes'
    case 'unsupported':
      return 'Non pris en charge'
    default:
      return 'Inscription à terminer'
  }
}
