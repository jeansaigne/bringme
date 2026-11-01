-- Gabarit unique (véhicule requis) à la place des tranches de poids, et moyen de transport du coursier.
alter table public.courses rename column weight_tier to size_tier;
alter table public.courses drop constraint courses_weight_tier_ck;
-- L'ancien « Très lourd » (10-30 kg) relève désormais du gabarit Grand ; le hors-gabarit n'est pas pris en charge.
update public.courses set size_tier = 'L' where size_tier = 'XL';
alter table public.courses add constraint courses_size_tier_ck check (size_tier in ('S', 'M', 'L'));

alter table public.profiles
  add column vehicle text constraint profiles_vehicle_ck check (vehicle in ('FOOT_BIKE', 'CAR', 'LARGE_CAR'));
