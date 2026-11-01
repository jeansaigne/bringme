-- Instructions de livraison saisies par le client, visibles du seul coursier ayant accepté la course
-- (règle appliquée par l'API ; la politique RLS existante limite déjà la lecture au client et au coursier).
alter table public.courses
  add column instructions text constraint courses_instructions_ck check (char_length(instructions) <= 1000);
