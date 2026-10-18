import { env } from '../env'
import { supabase } from './supabase'
import type { CourseStatus } from './status'

export interface Course {
  id: string
  status: CourseStatus
  role: 'client' | 'courier' | 'candidate'
  actions: string[]
  itemDescription: string
  pickupAddress: string
  dropoffAddress: string
  weightKg: number
  distanceKm: number
  priceCents: number
  commissionRate: number
  commissionCents: number
  courierAmountCents: number
  currency: string
  handoverCode: string | null
  payoutError: string | null
  createdAt: string
}

export interface Quote {
  pickupAddress: string
  dropoffAddress: string
  distanceKm: number
  weightKg: number
  priceCents: number
  commissionRate: number
  commissionCents: number
  courierAmountCents: number
}

export interface Me {
  id: string
  email: string
  displayName: string | null
  courier: { accountId: string; transfersActive: boolean; transfersStatus: string } | null
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message)
  }
}

async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown): Promise<T> {
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token
  const res = await fetch(`${env.apiUrl}/api${path}`, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = `Erreur ${res.status}`
    let code: string | undefined
    try {
      const problem = await res.json()
      message = problem.detail ?? message
      code = problem.code
    } catch {
      // corps non JSON : on garde le message générique
    }
    throw new ApiError(message, res.status, code)
  }
  return (await res.json()) as T
}

export const api = {
  me: () => request<Me>('GET', '/me'),
  quote: (input: { pickupAddress: string; dropoffAddress: string; weightKg: number }) =>
    request<Quote>('POST', '/quotes', input),
  createCourse: (input: { itemDescription: string; pickupAddress: string; dropoffAddress: string; weightKg: number }) =>
    request<{ course: Course; checkoutUrl: string }>('POST', '/courses', input),
  myCourses: () => request<Course[]>('GET', '/courses/mine'),
  availableCourses: () => request<Course[]>('GET', '/courses/available'),
  course: (id: string) => request<Course>('GET', `/courses/${id}`),
  cancel: (id: string) => request<Course>('POST', `/courses/${id}/cancel`),
  accept: (id: string) => request<Course>('POST', `/courses/${id}/accept`),
  deliver: (id: string, code: string) => request<Course>('POST', `/courses/${id}/deliver`, { code }),
  retryPayout: (id: string) => request<Course>('POST', `/courses/${id}/retry-payout`),
  createCourierAccount: () => request<{ accountId: string }>('POST', '/courier/account'),
  courierSession: () => request<{ clientSecret: string }>('POST', '/courier/account-session'),
  courierLoginLink: () => request<{ url: string }>('POST', '/courier/login-link'),
}
