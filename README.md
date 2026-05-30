# Notification-service

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
| `config`      | Kafka topic bean, HTTP Basic Auth, password encoding  |

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

## Authentication

All API endpoints require **HTTP Basic Auth**. The actuator endpoints (`/actuator/**`) are public.

Default credentials (configurable via `app.security.username` / `app.security.password`):

| Field    | Default  |
|----------|----------|
| Username | `admin`  |
| Password | `secret` |

Example with curl:

```bash
curl -u admin:secret -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{ ... }'
```

To override credentials without rebuilding, set environment variables:

```bash
APP_SECURITY_USERNAME=myuser APP_SECURITY_PASSWORD=mypass ./gradlew bootRun
# or in docker-compose.yml under app.environment:
#   APP_SECURITY_USERNAME: myuser
#   APP_SECURITY_PASSWORD: mypass
```

---

## API

### Submit a notification

```
POST /api/v1/notifications
Authorization: Basic YWRtaW46c2VjcmV0
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

| Status | Cause                                     |
|--------|-------------------------------------------|
| 400    | Missing or invalid fields                 |
| 401    | Missing or invalid credentials            |
| 429    | Rate limit exceeded (5/min per recipient) |

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

| App property                | Default  | Effect                                         |
|-----------------------------|----------|------------------------------------------------|
| `app.security.username`     | `admin`  | Basic Auth username                            |
| `app.security.password`     | `secret` | Basic Auth password (BCrypt-encoded at runtime)|
| `app.sender.failure-rate`   | `0.1`    | Probability a sender stub throws (test: `0.0`) |
| `app.sender.min-delay-ms`   | `50`     | Simulated send latency lower bound             |
| `app.sender.max-delay-ms`   | `150`    | Simulated send latency upper bound             |

---

## Вопросы
### Где в текущей архитектуре узкие места при росте нагрузки и как бы вы их устраняли? 

Простая реализация записи уведомления в базу данных и отправки сообщений в Kafka. Риск потери сообщений из-за отсутствия атомарности между записью в базу данных и отправкой сообщения в Kafka.
1. Запись успешно сохраняется в БД.
2. Отправка сообщения в Kafka завершается ошибкой.
3. Запись остается в базе, но сообщение в Kafka отсутствует.
4. Такая запись никогда не попадет в обработку.

### Как исправить

Использовать паттерн **Transactional Outbox**:

- В рамках одной транзакции сохранять:
    - основную запись уведомления;
    - запись в outbox-таблицу.
- Отдельный процесс (publisher) читает события из outbox и публикует их в Kafka.
- После успешной публикации событие помечается как обработанное.

Такой подход гарантирует, что событие не будет потеряно между БД и Kafka.

---

### Как обеспечивается (или не обеспечивается) гарантия доставки и идемпотентность обработки сообщений? Что произойдёт при падении консьюмера в середине обработки?

Гарантия доставки сообщений без дубликатов обеспечивается путем правильной конфигурации продюсера, нужно, чтобы сделать producer идемпотентным (enable.idempotence=true). Kafka автоматически присваивает продюсеру ID (PID) и для каждой партиции ведется свой sequence number для сообщений.
Чтобы корректно срабатывал идемпотентность продюсера, нужно чтобы остальные конфигурации тоже соответствовали
При падении консьюмера в середине обработки, если offset не был закоммичен, то после перезапуска еще раз считывается тот же сообщение, что приведет к дубликату записи.


Необходимые настройки:

```properties
enable.idempotence=true
acks=all
retries=Integer.MAX_VALUE
max.in.flight.requests.per.connection=5
```

---

### Что бы вы изменили или добавили, имея ещё неделю времени?
1. Разделил бы API часть от Processing часть, это даст нам независимое масштабирование, отказоустойчивость и обработка продолжится даже если API недоступно.
3. Реализовал бы retry с экспоненциальной задержкой и DLT.
4. Кэширование шаблонов
5. Добавил бы защиту на Kafka-cluster.
   - аутентификацию (SASL);
   - шифрование трафика (SSL/TLS);
   - ACL для разграничения прав доступа продюсеров и консьюмеров.
6. Добавил бы метрики и централизованное логирование