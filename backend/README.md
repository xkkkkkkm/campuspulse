# CampusPulse API

[中文运行说明](../docs/operations.zh-CN.md) · [API reference](../docs/api.md) · [Architecture](../docs/architecture.md)

The backend is a Java 17 Spring Boot modular monolith using Spring MVC, JDBC, MySQL and Flyway. Feature packages cover authentication, activities, teams, messages, profiles, recommendations, notifications, support and administration. Activity and team workflows use service and repository classes; some smaller features still query through `JdbcTemplate` in their controllers. This is not a microservice deployment or a uniform domain-layer abstraction.

## Run

The complete Docker setup needs Docker Compose v2 and Python 3.11+: from the repository root, run `python3 tools/dev.py init` and `python3 tools/dev.py up`.

For a native development API, install JDK 17, Maven 3.9+ and Python 3.11+, and start only the database:

```bash
python3 tools/dev.py init
python3 tools/dev.py run docker compose up -d --wait mysql
python3 tools/dev.py backend
```

In a second terminal, `python3 tools/dev.py frontend` starts the Node frontend proxy (Node.js 22+). The environment helper reads `.env` as data and passes values to subprocesses. A direct Maven invocation does not load `.env` automatically.

The API listens on [http://127.0.0.1:8080](http://127.0.0.1:8080) by default. In development, interactive OpenAPI documentation is at [http://127.0.0.1:8080/api-docs-ui](http://127.0.0.1:8080/api-docs-ui), with JSON at `/v3/api-docs`. The `prod` profile disables both. Liveness and readiness endpoints are `/actuator/health/liveness` and `/actuator/health/readiness`; readiness includes the database. Metrics at `/actuator/metrics` are available on the private API port and are not forwarded by the frontend proxy.

## Database and profiles

Flyway owns the schema. SQL migrations are under `src/main/resources/db/migration`; the V2 Java migration is under `src/main/java/db/migration`. Spring's ad hoc SQL initialization is disabled. The migration chain covers the baseline, legacy schema upgrades, account/security and support records, business consistency, recommendation publication and private chat media/read state.

The default `demo` profile installs public example accounts and synthetic fixtures once per database. The `prod` profile validates distinct random signing and verification secrets, rejects development verification mode, and creates a first administrator on a fresh database from `APP_ADMIN_*`. Production requires real SMTP for email flows. Changing a profile does not remove demo accounts from an existing database. Follow [the production guide](../docs/operations.md#production-configuration).

## Engineering boundaries

- Protected `/api` routes validate signed access tokens and current account status, role and token version. Logout revokes all tokens for that account. Account recovery uses email codes, not SMS.
- Registration and team approval serialize capacity decisions in transactions. Versioned edits reject stale updates; archived records are excluded from live workflows.
- Message retries use client IDs, cursor history and database persistence; SSE is an in-process delivery hint. Chat attachments are served by an authenticated API with channel access checks.
- Avatar and cover files use local persistent storage. There is no object-storage adapter in this repository.
- The private LangGraph service provides bilingual retrieval and optional generation. Java owns saved conversations, ownership, turn leases and request budgets. Local fallback and explicit tickets remain available. Live SMTP/model delivery requires separate provider verification.
- SSE emitters, stream tickets and general request limits are local to one JVM. Do not scale this deployment to multiple API replicas without shared coordination.

## Tests

```bash
# Unit tests only
python3 tools/dev.py run mvn -f backend/pom.xml test

# Unit and MySQL Testcontainers integration tests; Docker must be available
python3 tools/dev.py test
```

Tests cover security/configuration, image handling, migrations and persistence, business workflows and selected concurrency cases. Failsafe runs `*IT` tests during `verify`; `mvn test` alone does not execute them. Reports are generated under `backend/target/`, including the JaCoCo HTML report at `target/site/jacoco/index.html`. See [current validation status](../docs/remediation-status.md) rather than inferring measured coverage or production readiness from the presence of tests.
