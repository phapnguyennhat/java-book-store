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
- Redis 7 on 6379 (refresh tokens and revocation), Mailpit for SMTP.
- LocalStack 4.3 on 4566, S3 only. `localstack/init-s3.sh` creates the private bucket (`S3_BUCKET`) with CORS for browser uploads. Signature validation is on, so presigned URLs behave like real S3. Tests need it running.
- All ports/credentials come from `.env` (git-ignored; copy `.env.example` to create it). Both `docker-compose.yml` and `application.properties` read it (Spring via `spring.config.import=optional:file:.env[.properties]`, resolved from the working directory, so run from the project root). Add new settings to `.env`, `.env.example`, then reference them as `${VAR}`.

## Architecture notes

- Table names are plural (`users`, `books`, `shops`, `shop_kycs`); map entities with `@Table(name = ...)`.
- Flyway owns the schema; Hibernate runs with `ddl-auto=validate`. Change the schema by adding a new migration, never by editing an applied one or by letting Hibernate generate DDL. JPA entities must match the migrated tables.
- Code lives under `com.example.book_store` (underscore, since `book-store` is not a valid package name) so component scanning from `BookStoreApplication` finds it.
- `pom.xml` has intentionally empty `<licenses>`, `<developers>`, `<scm>` elements to stop inheriting them from the Spring Boot parent; leave them.
- Packages: `auth` (JWT cookies, roles), `shop` (shop + KYC), `storage` (S3 presigned URLs), `web` (error handling, i18n, OpenAPI, request logging).
- Errors: throw `ApiErrors.of(status, messageCode, fallback, args...)`; add the code to **both** `messages.properties` and `messages_vi.properties` (`MessagesTests` enforces equal keys). The body carries the untranslated `code` for clients (`validation` for field errors) and the translated `detail` for people.
- Config: `app.*` settings bind to `@ConfigurationProperties` records (`AuthProperties`, `S3Properties`), not `@Value`. Swagger UI / `/v3/api-docs` are off unless `API_DOCS_ENABLED=true` (set in `.env` locally).
- Transactions: `spring.jpa.open-in-view=false`; read methods use `@Transactional(readOnly = true)`; writes use `saveAndFlush` inside the method so constraint/version conflicts become 409s; no S3 or other network calls inside a transaction.
- Roles: `users.role` (USER/ADMIN) goes into the access token's `role` claim at login, so a role change applies after the next login/refresh. `/api/admin/**` requires ADMIN; promote with SQL (`UPDATE users SET role = 'ADMIN' ...`).
- Files never pass through the API: clients get a presigned PUT URL (Content-Type and Content-Length are signed), upload to S3, then submit the object key; the server checks the key's owner prefix and that the object exists.
