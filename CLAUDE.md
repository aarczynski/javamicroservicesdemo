# Claude Code Guide — javamicroservicesdemo

## Claude role

Claude must act as a Staff-level Java/Spring engineer:
- Write clean, production-grade code.
- Always use braces `{}` for all `if`, `else`, `for`, `while` blocks — even single-line bodies.
- Follow SOLID, KISS, and YAGNI principles.
- Do not describe code in comments. Extract methods with meaningful names, instead.
- Keep architecture simple, explicit, and maintainable.
- Do not use mutable collections built up in a loop — prefer `Stream`/`IntStream` pipelines with `flatMap` and `toArray`/`toList`.
- Work step by step.
- Respect existing project conventions.
- Avoid overengineering.
- Check for unused code, methods, classes, and tests when implementing changes. Remove unused code.
- Run tests after implementing changes.
- When in doubt, ask whether application code, or tests should be adjusted.

## Goal

Build multi-module Gradle microservices recruitment system consisting of:
- Two microservices:
  - `app-candidates`.
  - `app-job-offers`.
- `observability` (Grafana stack) showing microservices performance.
- `data-generator` building SQL files containing big amount of data.
- `load-test` (Gatling) sending high volume of requests.
- `load-background` (k6) sending continuous 24/7 ambient load.

## Project Context

This is a multi-microservice demo Java project using Spring Boot, JPA/Hibernate, Spock tests, Flyway migrations, Feign clients, and Docker Compose. Each microservice may need its own Postgres instance in Docker.

Technologies used:
- Java
- Spring Boot
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway (database migrations)
- Docker Compose
- Grafana, Prometheus, Loki, Tempo
- Groovy + Spock tests
- OpenFeign

## Base Package

All new code must use base package `pl.lunasoftware.demo.microservices`.

Each microservice should extend this base package, for example:
- `pl.lunasoftware.demo.microservices.joboffers`
- `pl.lunasoftware.demo.microservices.candidates`

## Package Structure — Mandatory

Use domain-based, feature-based packaging.
Do not create top-level global packages such as:
- `controller`
- `service`
- `repository`
Instead, group code by business capability.

## Architecture Rules

Within each feature package:
- Controller handles HTTP only.
- Service contains business logic.
- Repository handles persistence.
- Prefer `FetchType.LAZY` and handle `LazyInitializationExceptions` by `NamedEntityGraph`s. 
- DTOs are separate from entities.
- Exceptions should stay close to the feature when possible.

General rules:
- No business logic in controllers.
- Validate input at API boundaries.
- Keep methods small and clear.
- Keep classes focused.
- Avoid leaking JPA entities directly when DTOs give cleaner boundaries.
- Prefer readability over clever abstractions.
- Keep orchestration logic in services.
- Keep persistence concerns in repositories.
- Keep HTTP mapping concerns in controllers.
- Use constructor injection.
- Design for maintainability and explicit intent.

## Technical Constraints

Claude must:
- Use Gradle multi-module setup.
- Create modules analogous to existing modules.
- Use the same Java version as existing modules.
- Use the same Spring Boot version and compatible dependencies.
- Use Hibernate / Spring Data JPA.
- Use Flyway for PostgreSQL migrations.
- Use Groovy + Spock tests.
- Follow the current repository conventions.

Claude must not:
- Upgrade dependencies without asking.
- Introduce new frameworks without a strong reason.
- Introduce messaging systems such as Kafka or RabbitMQ without being asked.

## Microservices

### app-job-offers

This service does not manage full CRUD for job offers at this stage.
Its main responsibility is to return job offers matched against candidate search criteria.

Suggested endpoint:
- `POST /job-offers/search`

#### Responsibilities
- Accept candidate search criteria:
    - Candidate skills with seniority levels (`Set<CandidateSkillRequest>`)
    - Candidate years of experience (global, not per skill)
    - Candidate location (`geoLat`, `geoLon`)
    - Candidate expected salary
    - Candidate preferred employment types
    - Search radius in kilometers
- Return matching job offers.
- Calculate matching score for each offer.
- Sort results by `score` descending.
- Return score in range:
    - `1.0` = maximum match
    - `0.0` = no match

Search result should return a list of job offers together with computed match score.

At this stage, `app-job-offers` should focus on:
- Storing job offers and related data needed for matching.
- Exposing a search endpoint.
- Calculating score.
- Returning ranked offers.

#### Scoring system

Final score is a weighted sum of four sub-scores, each in `[0.0, 1.0]`:

| Sub-score    | Weight | Formula |
|--------------|--------|---------|
| Skills       | 0.50   | Weighted coverage of offer skills by candidate skills, adjusted for seniority gap (×0.5 per level below required) and mandatory penalty (×0.5 per missing mandatory skill). |
| Salary       | 0.20   | Quadratic decay: `1 - (expectedSalary / salaryTo)²`. Score drops faster near the offer ceiling. |
| Distance     | 0.15   | Haversine distance + cosine decay: `cos(π/2 · d/radius)`. Score=1.0 at distance=0, score=0.0 at the radius boundary. |
| Experience   | 0.15   | Exponential decay: `exp(-0.3 · max(0, requiredYears - candidateYears))`. Required years derived from the highest seniority among mandatory skills (falls back to all skills). Seniority → years mapping: JUNIOR=0, MID=2, SENIOR=5, LEAD=8, PRINCIPAL=12. |

SQL pre-filter (bounding box) runs before scoring to reduce the candidate set cheaply. Haversine is used only in the scoring phase.

#### Domain Model:

**Company**
Fields:
- `id` (UUID)
- `name`
- `geoLat`
- `geoLon`
- `createdAt`
- `updatedAt`

**Skill**
Fields:
- `id` (UUID)
- `name`
- `createdAt`
- `updatedAt`

**JobOffer**
Fields:
- `id` (UUID)
- `companyId` (UUID)
- `title`
- `description`
- `salaryFrom`
- `salaryTo`
- `currency`
- `offeredEmploymentTypes` (set of EmploymentType)
- `geoLat`
- `geoLon`
- `status`
- `createdAt`
- `updatedAt`

**JobOfferSkill**
This is an explicit join entity for the many-to-many relationship between `JobOffer` and `Skill`.

Fields:
- `id` (UUID)
- `jobOfferId` (UUID)
- `skillId` (UUID)
- `requiredSeniorityLevel`
- `isMandatory`
- `weight`
- `createdAt`
- `updatedAt`

Domain notes:
- `Company` has many `JobOffer`
- `JobOffer` belongs to one `Company`
- `JobOffer` has many required skills through `JobOfferSkill`
- `Skill` can be used in many job offers
- the relation between `JobOffer` and `Skill` must NOT be modeled as a plain `@ManyToMany`
- use an explicit join entity because the relation contains additional business fields
- Employment types must be modeled as sets of the same `EmploymentType` enum values.
- In JPA, `offeredEmploymentTypes` should be modeled with `@ElementCollection`, not as standalone domain entities.
- In PostgreSQL, they should be persisted in dedicated collection tables linked to the owning aggregate.

Recommended enums:
- `JobOfferStatus`
- `EmploymentType`
- `SeniorityLevel`

### app-candidates

This service manages candidates and orchestrates job offer search.
It does NOT expose CRUD-oriented API for job applications at this stage.

Its main responsibility is to:
- Store candidate data.
- Build search requests based on candidate profile.
- Call `app-job-offers` via Feign.
- Return matched job offers with score.

Responsibilities:
- Store and manage candidate data (including skills, location, years of experience, salary expectations, preferred employment types).
- Expose a single endpoint for job search based on candidate ID.
- Fetch candidate data from its own database.
- Build a search request for `app-job-offers`.
- Call `app-job-offers` using Feign.
- Return matched job offers with score.

#### Feign rules

- Call job-offers search endpoint.
- Map candidate data to search request.
- Separate client DTOs from internal domain model.
- Keep the Feign interface thin.
- Do not place business logic in the Feign client.
- Keep orchestration logic in the service layer.
- Map client errors explicitly and predictably.

#### Domain model

**Candidate**
Fields:
- `id` (UUID)
- `firstName`
- `lastName`
- `email`
- `phone`
- `geoLat`
- `geoLon`
- `yearsOfExperience`
- `expectedSalary`
- `expectedSalaryCurrency`
- `preferredEmploymentTypes` (set of EmploymentType)
- `createdAt`
- `updatedAt`

#### Skill
Fields:
- `id` (UUID)
- `name`
- `createdAt`
- `updatedAt`

#### CandidateSkill
This is an explicit join entity for the many-to-many relationship between `Candidate` and `Skill`.

Fields:
- `id` (UUID)
- `candidateId` (UUID)
- `skillId` (UUID)
- `seniorityLevel`
- `isPrimary`
- `lastUsedAt`
- `createdAt`
- `updatedAt`

Domain notes:
- `Candidate` has many skills through `CandidateSkill`.
- `Skill` can belong to many candidates.
- The relation between `Candidate` and `Skill` must NOT be modeled as a plain `@ManyToMany`.
- Use an explicit join entity because the relation contains additional business fields.
- Employment types must be modeled as sets of the same `EmploymentType` enum values.
- In JPA, `preferredEmploymentTypes` should be modeled with `@ElementCollection`, not as standalone domain entities.
- In PostgreSQL, they should be persisted in dedicated collection tables linked to the owning aggregate.

Recommended enums:
- `SeniorityLevel`
- `EmploymentType`

## load-background

This module sends continuous 24/7 ambient load to `app-candidates`.
It runs as a Docker Compose service and does **not** contain Java code.

### Purpose
- Provide realistic baseline traffic for observability dashboards.
- Show how the system behaves under continuous load, not just burst tests.

### How it works
- Sends `GET /api/v1/candidates/{id}/matching-offers` requests.
- RPS oscillates sinusoidally between 5 and 10 RPS (period: 5 minutes).
- Candidate UUIDs are read from the SQL file generated by `data-generator` (mounted as a Docker volume).
- The container extracts up to 10 000 UUIDs from the SQL file at startup using `grep`.
- If the data file is not present, the container still runs but requests will return 404.

### Start-up dependency
`app-candidates` and `app-job-offers` must expose `/actuator/health` returning `UP` before k6 starts.
Both services have `healthcheck` configured in `compose.yml`. `load-background` depends on `app-candidates` with `condition: service_healthy`.

### Module structure
```
load-background/
  Dockerfile               — builds the k6 image with the script and entrypoint
  entrypoint.sh            — extracts UUIDs from the SQL file, then runs k6
  src/candidate-search.js  — k6 script with sinusoidal load profile
  build.gradle             — defines runK6 task for standalone execution
```

### Rules for changes to this module
- Keep the k6 script as plain JavaScript — do not introduce TypeScript or bundlers.
- Load profile constants (`MIN_RPS`, `MAX_RPS`, `PERIOD_S`, `VUS`) are defined at the top of the script.
- Do not add thresholds — this module is ambient load, not a pass/fail test.

## Database and Migrations

- PostgreSQL is the database.
- Flyway must be used for schema migrations.
- Migrations must be versioned, deterministic, and reproducible.
- Each microservice should manage its own schema and migration history.
- Do not share the same schema across services as a shortcut.
- Schema design should match service boundaries.

## Modeling Rules

Claude must follow these modeling rules:

- Use UUID as primary keys in all entities.
- Use explicit join entities for many-to-many relationships.
- Do not use plain `@ManyToMany`.
- Model geo coordinates as `geoLat` and `geoLon`.
- `email` in Candidate must be unique.
- Treat join entities as first-class domain objects.
- Keep DTOs separate from entities.
- Keep the model simple and explicit.
- Skill matching is based on skill names, not shared UUIDs across services.
- Each microservice owns its own Skill table and identifiers.

## Suggested Business Meaning of Additional Relation Fields

### JobOfferSkill
- `requiredSeniorityLevel` — expected level of the skill for the offer.
- `isMandatory` — whether the skill is mandatory or optional.
- `weight` — importance of the skill in the context of the offer. Ranges from 0.00 to 1.00.

### CandidateSkill
- `seniorityLevel` — candidate’s declared level in the skill
- `isPrimary` — whether this is one of the candidate’s primary skills
- `lastUsedAt` — when the skill was last actively used

## Logging

All services must produce structured logs.

Rules:
- Log important business actions.
- Log errors with context (input data, identifiers).
- Avoid logging sensitive data.
- Use consistent log format and structure.

Logging is mandatory for every new service and major operation.

## Groovy / Spock Style

- Use double quotes `"..."` for Spock feature method names (`def "should do something"()`).
- Use single quotes `'...'` for all other plain strings (values, identifiers, map keys, assertions). Use double quotes only when string interpolation is required (`"Hello $name"`).
- Use triple single quotes `'''...'''` for multi-line strings without interpolation (e.g. JSON in test assertions).
- For multi-line expected SQL strings in tests, use `"""\\\n|...\n|""".stripMargin()` — keeps the string body indented at the call site while producing clean output without leading whitespace.
- Declare all class-level fields with explicit access modifier: `private` for instance fields, `private static final` for constants.
- Local variables inside feature methods use `def`.
- Always keep static imports. Do not replace `import static` with fully-qualified calls.

## Checking latest dependency versions

When upgrading versions, use `WebFetch` directly on the release pages — do not delegate to an agent and do not rely on search results.

| Dependency               | URL |
|--------------------------|--|
| Gradle                   | https://gradle.org/releases/ |
| Java (LTS releases)      | https://www.java.com/releases/ |
| Java (Docker base image) | https://hub.docker.com/_/eclipse-temurin/tags |
| Spring Boot              | https://mvnrepository.com/artifact/org.springframework.boot/spring-boot |
| Spring Cloud             | https://mvnrepository.com/artifact/org.springframework.cloud/spring-cloud-dependencies |
| Flyway                   | https://mvnrepository.com/artifact/org.flywaydb/flyway-core |
| PostgreSQL JDBC          | https://mvnrepository.com/artifact/org.postgresql/postgresql |
| PostgreSQL (Docker)      | https://www.postgresql.org/docs/release/ |
| Groovy                   | https://mvnrepository.com/artifact/org.spockframework/spock-spring |
| Spock                    | https://mvnrepository.com/artifact/org.spockframework/spock-core |
| Instancio                | https://mvnrepository.com/artifact/org.instancio/instancio-core |
| Gatling                  | https://plugins.gradle.org/plugin/io.gatling.gradle |
| OTEL Java Agent          | https://mvnrepository.com/artifact/io.opentelemetry.javaagent/opentelemetry-javaagent |
| Grafana                  | https://hub.docker.com/r/grafana/grafana/tags |
| Loki                     | https://hub.docker.com/r/grafana/loki/tags |
| Tempo                    | https://hub.docker.com/r/grafana/tempo/tags |
| Prometheus               | https://hub.docker.com/r/prom/prometheus/tags |
| OTEL Collector           | https://hub.docker.com/r/otel/opentelemetry-collector-contrib/tags |

When upgrading Java or Spring Boot, a version bump is not enough:
- Read the official release notes and migration guide.
- Search the codebase for deprecated APIs, annotations, and configuration properties — remove or replace them.
- Update configuration files (application.yml, etc.) if property names or structure changed.
- Verify that all dependencies are compatible with the new version before and after the upgrade.

Always keep the OTEL Java Agent at the latest stable version. The agent instruments third-party libraries (e.g. Logback) via bytecode injection; older versions break silently when the instrumented library is upgraded (e.g. Spring Boot ships a newer Logback). A stale agent version is a common cause of `NoSuchFieldError` / `NoSuchMethodError` at startup.

When upgrading observability components (Grafana, Loki, Tempo, Prometheus, OTEL Collector), a version bump is not enough.
Always check the official release notes and migration guides for breaking changes — ports, protocols, configuration file format, and YAML structure can change between versions.
Blindly bumping the image tag without reviewing the changelog may result in a broken setup.
