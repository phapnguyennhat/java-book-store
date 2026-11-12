# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

A learning project for Spring Boot. Prefer the common, mainstream tool for each job over clever alternatives:

| Concern | Tool |
|---|---|
| Web | Spring MVC (`spring-boot-starter-webmvc`) |
| API docs | SpringDoc OpenAPI / Swagger UI (`/swagger-ui.html`, `/v3/api-docs`) |
| Boilerplate | Lombok |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Migrations | Flyway (`src/main/resources/db/migration`, `V<n>__name.sql`) |
| Messaging | RabbitMQ (`spring-boot-starter-amqp`) |

Java 21, Spring Boot 4.1.2 (Boot 4 split starters: Flyway needs `spring-boot-starter-flyway` plus `flyway-database-postgresql`).

## Commands

Maven wrapper; no linter configured.

- Start infrastructure: `docker compose up -d` (stop: `docker compose down`; add `-v` to wipe the DB)
- Run app: `./mvnw spring-boot:run`
- Build + test: `./mvnw verify`
- Single test class: `./mvnw test -Dtest=BookStoreApplicationTests`
- Single method: `./mvnw test -Dtest=BookStoreApplicationTests#contextLoads`

**Tests are `@SpringBootTest` against the real containers**, so `docker compose up -d` must be running first and `.env` must define `JWT_SECRET` or the context fails to start.

## Infrastructure (docker-compose.yml)

- PostgreSQL 17 on host port **5433** (not 5432, which another local project's container occupies), db/user/password all `bookstore`.
- RabbitMQ 4 on 5672, management UI at http://localhost:15672, user/password `bookstore`.
- All ports/credentials come from `.env` (git-ignored; copy `.env.example` to create it). Both `docker-compose.yml` and `application.properties` read it (Spring via `spring.config.import=optional:file:.env[.properties]`, resolved from the working directory, so run from the project root). Add new settings to `.env`, `.env.example`, then reference them as `${VAR}`.

## Architecture notes

- Flyway owns the schema; Hibernate runs with `ddl-auto=validate`. Change the schema by adding a new migration, never by editing an applied one or by letting Hibernate generate DDL. JPA entities must match the migrated tables.
- Code lives under `com.example.book_store` (underscore, since `book-store` is not a valid package name) so component scanning from `BookStoreApplication` finds it.
- `pom.xml` has intentionally empty `<licenses>`, `<developers>`, `<scm>` elements to stop inheriting them from the Spring Boot parent; leave them.
- Only a `book` table migration (V1) and the application class exist so far; no entities, controllers, or RabbitMQ queues yet.
