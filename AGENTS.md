# Backend Development Guidelines

## Project scope

- This repository contains the backend for the university grade-validation system.
- Preserve existing user changes and do not commit, push, or rewrite Git history unless explicitly requested.
- Never add real applicant data, student records, resident registration numbers, credentials, or other sensitive information to source code, fixtures, logs, or documentation.

## Technology

- Use Java 21, Spring Boot, Gradle, Spring Data JPA, MySQL, and Flyway.
- Use the Gradle wrapper (`gradlew` or `gradlew.bat`) instead of a globally installed Gradle.
- Do not add a production dependency without explaining its purpose and verifying that an existing dependency cannot cover the requirement.

## Package architecture

- Use the base package `com.jinhakapply.gradevalidation`.
- Organize code by feature first, then by responsibility within each feature.
- Recommended top-level features include `applicant`, `transcript`, `evaluation`, `rule`, and `verification`.
- Keep cross-cutting code under `global`, such as `global.config`, `global.exception`, and `global.response`.
- Within a feature, use packages such as `controller`, `service`, `repository`, `domain`, and `dto` only when they are needed.
- Controllers must depend on services, and services may depend on repositories. Repositories must not depend on controllers or services.
- Keep score-calculation rules out of controllers and persistence entities.

## DTO and API rules

- Define endpoint mappings and OpenAPI annotations in feature-specific `*Api` interfaces, and have controllers implement those interfaces.
- Do not expose JPA entities directly through HTTP request or response bodies.
- Use separate request and response DTOs; Java records are preferred for immutable DTOs.
- Apply Bean Validation to incoming request DTOs and handle validation failures consistently.
- Repositories should normally return entities or projections. Do not create mechanical DTOs between every internal method call.
- Return enough calculation detail to explain how a final score was produced, including the applied rule version, selected inputs, calculation steps, warnings, and exclusion reasons when relevant.

## JPA and database rules

- Do not use Lombok `@Data` on JPA entities.
- Prefer Lombok `@Getter` over manually implemented boilerplate getters on entities and simple model classes.
- Avoid public setters on entities; express state changes through meaningful domain methods.
- Define transaction boundaries in the service layer. Use read-only transactions for queries where appropriate.
- Avoid unbounded collection loading and N+1 queries. Add fetch joins, entity graphs, or projections only after confirming the query requirement.
- Manage schema changes with versioned Flyway migrations under `src/main/resources/db/migration`.
- Do not use Hibernate automatic schema creation as a substitute for migrations.
- Do not commit database passwords. Read connection details from environment variables.

## Exception handling

- Represent API error types with `ApiResponseCode` and throw them through `CustomException.of(...)`.
- Handle `CustomException` centrally in `GlobalExceptionHandler`; do not create one exception class for each resource or status code.

## Testing and verification

- Add unit tests for calculation and eligibility rules, especially boundary values, rounding, truncation, tie-breaking, and rule-version changes.
- Use integration tests for repository mappings and database-specific behavior.
- When modifying backend code, run on Windows:

```powershell
.\gradlew.bat test
```

- Do not claim completion when compilation or relevant tests fail. Report any failure and its cause.

## Git scope

- Treat this directory as an independent Git repository.
- Run Git commands from this repository, not from the parent `project` or `입학관리` directory.
- Do not include frontend or surrounding business-document files in backend commits.
- Work on `develop` unless the user requests another branch.
- Keep commits small and focused on one coherent change, and push completed work when the user has explicitly authorized it.
