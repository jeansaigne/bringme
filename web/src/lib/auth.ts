import { ref } from 'vue'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './supabase'

export const session = ref<Session | null>(null)
export const authReady = ref(false)

const ready = supabase.auth.getSession().then(({ data }) => {
  session.value = data.session
  authReady.value = true
})

supabase.auth.onAuthStateChange((_event, s) => {
  session.value = s
})

export function waitForAuth(): Promise<void> {
  return ready
}
