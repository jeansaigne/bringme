/** Initiale affichée dans la pastille du compte connecté. */
export function accountInitial(email: string | null | undefined): string {
  const first = email?.trim().charAt(0)
  return first ? first.toUpperCase() : '?'
}
