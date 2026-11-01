-- Poids déclaré : passage d'un poids libre (kg) à des tranches (S, M, L, XL).
-- Les bornes et suppléments vivent dans la configuration de l'API ; la base ne garde que le code.
alter table public.courses add column weight_tier text;

update public.courses
set weight_tier = case
  when weight_kg <= 2 then 'S'
  when weight_kg <= 5 then 'M'
  when weight_kg <= 10 then 'L'
  else 'XL'
end;

alter table public.courses
  alter column weight_tier set not null,
  add constraint courses_weight_tier_ck check (weight_tier in ('S', 'M', 'L', 'XL'));

alter table public.courses drop column weight_kg;
