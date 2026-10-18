# BringMe

Marketplace de courses entre particuliers : un client publie une course (« va me chercher X à Y, livre à Z »),
n'importe quel utilisateur inscrit peut l'accepter et devenir coursier. Walking skeleton, Stripe en **mode test**.

| Dossier | Contenu |
|---|---|
| `api/` | Spring Boot 3.5, Java 21, Gradle — prix, machine à états, paiements, webhooks |
| `web/` | Vue 3 + TypeScript + Vite — SPA (hash routing) |
| `supabase/migrations/` | Schéma versionné (RLS active partout) |
| `.github/workflows/ci.yml` | Lint, tests et build pour `api` et `web` |

## Parcours couvert

1. Inscription (Supabase Auth), création d'une course, prix calculé par l'API.
2. Paiement du total via Stripe Checkout (compte plateforme, charges et transferts séparés).
3. Un autre utilisateur devient coursier (compte Connect **Accounts v2**, configuration `recipient`,
   onboarding par composant intégré), puis accepte la course.
4. Le coursier confirme la livraison avec le code de remise du client.
5. L'API vérifie que la capacité `stripe_transfers` du coursier est active, puis lui transfère prix − commission.
6. Une annulation avant acceptation rembourse intégralement le client.

## Machine à états

```
PENDING_PAYMENT --paiement--> PAID --accept--> ACCEPTED --code OK--> DELIVERED --transfert--> PAID_OUT
PENDING_PAYMENT --expiration--> EXPIRED          PENDING_PAYMENT --annulation--> CANCELLED
PAID --annulation--> REFUND_PENDING --remboursement--> REFUNDED
CANCELLED | EXPIRED --paiement tardif--> REFUND_PENDING
```

Source de vérité : `api/src/main/java/app/bringme/api/course/CourseStateMachine.java`.
Chaque transition est un `UPDATE` conditionné à la version et au statut lus (verrou optimiste) et est journalisée
dans `course_transitions`.

## Grille tarifaire

`prix = 3,00 € + 0,50 € × km commencés au-delà de 2 km + 1,00 € × kg commencés` ; commission 20 % (arrondi au
centime, half-up), le reste au coursier. Distance à vol d'oiseau entre les adresses géocodées par l'API
(Géoplateforme IGN) — le client ne peut pas fournir ses propres coordonnées.

## Sécurité

- L'API valide les JWT Supabase (JWKS si clés asymétriques, sinon validation auprès de Supabase Auth).
- Le front ne détient que des clés publiques (`pk_test`, clé publishable Supabase).
- L'API se connecte avec un rôle Postgres dédié `bringme_api` (pas de `BYPASSRLS`, droits minimaux) ; son mot
  de passe n'est jamais versionné.
- Webhooks Stripe : signature HMAC vérifiée (tolérance 5 min), idempotence par `stripe_events.event_id` dans la
  même transaction que la transition.

## Développement local

```bash
# API (Postgres requis ; tests d'intégration via Testcontainers, donc Docker)
cd api && gradle test && gradle bootRun

# Front
cd web && cp .env.example .env.local && npm ci && npm run dev
```

Variables de l'API : `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `WEB_URL`, `STRIPE_SECRET_KEY`,
`STRIPE_WEBHOOK_SECRET`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `COMMISSION_RATE`.
