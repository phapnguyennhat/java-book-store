# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Maven wrapper, Java 21, Spring Boot 4.1.2. No linter is configured.

- Run: `./mvnw spring-boot:run`
- Build + test: `./mvnw verify`
- All tests: `./mvnw test`
- Single test class: `./mvnw test -Dtest=BookStoreApplicationTests`
- Single method: `./mvnw test -Dtest=BookStoreApplicationTests#contextLoads`

## State of the project

Freshly generated Spring Initializr skeleton: only `spring-boot-starter` and `spring-boot-starter-test` are declared (no web, JPA, or database), and the only code is `BookStoreApplication` plus a context-load test. Add starters to `pom.xml` as features need them.

## Gotchas

- The package is `com.example.book_store` (underscore), because `book-store` is not a valid Java package name. Put new code under this package so component scanning from `BookStoreApplication` picks it up.
- `pom.xml` has intentionally empty `<licenses>`, `<developers>`, `<scm>` elements to stop inheriting them from the Spring Boot parent; leave them.
