# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

- **Spring Boot 4.0.6** on **Java 21** (not Java 17 — toolchain is set to 21)
- Gradle Kotlin DSL (`build.gradle.kts`)
- PostgreSQL + Liquibase migrations, JPA/Hibernate with `ddl-auto: validate`
- Kafka (KRaft mode in Docker), Spring Kafka consumer/producer
- Redis via `StringRedisTemplate` for rate limiting
- Lombok throughout — all entities and services use `@RequiredArgsConstructor`

## Commands

```bash
# Build
./gradlew build

# Run tests (unit only when Docker is absent; integration tests auto-skip)
./gradlew test

# Run a single test class
./gradlew test --tests "tj.tajnav.notificationservice.domain.NotificationStatusTransitionTest"

# Run a single test method
./gradlew test --tests "tj.tajnav.notificationservice.template.TemplateRendererTest.interpolate_replacesAllPlaceholders"

# Build fat jar for Docker
./gradlew bootJar

# Start full stack (wipe volumes first if schema changed)
docker compose down -v && docker compose up --build

# Start infra only (run app locally with bootRun)
docker compose up postgres redis kafka
./gradlew bootRun
```

## Spring Boot 4 notes

Spring Boot 4 split auto-configurations into per-technology modules and removed several that existed in Boot 3. Non-obvious things:

- **Liquibase has no auto-configuration.** It must be wired manually in `config/LiquibaseConfig`. The bean is `SpringLiquibase`; the ordering guarantee (run before Hibernate validates the schema) is provided by `EntityManagerFactoryDependsOnPostProcessor("liquibase")` from `spring-boot-jpa`. Without this post-processor, Hibernate validation races against Liquibase and fails with *"missing table"*.
- **`@AutoConfigureMockMvc`** moved to `org.springframework.boot.webmvc.test.autoconfigure` — requires the `spring-boot-starter-webmvc-test` test dependency (not included in `spring-boot-starter-test`).
- The old `spring-boot-starter-webmvc` is now `spring-boot-starter-web` (or `spring-boot-webmvc` directly).

## Architecture

The request flow is:

```
POST /api/v1/notifications
  → NotificationController          (api)
  → NotificationService             (application)  — rate-limits via Redis, persists, publishes to Kafka
  → Kafka topic: notification.requests
  → NotificationConsumer            (messaging)    — transitions ACCEPTED→PROCESSING, renders, sends, transitions →SENT|FAILED
```

### Package responsibilities

| Package       | Key classes                                        | Notes |
|---------------|----------------------------------------------------|-------|
| `api`         | `NotificationController`, `GlobalExceptionHandler` | Returns 202; maps `RateLimitExceededException` → 429 |
| `application` | `NotificationService`                              | Single `submit()` method; owns the Kafka topic constant |
| `domain`      | `NotificationStatus`, `InvalidStatusTransitionException` | Status transitions are enforced inside the enum via a static allowed-map |
| `persistence` | `NotificationEntity`, `NotificationTemplateEntity` | `NotificationEntity.transitionTo()` calls `status.validateTransitionTo()` before mutating |
| `messaging`   | `NotificationConsumer`, `NotificationMessage`      | Consumer is `@Transactional`; failure path catches all exceptions and marks FAILED |
| `template`    | `TemplateRenderer`                                 | `{{placeholder}}` interpolation; looks up content from `notification_templates` table |
| `sender`      | `AbstractStubSender`, `SenderRegistry`             | Registry is auto-built from all `NotificationSender` beans; stubs read `app.sender.*` properties |
| `ratelimit`   | `RateLimiter`                                      | Redis `INCR` + `EXPIRE`; max 5/min per `recipientId` |
| `config`      | `KafkaConfig`, `SecurityConfig`                    | Security is permit-all (no auth); Kafka topic bean declares 3 partitions |

### Status transition rules (enforced in `NotificationStatus`)

```
ACCEPTED → PROCESSING → SENT
                      → FAILED
```

Any other transition throws `InvalidStatusTransitionException`. The transition logic lives in the enum itself (not a service), tested in `NotificationStatusTransitionTest`.

### Schema management

Liquibase runs before Hibernate validation on every startup. Changelogs are in `src/main/resources/db/changelog/`:

- `001` — `notifications` table
- `002` — `notification_templates` table (use `type: bigint` + `autoIncrement: true`, **not** `bigserial` — combining `bigserial` with `autoIncrement: true` creates a sequence conflict)
- `003` — seed templates (`WELCOME`, `ORDER_SHIPPED` × 3 channels each)

If a changeset fails mid-run, Liquibase records it as failed in `DATABASECHANGELOG`. Re-running without wiping the volume will not retry it — run `docker compose down -v` first.

### Sender stubs

`AbstractStubSender` reads three properties that are overridden in `src/test/resources/application-test.yaml` to make tests deterministic:

```yaml
# application-test.yaml (test profile)
app.sender.failure-rate: 0.0   # no random failures
app.sender.min-delay-ms: 0
app.sender.max-delay-ms: 0
```

Add `@ActiveProfiles("test")` to any test that invokes the consumer path.

### Integration tests

`NotificationIntegrationTest` uses `@Testcontainers(disabledWithoutDocker = true)` — tests are skipped automatically when Docker is unavailable rather than failing. Containers (Postgres, Redis, Confluent Kafka) are declared as `static @Container` fields and wired via `@DynamicPropertySource`.
