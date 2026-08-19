# Chitmon Backend

Backend monorepo for [chitmon.com](https://chitmon.com) — domain-based API modules (pokemon, blog, ...).

## Tech stack

- Java 21 / Spring Boot 4
- Maven (wrapper included — no local Maven install required)
- PostgreSQL 17, with one schema per sub-project (e.g. `pokemon`)
- Flyway for schema migrations, run per-schema on application startup
- Docker / Docker Compose for local development and deployment

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
2. Run the app outside Docker:
   ```
   ./mvnw spring-boot:run
   ```
   (or run/debug `ChitmonBackendApplication` from your IDE)

The app's defaults in `application.yaml` already point at `localhost:5432` when no `SPRING_DATASOURCE_*` env vars are set, so this works with no extra configuration — it connects to the same Postgres container via the port `docker-compose.override.yml` publishes.

### Adding a new schema/migration

See the doc comment on `FlywayMultiSchemaConfig` (`src/main/java/com/chitmon/backend/config/FlywayMultiSchemaConfig.java`) for the convention used to add a new sub-project's schema and migrations.
