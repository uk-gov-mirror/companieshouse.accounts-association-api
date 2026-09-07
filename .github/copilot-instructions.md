# Copilot Instructions — accounts-association-api

## Service Overview

Spring Boot REST API (Java 21) that manages associations (links) between
Companies House users and companies — invitations, auth-code approvals,
confirmation/removal, and delegated admin access. Backed by MongoDB. Called
by frontend services (e.g. your-companies-web) and internally by other CH
services via API key; it in turn calls the Users, Company Profile, Oracle
Query, and Kafka/Email APIs.

**Type:** java-api
**Team:** Phoenix
**Jira project:** SIV

---

## Key Dependencies

| Service | Relationship |
|---|---|
| `private-api-sdk-java` | Generated OpenAPI client/server models for this API's controllers (`uk.gov.companieshouse.api.accounts.associations.*`) — the spec, not the Java code, is the source of truth for request/response shapes |
| `api-sdk-manager-java-library` / `api-helper-java` | Internal API client plumbing |
| accounts-user-api (Users API) | Resolves user details from `ERIC-Identity` |
| company-profile-api (via Company WebClient) | Resolves company details |
| Oracle Query API | Company officer/auth-code lookups |
| chs-kafka-api | Sends emails (invitation, confirmation, auth-code-added) |
| MongoDB | Persists associations (`AssociationDao`, `InvitationDao`, `PreviousStatesDao`) |

---

## Local Development

Requires Java 21, Maven, and Docker (integration tests use Testcontainers to
spin up a real MongoDB instance — Docker must be running).

```sh
make build              # mvn package (skips tests), produces accounts-association-api.jar
make test               # mvn verify — full suite (unit + integration)
make test-unit          # mvn test -DexcludedGroups="integration-test"
make test-integration   # mvn test -Dgroups="integration-test"
```

Tests are grouped with JUnit `@Tag("unit-test")` / `@Tag("integration-test")`.
Integration tests live under `src/test/.../integration/` and extend
`BaseMongoIntegration`, which starts a `mongo:7.0.17-jammy` Testcontainer.

To run a single test class or method directly with Maven (bypassing the
Makefile's group filtering):
```sh
mvn test -Dtest=UserCompanyAssociationsTest
mvn test -Dtest=UserCompanyAssociationsTest#addAssociationWithNullRequestBodyReturnsBadRequest
```

There is no local Docker Compose setup for running the service standalone —
it's normally run via `docker-chs-development` (`chs-dev services enable
accounts-association-api`).

**Required environment variables** (see `src/main/resources/application.properties`):
- `MONGODB_URL`, `MONGODB_DATABASE` — MongoDB connection
- `API_URL` — private API base URL
- `ACCOUNT_URL` — Users API base URL
- `ORACLE_QUERY_API_URL` — Oracle Query API base URL
- `CHS_URL` — used to build invitation links
- `ACCOUNTS_USER_INTERNAL_API_KEY` — internal API key
- `KAFKA_API_URL` — chs-kafka-api base URL (defaults to a dev URL if unset)

---

## Architecture and Design Decisions

- **Controllers** (`controller/`) implement generated interfaces from the
  `private-api-sdk-java` OpenAPI spec (package
  `uk.gov.companieshouse.api.accounts.associations.*` — request/response
  models like `Association`, `AssociationsList`, `RequestBodyPost`,
  `RequestBodyPut` are generated, not hand-written). Controllers are thin:
  validate request params, delegate to services, return `ResponseEntity`.
- **Services** (`service/`) hold business logic and orchestrate calls to Mongo
  repositories, and external CH APIs (Users, Company Profile, Oracle Query,
  Email/Kafka) via WebClients configured in `configuration/`.
- **DAOs vs API models**: `models/AssociationDao.java`,
  `InvitationDao.java`, `PreviousStatesDao.java` are the MongoDB persistence
  shape (snake_case fields). Mappers (`mapper/*Mapper.java`) convert DAOs to
  the generated API response models. Never return a DAO directly from a
  controller — always map it first.
- **Update pattern**: association status changes (confirm, remove, invite,
  auth-code-approve) are built as Spring Data `Update` objects in
  `utils/AssociationsUtil.java` (`mapToInvitationUpdate`,
  `mapToConfirmedUpdate`, `mapToRemovedUpdate`, `mapToAuthCodeConfirmedUpdated`,
  etc.), not by mutating and re-saving a loaded DAO. Every status change also
  pushes a snapshot onto the `previous_states` array
  (`PreviousStatesDao`) for audit history, and regenerates `etag`.
- **Auth model** is two-layered:
  1. `filter/UserAuthenticationFilter` (Spring Security filter, runs on every
     request) inspects `ERIC-Identity`/`ERIC-Identity-Type`/`ERIC-Authorised-*`
     headers and assigns Spring `ROLE_*` authorities (`SpringRole` enum:
     `KEY_ROLE`, `BASIC_OAUTH_ROLE`, `ADMIN_READ_ROLE`, `ADMIN_UPDATE_ROLE`).
  2. `configuration/WebSecurityConfig` maps each endpoint+HTTP-method to the
     roles allowed to call it (`hasAnyRole(...)`); unmatched requests are
     `denyAll()`.
  3. `interceptor/RequestLifecycleInterceptor` (a servlet `HandlerInterceptor`,
     separate from the security filter) resolves the calling `User` from the
     Users API and stores per-request context (`RequestContextData`, accessed
     via `utils/RequestContextUtil`) used throughout services/controllers —
     this is how `getXRequestId()`, `getEricIdentity()`, `getUser()` work
     without threading params everywhere.
- **Errors**: services throw `exceptions/*RuntimeException` (`NotFoundRuntimeException`,
  `BadRequestRuntimeException`, `ForbiddenRuntimeException`,
  `InternalServerErrorRuntimeException`). `controller/ControllerAdvice` maps
  these (and `ConstraintViolationException`) to the CH-standard `Errors`
  response body and the corresponding HTTP status — don't catch these in
  controllers/services, let them propagate.
- **Emails**: `service/EmailService` builds `models/email/data/*EmailData`
  via `models/email/builders/*`, wraps them with `factory/SendEmailFactory`
  into a `SendEmail` (chs-kafka-api model), and sends asynchronously via
  reactive `Mono`/`Flux` chains (see `UserCompanyAssociations.addAssociation`
  for the pattern: fire-and-forget `.subscribe()` after the main response is
  built — don't block the HTTP response on email sending).
- **Logging**: use `utils/LoggingUtil.LOGGER` (`structured-logging` library)
  with `xRequestId` context on every log call. Never log user email/name/PII —
  log user IDs and company numbers only.

## Known Gotchas

- Static imports are used heavily for utils/constants (`Constants`,
  `RequestContextUtil`, `LoggingUtil.LOGGER`, enum values like `StatusEnum.CONFIRMED`)
  — follow this style rather than fully-qualifying calls inline.
- Method params use CH house style with a space inside parens: `method( arg1, arg2 )`.
- New API endpoints are added by updating the OpenAPI spec that generates
  `private-api-sdk-java` (external repo/dependency) first, then implementing
  the generated `*Interface` in a controller — the spec is the source of
  truth for request/response shapes, not the Java classes in this repo.
- Integration tests require Docker to be running (Testcontainers pulls
  `mongo:7.0.17-jammy`); they will hang/fail without it.

---

<!-- CHS-STANDARDS: java-api -->
<!-- Generated by ch-ai-dev/bin/apply-standards — do not edit this section manually -->
<!-- ch-ai-dev-commit: 778faf0ee -->

# CHS Java Development Standards

These standards apply to **all** Companies House Java services and libraries
regardless of service type. Service-type-specific standards (`java-api.md`,
`java-web.md`, `java-library.md`) supplement this file — read this first.

**Authoritative style reference:** [`companieshouse/styleguides` — Java](https://github.com/companieshouse/styleguides/blob/main/standards/java.md)
(based on Google Java Style Guide with CH modifications: 4-space indent,
130-char line length, K&R braces). All code must also pass the
[CH Checkstyle configuration](https://github.com/companieshouse/java-checkstyle-config).

---

## 1. Code Style

> **[CH Required]** All Java code must pass the [CH Checkstyle configuration](https://github.com/companieshouse/java-checkstyle-config). The CI pipeline enforces this.
>
> **[CH Required]** No Lombok. Write explicit constructors, getters, and setters. Source: [CH Java styleguide](https://github.com/companieshouse/styleguides/blob/main/standards/java.md)
>
> **[CH Required]** Constructor injection only — never `@Autowired` on fields or setters. Source: [CH Java styleguide](https://github.com/companieshouse/styleguides/blob/main/standards/java.md)

### General
- Use `var` for local variables where the type is obvious from the right-hand
  side (Java 17+); do not use it when the type aids readability
- `static final` constants over inline literals; group related constants in a
  dedicated `Constants` class (or `JsonConstants` for JSON field names)
- Comments only where the code genuinely needs clarification; self-explanatory
  code needs no comment
- Always use `@Override` when overriding methods
- Javadoc required on all public classes and public/protected members (unless
  self-evident like `getId()`); `@param`, `@return`, `@throws` in that order

### Naming
- Method and variable names must be self-documenting — avoid abbreviations
  and single-letter names outside loop counters
- Non-obvious design decisions must have a comment explaining **why**, not what
- Keep methods short and focused — a method that needs a comment to explain
  its own structure should be split

### Exception handling
- Never swallow exceptions silently — always log with full context and
  either rethrow or return an appropriate error response
- Include the full exception object in error log calls, not just `e.getMessage()`
- When wrapping a caught exception in a custom type, always pass the original as
  the cause — `throw new MyException("message", e)` — never construct from
  `e.getMessage()` alone; the stack trace is otherwise silently discarded,
  making production diagnosis much harder

---

## 2. Logging

> **[CH Required]** Use the Companies House structured logger throughout. Do not use `System.out.println` or other ad-hoc logging. Source: CH convention.
>
> **[CH Required]** PII must never appear in logs at any level. Log IDs (UUIDs, userIds, company numbers), never values (names, emails, document details). Source: [Secure SDLC](https://companieshouse.atlassian.net/wiki/spaces/DEV/pages/973242435)

Use `LoggerFactory.getLogger(ApplicationNamespace.APPLICATION_NAMESPACE)` and always pass a `Map<String, Object>` context — never concatenate values into the message string.

| Level | When to use |
|---|---|
| `DEBUG` | Detailed trace. Suppressed in production. |
| `INFO` | Significant business events. |
| `WARN` | Recoverable issues; unexpected but handled state. |
| `ERROR` | Exceptions — always include the full exception object, never just `e.getMessage()`. |

---

## 3. Testing

### Framework
- JUnit 5 only (`org.junit.jupiter.*`) — never add or use JUnit 4 (`org.junit.*`)
- `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks`
- `assertThrows` from `org.junit.jupiter.api.Assertions`, not JUnit 4 `Assert`
- Test class names mirror the class under test with a `Test` suffix

### Quality
- Test names describe expected behaviour, not implementation: prefer
  `shouldReturn404WhenCompanyNotFound` over `testGetCompany`
- Mock external service calls with Mockito; never rely on live endpoints
- Tests must fail before the implementation exists — do not write tests that
  pass against a stub

---

## 4. Maven and Dependencies

### BOM imports

> **[CH Required]** All CHS Java services must import `ch-dependency-bom` and
> `ch-test-bom` in `<dependencyManagement>`. Do not redeclare version numbers
> for any dependency managed by those BOMs. Source: CH Maven convention.

### Dependency discipline
- If the service code directly imports or uses a class from a library, that
  library must be declared explicitly in `<dependencies>` — do not rely on
  transitive dependencies
- Do not add a dependency to `pom.xml` that is never imported in the codebase
- Keep `<dependencyManagement>` and `<dependencies>` free of duplicates
- Test-scoped dependencies must use `<scope>test</scope>`
- Remove unused dependencies

### Checking dependency versions
CH libraries are published to Artifactory at `https://chartifacts.jfrog.io` — always check here first for CH library versions, not Maven Central. Credentials are in `~/.m2/settings.xml`. Running `mvn dependency:tree` locally resolves from Artifactory automatically.

---

## 5. Local Development

CHS Java services run in the Docker CHS development environment (`docker-chs-development`) via `chs-dev`. Run tests directly with `./mvnw test`. Environment variables go in `.env` at the repo root (gitignored).

# CHS Java API Standards

These standards apply to Companies House Java microservices (Spring Boot
REST APIs). They supplement `core.md` and the common Java standards in
`java.md` — read both of those first.

---

## 1. Spring Boot

- Annotate services with `@Service`, controllers with `@RestController`
- Return `ResponseEntity<?>` from all controller methods
- Use `@Value` for injecting individual configuration properties; do not use
  `@ConfigurationProperties` unless the repo already uses it
- Keep `@Transactional` scope as narrow as possible — method level, not class level
- Do not put business logic in controllers — delegate to service classes

### Controllers
- Validate and sanitise all input at the controller boundary before passing
  to service layer
- Map every expected failure case to an appropriate HTTP status code
- Do not leak internal exception details in error responses returned to clients

### Entities and repositories
- Avoid N+1 queries — use `JOIN FETCH` or projections rather than
  lazy-loading in loops
- Use DTO projections (interface-based or record-based) for list endpoints —
  do not fetch full entities when only a subset of columns is needed
- Where multiple independent queries are needed in a single request, consider
  `CompletableFuture` for concurrent execution
- Escalate to the team if a feature requires a table scan without an indexed filter

---

## 2. Testing

### Coverage expectations
- Unit tests for all service layer logic
- Integration tests for all API endpoints: happy path, validation failures,
  auth failures, not-found cases
- Every acceptance criterion must have at least one test
- Test security: verify unauthorised requests are rejected with 401/403

---

## 3. API Design

### REST conventions
- Use nouns not verbs in paths: `/companies/{companyNumber}/officers` not
  `/getCompanyOfficers`
- Use HTTP methods correctly: GET (read), POST (create), PUT (replace),
  PATCH (partial update), DELETE (remove)
- Return 201 Created (with Location header) for successful POST operations
  that create a resource
- Return 204 No Content for DELETE operations
- Use consistent error response bodies across all endpoints

### Versioning
- Version APIs via path prefix: `/v1/`, `/v2/`
- Do not make breaking changes to an existing version — add a new version
  instead

### Security
- Every endpoint must have explicit authorisation — there is no default of
  "allow if authenticated"
- Check access at the service layer, not just the controller
- Document the access control requirements for every endpoint

<!-- END CHS-STANDARDS -->
