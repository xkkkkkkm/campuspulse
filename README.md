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

Support uses a local **LangGraph** service with 15 bilingual guides, cited answers, saved conversations and explicit ticket submission. No model key is required for retrieval. To enable model generation, the person running this installation must supply their own provider endpoint, model name and API key; see [the setup steps below](#enable-model-generated-support-optional). Website visitors do not enter keys. By default, generation is disabled; real external model and SMTP delivery have not been verified. See the [support agent guide](support-agent/README.md).

## Download and run

Download this repository with GitHub **Code → Download ZIP**, extract it, and open a terminal in the extracted directory (the one containing `docker-compose.yml`). Alternatively:

```bash
git clone https://github.com/xkkkkkkm/campuspulse.git
cd campuspulse
```

All commands in this README run from that repository root. On Windows, use `py -3.11` (or your installed Python 3.11+ command) wherever these commands say `python3`.

For the simplest local run, install **Docker with Compose v2** (Docker Desktop on macOS/Windows) and **Python 3.11+**, then start Docker. Java, Maven, Node.js, MySQL and the support service's Python 3.13 run inside containers; they do not need separate host installations for this path. Internet access is needed for the first image/dependency downloads. Verify the prerequisites:

```bash
python3 --version
docker compose version
docker info
```

Then initialize and start the demo:

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

Open [http://127.0.0.1:8125](http://127.0.0.1:8125). The first build downloads dependencies and can take several minutes; Docker build output is expected. The helper creates a local `.env` with unique application secrets, then builds and waits for all four services: MySQL, the API, the frontend and the private LangGraph support service. Support retrieval works without a model key. Host ports bind to loopback only: frontend `8125`, API `8080`, MySQL `3306`; the support service has no published host port.

| Demo role | Username | Password |
| --- | --- | --- |
| Student | `linzhixia` | `demo12345` |
| Organizer | `org` | `org123` |
| Administrator | `admin` | `admin123` |

These credentials are public local fixtures. The default `demo` profile seeds each database once; restarts preserve changes and do not refresh the sample event dates. To try registration, click **Send code**: demo mode displays the verification code in the page message/dialog, and no real email is sent. You can also use the public accounts above without registering. Use a fresh production database and the [production setup](docs/operations.md#production-configuration) for a deployment with real users.

```bash
python3 tools/dev.py down
```

Stopping the stack retains its named data volumes. Run `python3 tools/dev.py up` again to resume. See [operations](docs/operations.md) for port conflicts, native Java development, backups, restore and troubleshooting.

## Check your first run

```bash
docker compose ps
python3 tools/smoke_test.py --base-url http://127.0.0.1:8125
```

Expect four healthy services (`mysql`, `backend`, `frontend`, `support-agent`) and a smoke report without failed assertions. The smoke check uses the demo accounts; it is not for a production database and does not validate SMTP or model generation. If you changed `FRONTEND_PORT`, replace `8125` in both the browser address and `--base-url`.

Sign in as the student to browse activities, apply to join a team and ask the assistant “How can I join a team?”. Use the organizer account to review activity registrations and the administrator account to manage reviews and support tickets. Joining a team and registering for its linked activity are separate actions; a pending application needs approval.

| Capability | Works after the default startup? | Additional setup |
| --- | --- | --- |
| Activities, teams, chat, notifications and manual support tickets | Yes | Demo accounts provided above |
| Bilingual support guides with citations | Yes | No model key; small local phrase/keyword retrieval |
| Model-generated support answers | No, disabled by default | Your own endpoint, model and API key; instructions below |
| Real verification emails | No, demo shows codes on screen | [SMTP configuration](docs/operations.md#production-configuration) |
| Recommendations | Yes, rule ranking | [Offline model training](ml/README.md) needs sufficient genuine observations; synthetic demo data is excluded |

## Enable model-generated support (optional)

**The person deploying CampusPulse configures the model once for the installation. Ordinary website users do not supply API keys.** LangGraph orchestrates the support workflow; it does not provide a model account, API key or free model credits. Without this setup, the assistant returns reviewed local guides, which are not LLM-generated answers.

1. Choose a provider and create an API key in that provider's console. Get its exact model identifier and OpenAI-compatible **base URL** (not the full `/chat/completions` endpoint). The selected model/API must support Chat Completions and `response_format: {"type":"json_object"}`. Model availability and charges depend on the provider.
2. After `python3 tools/dev.py init`, open the generated **`.env` in the repository root** with a text editor. Edit the existing entries below in `.env` and keep its other settings. These are file contents, not terminal commands; `.env.example` is only the template. Replace all three example values with your provider's values:

```dotenv
SUPPORT_AGENT_ENABLED=true
SUPPORT_LLM_ENABLED=true
SUPPORT_LLM_BASE_URL='https://api.your-provider.example/v1'
SUPPORT_LLM_MODEL='your-provider-model-id'
SUPPORT_LLM_API_KEY='your-provider-api-key'
```

The `.example` address and sample model/key above are placeholders. Keep the generated `SUPPORT_AGENT_TOKEN`: it is a different internal shared secret, not your model key. `.env` is ignored by Git; do not commit it or paste it into an issue. Only the Python support container receives the provider key; the browser does not. Enabling generation sends questions and limited recent history to the provider with best-effort redaction; see [data handling and limits](support-agent/README.md#optional-model-generation-and-privacy).

3. Apply the edited environment to both the backend and support service:

```bash
docker compose up -d --wait backend support-agent
```

Use `up` to recreate containers with new environment values; `docker compose restart` does not reload `.env`. If you use a separate environment/project such as `.env.prod` and `campuspulse-prod`, use `docker compose --env-file .env.prod -p campuspulse-prod up -d --wait backend support-agent`. For native development, follow the [support startup guide](support-agent/README.md#run-locally).

4. **Sign in** before asking a question. Open the assistant, start a new conversation and ask “How can I join a team?”. Anonymous visitors always use retrieval. Even when enabled, generation requires a matching guide and available quota; the default limits are 10 generation reservations per user/hour and 100 for the installation/day (`SUPPORT_MAX_DAILY_GENERATIONS`).
5. Check the label below the **new** answer. “Assistant” indicates a model result passed validation; “Help documentation” indicates retrieval/fallback. For an exact check, open browser developer tools → **Network**, select the `POST /api/support/chat` response, and inspect `data.source`:

| `data.source` | Meaning |
| --- | --- |
| `LANGGRAPH_LLM` | The model returned a result that passed the application's checks |
| `LANGGRAPH_RETRIEVAL` | Local LangGraph guides; the model was not used or fell back |
| `LOCAL_KNOWLEDGE` | Java emergency guide; the graph service was disabled, unavailable or busy |

A successful HTTP response or a healthy container alone does not prove a model call succeeded. If you still receive guides, check login, all four `SUPPORT_LLM_*` values, the exact provider/model contract, quota, and [fallback diagnostics](support-agent/README.md). The default provider timeout is 8 seconds; rejected output and timeouts return local guides. Unknown questions suggest a support ticket; they do not automatically create one.

To stop external model calls, set `SUPPORT_LLM_ENABLED=false` and run the same `docker compose up` command. Local guides and tickets remain available. The adapter and simulated-provider tests are implemented; real external provider calls have not been validated for this repository.

## Common setup problems

| Symptom | What to do |
| --- | --- |
| `docker` / `python3` command not found | Install the prerequisites above; on Windows use `py -3.11` or your installed 3.11+ Python command |
| Cannot connect to the Docker daemon | Start Docker Desktop/the Docker service, then retry `docker info` |
| Build is downloading layers or Maven/Python packages | Allow the first build to finish; for download errors, check network/proxy access and rerun `python3 tools/dev.py up` |
| Port already in use | Change `FRONTEND_PORT`, `BACKEND_PORT` or `MYSQL_PORT` in `.env`; update browser origins if the frontend port changes; see [the port example](docs/operations.md#local-container-demo) |
| Page opens but API calls fail | Check `docker compose ps` and `docker compose logs --tail=100 backend`; opening HTML files directly does not start the API |
| Edited `.env` but behavior did not change | Run `docker compose up -d --wait` to recreate affected containers; exported shell variables override `.env` |
| No verification email arrives | Default demo mode shows the code on screen. Configure SMTP for real delivery |
| Some content remains Chinese in English mode | Demo content has translations; user-authored text, names and uploaded image text remain original |
| Existing demo events become old | Seeding is one-time; publish a new event or use a separate demo database. Restarting does not refresh dates |

For development outside Docker, required tools and service startup order are in [native development](docs/operations.md#native-development).

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
