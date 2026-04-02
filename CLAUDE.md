# Claude Code Guide — javamicroservicesdemo Extension

## Goal

Extend this multi-module Gradle microservices repository into a recruitment system by adding two new microservices:

- `app-job-offers`
- `app-candidates`

The ultimate goal is to get rid of current `app-company` and `app-company-client` as they are too trivial.

Claude must act as a Staff-level Java/Spring engineer:
- Write clean, production-grade code.
- Follow SOLID, KISS, and YAGNI principles.
- Do not describe code in comments. Extract methods with meaningful names, instead.
- Keep architecture simple, explicit, and maintainable.
- Work step by step.
- Respect existing project conventions.
- Avoid overengineering.
- Check for unused code, methods, classes, and tests when implementing changes. Remove unused code.
- Run tests after implementing changes.
- When in doubt, ask whether application code, or tests should be adjusted.

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

Existing modules include:
- `app-company`
- `app-company-client`
- `data-generator`
- `load-test`
- `observability`

At this stage:
- Keep Docker and Docker Compose unchanged.
- Do not upgrade Java.
- Do not upgrade library versions.
- Do not introduce Kubernetes yet.

Kubernetes will be added later, after the new microservices are implemented and after version upgrades.

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
- Upgrade dependencies at this stage.
- Introduce new frameworks without a strong reason.
- Modify Docker or Docker Compose.
- Introduce Kubernetes.
- Introduce messaging systems such as Kafka or RabbitMQ.

## New Microservices

### app-job-offers

This service does not manage full CRUD for job offers at this stage.
Its main responsibility is to return job offers matched against candidate search criteria.

Suggested endpoint:
- `POST /job-offers/search`

#### Responsibilities
- Accept candidate search criteria:
    - Candidate skills (`list` or `set`)
    - Candidate years of experience (global candidate experience, not per skill)
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

## Development Phases

Claude must work strictly step by step.

### Phase 1 — Skeleton modules
- create `app-job-offers`
- create `app-candidates`
- add modules to `settings.gradle`
- create Gradle build files
- create minimal Spring Boot application classes
- create base package: `pl.lunasoftware.demo.microservices`
- create domain-based (feature-based) package structure
- add basic smoke tests in Groovy + Spock

---

### Phase 2 — Entities, repositories, and Flyway migrations
- implement JPA entities
- implement repositories (Spring Data JPA)
- create Flyway migrations for PostgreSQL
- ensure each service has its own schema/migrations
- add repository tests (Spock)

---

### Phase 3 — Services ✅ (app-job-offers)
- implement service layer
- add business logic
- enforce domain rules
- add validation
- introduce domain exceptions
- keep logic out of controllers
- add service tests (Spock)

#### app-job-offers — implemented services
- `JobOfferService.search(CandidateSearchRequest)` — resolwuje nazwy skillów do ID, filtruje przez `findCandidateMatches`, liczy score = `∑ wag dopasowanych skillów / ∑ wag wszystkich skillów oferty`, sortuje malejąco po score
- Wyjątki domenowe: `ResourceNotFoundException` (404), `BadRequestException` (400) — gotowe do użycia w kolejnych fazach

---

### Phase 4 — Controllers ✅ (app-job-offers)
- implement REST controllers
- create request DTOs
- create response DTOs
- add validation annotations
- use proper HTTP status codes
- implement exception handling
- add controller tests (Spock)

#### app-job-offers — implemented controllers
- `JobOfferController` — `@RestController` na `/api/v1/job-offers`
  - `POST /api/v1/job-offers/search` — wyszukiwanie ofert wg kryteriów kandydata; zwraca `JobOfferMatchDto[]` posortowane malejąco po `score`
- `GlobalExceptionHandler` (`@RestControllerAdvice`) w pakiecie `error`:
  - `ResourceNotFoundException` → 404 + `{"message": "..."}`
  - `BadRequestException` → 400 + `{"message": "..."}`
  - `MethodArgumentNotValidException` (Bean Validation) → 400 + lista błędów pól
- Bean Validation na `CandidateSearchRequest`: `@NotEmpty skillNames`, `@Positive radiusKm`, `@NotNull expectedSalary`, `@NotEmpty preferredEmploymentTypes`
- Testy kontrolera: `@WebMvcTest` + `MockMvc` + `@MockitoBean` (Mockito) w Spocku

---

### Phase 5 — Feign integration
- add Feign client in `app-candidates`
- create separate client DTOs
- fetch candidate data and map it to job-offers search request
- call `app-job-offers` search endpoint
- keep orchestration logic in service layer
- add tests for Feign-related logic

---

### Phase 6 — Observability alignment
- add Spring Boot Actuator
- expose health and metrics endpoints
- align with existing Prometheus/Grafana setup
- keep Docker Compose unchanged
- follow conventions from existing services

---

### Phase 7 — Later (not now)
- upgrade Java version
- upgrade Spring Boot and dependencies
- upgrade build tools
- introduce Kubernetes (K8S)
- add advanced scalability and resilience patterns


