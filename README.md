# notification-service

Async notification delivery service: accepts notification requests via REST, publishes to Kafka, and processes them asynchronously across EMAIL / SMS / PUSH channels.

---

## Architecture

```
┌─────────────┐   POST /api/v1/notifications   ┌─────────────────────┐
│   Client    │ ─────────────────────────────> │  NotificationController │
└─────────────┘         HTTP 202               └──────────┬──────────┘
                                                          │ validate + rate-limit
                                                          ▼
                                               ┌─────────────────────┐
                                               │  NotificationService │
                                               └──────┬───────┬──────┘
                                                      │       │
                                             persist  │       │ publish
                                                      ▼       ▼
                                               PostgreSQL   Kafka
                                               (status=     topic:
                                               ACCEPTED)  notification.requests
                                                                │
                                                                ▼
                                               ┌─────────────────────┐
                                               │  NotificationConsumer│
                                               └──────────┬──────────┘
                                                          │
                                          ACCEPTED → PROCESSING → SENT | FAILED
                                                          │
                                              render template + call sender stub
```

### Packages

| Package       | Responsibility                                        |
|---------------|-------------------------------------------------------|
| `api`         | REST controller, DTOs, global exception handler       |
| `application` | Orchestration: validate, persist, publish to Kafka    |
| `domain`      | Enums, status transition rules, domain exceptions     |
| `persistence` | JPA entities, repositories                           |
| `messaging`   | Kafka message DTO, Kafka consumer                     |
| `template`    | Template lookup and `{{placeholder}}` interpolation   |
| `sender`      | Channel sender interface + stubs (EMAIL/SMS/PUSH)     |
| `ratelimit`   | Redis-backed rate limiter (5 req/min per recipientId) |
| `config`      | Kafka topic bean, Security permit-all                 |

### Status transitions

```
ACCEPTED → PROCESSING → SENT
                      → FAILED
```

Any other transition throws `InvalidStatusTransitionException`.

---

## Running with Docker Compose

### Prerequisites

- Docker Desktop (or Docker Engine + Compose plugin)
- JDK 21 (for building the jar)

### Start all services (Postgres + Redis + Kafka + app)

```bash
./gradlew bootJar          # build the fat jar first
docker compose up --build  # start infrastructure + app
```

The app will wait for Postgres, Redis, and Kafka to pass their healthchecks before starting.

### Start only infrastructure (for local development)

```bash
docker compose up postgres redis kafka
```

Then run the app locally:

```bash
./gradlew bootRun
```

### Stop and remove containers

```bash
docker compose down          # stop containers, keep volumes
docker compose down -v       # stop containers AND delete volumes (wipes DB)
```

---

## Running tests locally

### Unit tests (no Docker required)

```bash
./gradlew test
```

The integration tests are automatically **skipped** when Docker is unavailable (`@Testcontainers(disabledWithoutDocker = true)`).

### Integration tests (requires Docker)

Start Docker Desktop, then:

```bash
./gradlew test
```

Testcontainers will spin up Postgres, Redis, and Kafka automatically in containers. No manual setup needed.

---

## API

### Submit a notification

```
POST /api/v1/notifications
Content-Type: application/json

{
  "recipientId": "550e8400-e29b-41d4-a716-446655440000",
  "templateCode": "WELCOME",
  "channelType": "EMAIL",
  "parameters": { "name": "Alice" },
  "priority": "HIGH"
}
```

**Response — HTTP 202 Accepted**

```json
{
  "notificationId": "a1b2c3d4-...",
  "status": "ACCEPTED"
}
```

**Errors**

| Status | Cause                                    |
|--------|------------------------------------------|
| 400    | Missing or invalid fields                |
| 429    | Rate limit exceeded (5/min per recipient)|

---

## Configuration

All defaults are in `application.yaml`. Override for Docker Compose via environment variables:

| Env var                          | Default                                |
|----------------------------------|----------------------------------------|
| `SPRING_DATASOURCE_URL`          | `jdbc:postgresql://localhost:5432/notifications` |
| `SPRING_DATASOURCE_USERNAME`     | `notification`                         |
| `SPRING_DATASOURCE_PASSWORD`     | `notification`                         |
| `SPRING_DATA_REDIS_HOST`         | `localhost`                            |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                       |

| App property               | Default | Effect                                        |
|----------------------------|---------|-----------------------------------------------|
| `app.sender.failure-rate`  | `0.1`   | Probability a sender stub throws (test: `0.0`)|
| `app.sender.min-delay-ms`  | `50`    | Simulated send latency lower bound            |
| `app.sender.max-delay-ms`  | `150`   | Simulated send latency upper bound            |

---

## Tradeoffs & known limitations

### Delivery guarantees

- **At-least-once**: Kafka consumer does not use manual offset commits with idempotent processing. A consumer crash after `PROCESSING` but before `SENT`/`FAILED` will re-process the message, potentially re-sending.
- Idempotency guard (check `status == ACCEPTED` before transitioning) would prevent duplicate processing but is not yet implemented.

### Rate limiting

- Redis `INCR` + `EXPIRE` is not atomic when the key doesn't exist yet. Under high concurrency a small window exists where the TTL might not be set. A Lua script or `SET NX PX` pattern would be strictly atomic.
- Rate limit counters are lost on Redis restart.

### What would be improved with more time

- Idempotent consumer: skip reprocessing if notification is already `SENT`
- Dead-letter topic for permanently failed messages
- Outbox pattern: write to DB + publish in a single transaction (currently the Kafka publish can succeed after a DB rollback or vice versa)
- Retry with exponential backoff on sender failures
- Metrics (Micrometer counters for sent/failed per channel)
- OpenAPI / Swagger docs
- Authentication on the REST API