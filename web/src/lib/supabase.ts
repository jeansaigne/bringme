import { createClient } from '@supabase/supabase-js'
import { env } from '../env'

/** Supabase ne sert qu'à l'authentification : toutes les données passent par l'API. */
export const supabase = createClient(env.supabaseUrl, env.supabaseKey, {
  auth: { persistSession: true, autoRefreshToken: true },
})
