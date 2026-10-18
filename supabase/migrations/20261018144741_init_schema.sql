-- BringMe — schéma initial du walking skeleton
-- Toutes les tables sont en RLS. Le front ne lit/écrit rien directement (il n'utilise
-- Supabase que pour l'auth) ; les politiques ci-dessous sont un filet de sécurité.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create type public.course_status as enum (
  'PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'DELIVERED', 'PAID_OUT',
  'CANCELLED', 'EXPIRED', 'REFUND_PENDING', 'REFUNDED'
);

-- ---------------------------------------------------------------- profiles
create table public.profiles (
  id                uuid primary key references auth.users (id) on delete cascade,
  display_name      text check (char_length(display_name) <= 80),
  stripe_account_id text unique,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);

-- ---------------------------------------------------------------- courses
create table public.courses (
  id                         uuid primary key default gen_random_uuid(),
  client_id                  uuid not null references public.profiles (id),
  courier_id                 uuid references public.profiles (id),
  item_description           text not null check (char_length(item_description) between 1 and 500),
  pickup_address             text not null check (char_length(pickup_address) between 3 and 300),
  pickup_lat                 double precision not null,
  pickup_lng                 double precision not null,
  dropoff_address            text not null check (char_length(dropoff_address) between 3 and 300),
  dropoff_lat                double precision not null,
  dropoff_lng                double precision not null,
  weight_kg                  numeric(6, 2) not null check (weight_kg > 0 and weight_kg <= 30),
  distance_km                numeric(8, 3) not null check (distance_km >= 0),
  price_cents                integer not null check (price_cents > 0),
  commission_rate            numeric(5, 4) not null check (commission_rate >= 0 and commission_rate < 1),
  commission_cents           integer not null check (commission_cents >= 0),
  courier_amount_cents       integer not null check (courier_amount_cents >= 0),
  currency                   text not null default 'eur' check (currency = 'eur'),
  status                     public.course_status not null default 'PENDING_PAYMENT',
  handover_attempts          integer not null default 0,
  stripe_checkout_session_id text unique,
  stripe_payment_intent_id   text unique,
  stripe_charge_id           text,
  stripe_transfer_id         text unique,
  stripe_refund_id           text unique,
  payout_error               text,
  version                    integer not null default 0,
  created_at                 timestamptz not null default now(),
  updated_at                 timestamptz not null default now(),
  paid_at                    timestamptz,
  accepted_at                timestamptz,
  delivered_at               timestamptz,
  paid_out_at                timestamptz,
  closed_at                  timestamptz,
  constraint courses_split_ck check (price_cents = commission_cents + courier_amount_cents),
  constraint courses_courier_not_client_ck check (courier_id is null or courier_id <> client_id)
);

create index courses_client_id_idx on public.courses (client_id);
create index courses_courier_id_idx on public.courses (courier_id);
create index courses_available_idx on public.courses (created_at) where status = 'PAID' and courier_id is null;

-- Code de remise : table isolée, aucune politique => invisible via l'API Data,
-- seule l'API back (rôle dédié) y accède.
create table public.course_handover_codes (
  course_id  uuid primary key references public.courses (id) on delete cascade,
  code       text not null check (code ~ '^[0-9]{6}$'),
  created_at timestamptz not null default now()
);

-- Journal append-only des transitions
create table public.course_transitions (
  id          bigint generated always as identity primary key,
  course_id   uuid not null references public.courses (id) on delete cascade,
  from_status public.course_status,
  to_status   public.course_status not null,
  actor       text not null,
  created_at  timestamptz not null default now()
);
create index course_transitions_course_id_idx on public.course_transitions (course_id);

-- Idempotence des webhooks Stripe
create table public.stripe_events (
  event_id    text primary key,
  type        text not null,
  received_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- triggers
create function private.set_updated_at() returns trigger
  language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_set_updated_at before update on public.profiles
  for each row execute function private.set_updated_at();
create trigger courses_set_updated_at before update on public.courses
  for each row execute function private.set_updated_at();

-- Profil créé automatiquement à l'inscription
create function private.handle_new_user() returns trigger
  language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, left(coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)), 80))
  on conflict (id) do nothing;
  return new;
end;
$$;
revoke all on function private.handle_new_user() from public, anon, authenticated;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.handle_new_user();

-- ---------------------------------------------------------------- RLS
alter table public.profiles              enable row level security;
alter table public.courses               enable row level security;
alter table public.course_handover_codes enable row level security;
alter table public.course_transitions    enable row level security;
alter table public.stripe_events         enable row level security;

-- Droits minimaux pour les rôles exposés par l'API Data
revoke all on all tables in schema public from anon, authenticated;
grant select on public.profiles, public.courses to authenticated;
grant update (display_name) on public.profiles to authenticated;

create policy profiles_select_own on public.profiles
  for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles
  for update to authenticated using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy courses_select_party on public.courses
  for select to authenticated
  using (client_id = (select auth.uid()) or courier_id = (select auth.uid()));
