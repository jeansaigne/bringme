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
