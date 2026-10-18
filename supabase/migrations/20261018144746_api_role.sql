-- Rôle dédié à l'API Spring Boot (moindre privilège, pas de BYPASSRLS).
-- Le mot de passe n'est JAMAIS versionné : il est posé hors migration
-- (ALTER ROLE bringme_api PASSWORD ...) et stocké uniquement dans Render.
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'bringme_api') then
    create role bringme_api login noinherit;
  end if;
end
$$;

grant usage on schema public to bringme_api;
grant select, insert, update on public.profiles, public.courses, public.course_handover_codes to bringme_api;
grant select, insert on public.course_transitions, public.stripe_events to bringme_api;
grant usage on all sequences in schema public to bringme_api;

-- L'API applique elle-même les règles d'autorisation métier.
create policy api_all on public.profiles              for all to bringme_api using (true) with check (true);
create policy api_all on public.courses               for all to bringme_api using (true) with check (true);
create policy api_all on public.course_handover_codes for all to bringme_api using (true) with check (true);
create policy api_all on public.course_transitions    for all to bringme_api using (true) with check (true);
create policy api_all on public.stripe_events         for all to bringme_api using (true) with check (true);
