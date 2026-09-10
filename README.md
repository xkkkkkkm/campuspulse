# CampusPulse

[简体中文](README.zh-CN.md) · [Run and operate](docs/operations.md) · [Architecture](docs/architecture.md) · [API](docs/api.md) · [Review guide](docs/portfolio-overview.md)

CampusPulse is a university course project for discovering campus activities and finding teammates. It combines a bilingual web interface, a Java API, MySQL persistence, and an optional offline recommendation pipeline. This repository presents the implemented system and its engineering limits for source review and local evaluation.

## What you can do

- Browse, filter, search and favorite activities; register and track approval status.
- Publish and manage activities, review registrations, and open participant chat.
- Create teams, apply to join, review applications, transfer leadership and manage membership.
- Send direct, team and activity messages, including private image attachments; reconnect using stored message history and read cursors.
- Manage a profile and interests; use email verification for registration, account recovery and account changes.
- Review notifications and activity reminders; submit support tickets and receive administrator replies.
- Administer accounts, roles, activity reviews, tags and featured content with audit records.
- Receive recommendations from interest, popularity, time and availability rules, optionally combined with a trained historical conversion model.

The interface offers English and Simplified Chinese, including reviewed English titles, descriptions and locations for all 40 demo activities and 33 teams, platform tags, and generated covers. English titles and platform tags are searchable. User-authored text and uploaded image pixels retain their originals; editing demo text invalidates its outdated translation.

Support uses a local **LangGraph** service with 15 bilingual guides, cited answers, saved conversations and explicit ticket submission. No model key is required for retrieval. Optional model generation is configured separately; real external model and SMTP delivery have not been verified. See the [support agent guide](support-agent/README.md).

## Download and run

Download this repository with GitHub **Code → Download ZIP**, extract it, and open a terminal in the extracted directory; a normal Git clone works too. No committed credentials or prebuilt application archive is required.

For the simplest local run, install **Docker with Compose v2** and **Python 3.11+**, start Docker, then run:

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

Open [http://127.0.0.1:8125](http://127.0.0.1:8125). The first build downloads dependencies. The helper creates a local `.env` with unique application secrets, then builds and waits for all four services: MySQL, the API, the frontend and the private LangGraph support service. Support retrieval works without a model key. Host ports bind to loopback only: frontend `8125`, API `8080`, MySQL `3306`; the support service has no published host port.

| Demo role | Username | Password |
| --- | --- | --- |
| Student | `linzhixia` | `demo12345` |
| Organizer | `org` | `org123` |
| Administrator | `admin` | `admin123` |

These credentials are public local fixtures. The default `demo` profile seeds each database once; restarts preserve changes and do not refresh the sample event dates. Email development mode shows a verification code for evaluating account flows without SMTP. Use a fresh production database and the [production setup](docs/operations.md#production-configuration) for a deployment with real users.

```bash
python3 tools/dev.py down
```

Stopping the stack retains its named data volumes. See [operations](docs/operations.md) for port conflicts, native Java development, backups, restore and troubleshooting.

## Repository and architecture

```text
frontend/   Plain HTML, CSS and modular browser JavaScript; EN / zh-CN UI
backend/    Java 17, Spring Boot, JDBC, Flyway and MySQL
support-agent/  FastAPI / LangGraph, bilingual guides and optional model generation
ml/         Offline temporal GradientBoostingClassifier training and releases
docs/       Current architecture, API, operations and historical course sources
tools/      Environment loader, frontend proxy, smoke checks and backup helpers
```

Business features use a modular monolith: a Node server serves the frontend and proxies `/api` to one Spring Boot instance. A private LangGraph service handles support retrieval/generation; Java owns its conversations, permissions and ticket writes. MySQL stores application records and recommendation scores; a persistent upload directory stores images. SSE connections, stream tickets and request counters are process-local, so the supplied deployment uses one API instance. HTTPS termination and shared infrastructure for multiple instances are outside the supplied stack.

The optional trainer uses observed exposures and server-recorded conversions, with a chronological holdout and a purged label window. It excludes legacy and synthetic demo telemetry. Insufficient real observations produce an `insufficient-data` report with no trained release; the application continues with rule ranking. See [the model documentation](ml/README.md) for features, evaluation, publishing and rollback. No production accuracy or causal improvement is claimed.

## Verification

Source checks and tests are included; the commands below describe how to reproduce them, not a guarantee that every environment has passed. The [remediation and validation record](docs/remediation-status.md) records the current review and validation state.

```bash
# Java 17 and Maven 3.9+; Docker must be running for MySQL integration tests
python3 tools/dev.py test

# Node.js 22+; includes frontend and local proxy checks
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend test
node --test tools/tests/proxy.test.cjs

# Python helper tests; no live application required
python3 -m unittest discover -s tools/tests

# Running demo stack required; login, discovery and access-control smoke checks
python3 tools/dev.py run python3 tools/smoke_test.py
```

Fixture-writing media, model publication and recovery checks have a separate [isolated test setup](docs/operations.md#isolated-integration-exercises). Browser checks are documented in [frontend/README.md](frontend/README.md); model tests and their Python dependencies are documented in [ml/README.md](ml/README.md). Backend integration tests use real MySQL through Testcontainers. Course-era assessments and design sources are retained as historical context, separate from the current implementation claims.

## Project provenance and license

CampusPulse originated as a collaborative course project. This repository does not assign every component to one person or imply endorsement by a university. Personal contribution claims should be supported by commits and reviewable changes.

Project code is available under the [MIT License](LICENSE). Bundled third-party files retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Historical course binaries, local uploads, secrets and generated build outputs are excluded from the source release.
