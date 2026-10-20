# Chitmon Backend

Backend for [chitmon.com](https://chitmon.com), organized into user and Pokémon domains. The Angular frontend is in the sibling `personal-site` repository; its [architecture notes](../personal-site/docs/architecture.md) describe the complete system and verified homelab setup (reviewed 2026-10-20).

## Tech stack

- Java 21 / Spring Boot 4
- Maven (wrapper included — no local Maven install required)
- PostgreSQL 17, with one schema per sub-project (e.g. `pokemon`)
- Flyway for schema migrations, run per-schema on application startup
- Docker / Docker Compose for local development and deployment

## Architecture

- Spring MVC controllers delegate to domain services; persisted domains use JPA repositories. WebClient handles external HTTP calls.
- Supabase provides hosted Google OAuth and sessions. Spring validates ES256 access tokens using Supabase JWKS, issuer, expiry, and audience. The API is stateless; `UserSyncFilter` mirrors authenticated user profile claims into `core.users`.
- `/me` and `/users/**` require authentication; other routes are currently public. New sensitive endpoints need explicit rules in `SecurityConfig`. Frontend admin guards are not backend authorization.
- Active PostgreSQL schemas are `core` (shared users) and `pokemon` (bootstrap only). Each has an independent Flyway history; migrations run before Hibernate schema validation. Add new versioned migrations rather than editing applied ones.
- Pokémon responses come from PokeAPI. Shipment tracking, its carrier endpoints, and its scraper have been removed. The retired `shipment_tracker` schema is no longer managed or created on fresh installations; its original V1 migration remains unchanged for history, and existing tables/data are preserved.
- Cat/resume `/supabase/...` URLs work on the public API host through additional routing outside Spring. Direct requests to Spring return 404 for those paths; the routing configuration was not inspected.

## Local setup

### Prerequisites

- Docker + Docker Compose plugin
- Java 21, only if you want to run the app outside Docker (see [Alternate workflow](#alternate-workflow-running-the-app-from-your-ide) below) — no separate Maven install needed, this repo includes the Maven wrapper (`./mvnw`)

### 1. Configure environment variables

```
cp .env.example .env
```

The defaults work out of the box for local dev: local Postgres credentials, and `SPRING_PROFILES_ACTIVE=local` (relaxes CORS for an Angular dev server at `localhost:4200`). `.env` is git-ignored — never commit real credentials.

### 2. Run everything via Docker Compose

```
docker compose up --build
```

This builds the backend image from the local `Dockerfile` and starts it alongside Postgres. `docker-compose.override.yml` is picked up automatically alongside `docker-compose.yml` and publishes Postgres to `localhost:5432` for local tooling (DBeaver, `psql`, etc.) — this override file is local-only and is never deployed to production.

What comes up:
- `personal-site-service` on http://localhost:8080
- `postgres` (Postgres 17), reachable at `localhost:5432`

On startup, the app automatically creates/migrates each managed schema via Flyway — see `FlywayMultiSchemaConfig`.

Data persists across restarts in the named volume `postgres-data`:
- `docker compose down` — stops containers, keeps the volume (and your data)
- `docker compose down -v` — also deletes the volume (**all local data is lost**) — only do this deliberately

### 3. Verify it's working

```
curl http://localhost:8080/actuator/health
curl http://localhost:8080/pokemon/random
```

Inspect the database directly:

```
docker compose exec postgres psql -U chitmon_app -d chitmon -c "\dn"
```

### Alternate workflow: running the app from your IDE

For day-to-day development (hot reload via `spring-boot-devtools`), run Postgres in Docker while running the app directly from your IDE or Maven:

1. Start only Postgres:
   ```
   docker compose up postgres -d
   ```
2. Run the app outside Docker with the `local` profile active (this enables CORS for an Angular dev server on `localhost:4200` — see `CorsConfig`; without it, cross-origin requests from a local frontend get rejected):
   ```
   SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
   ```
   (or, from your IDE, set the `SPRING_PROFILES_ACTIVE=local` env var / active profile on the `ChitmonBackendApplication` run/debug config)

The app's defaults in `application.yaml` already point at `localhost:5432` when no `SPRING_DATASOURCE_*` env vars are set, so this works with no extra configuration — it connects to the same Postgres container via the port `docker-compose.override.yml` publishes.

### Adding a new schema/migration

See the doc comment on `FlywayMultiSchemaConfig` (`src/main/java/com/chitmon/backend/config/FlywayMultiSchemaConfig.java`) for the convention used to add a new sub-project's schema and migrations.

## Production deployment

Pushes to `main` trigger `.github/workflows/deploy.yml`: a GitHub-hosted job builds and publishes the container to GHCR with `latest` and commit tags. A self-hosted runner on the homelab then copies only `docker-compose.yml` into `/opt/personal-site-service`, pulls images, and updates the stack.

`api.chitmon.com` reaches the Spring application on `localhost:8080` through Cloudflare Tunnel. PostgreSQL runs in the same Compose stack. The homelab has a manually maintained override that publishes PostgreSQL on `127.0.0.1:5432` for SSH-tunneled tools; preserve this file and never copy the development override into production.

The public and local `/actuator/health` endpoints returned `UP` during the 2026-10-20 review, and the then-active `core`, `pokemon`, and `shipment_tracker` schemas had successful V1 migrations. Only `core` and `pokemon` are managed after shipment tracking is retired. These are dated observations; recheck live state before deployment changes.

## Verification

Run tests with `./mvnw test` (or `./mvnw.cmd test` on Windows). Current automated coverage is a Spring context-load test and requires a reachable configured database. The Docker build packages with tests skipped; deployment is not a substitute for testing changes.
