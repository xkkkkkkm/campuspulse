# Run and Operate CampusPulse

[简体中文](operations.zh-CN.md) · [Project README](../README.md) · [Architecture](architecture.md)

Commands below run from the repository root. Use Python 3.11+ for the development and backup helpers. The examples use the default ports; adjust `.env` if another service occupies them. On Windows, use a Python 3.11+ command such as `py -3.11` where `python3` is unavailable.

## Local container demo

Install Docker with a recent Compose v2 supporting `up --wait` and Python 3.11+ on the host. Java, Maven, Node.js and the agent’s Python 3.13 runtime are installed inside the images; a container demo does not require them on the host. Start Docker, then run:

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

The helper initializes the repository-root `.env`, generates distinct random application secrets, and starts MySQL, the Java backend, the Node frontend proxy and the private LangGraph support agent after a source build. The database and upload directory persist in named volumes. Dependencies and container images are downloaded on the first run.

| Local service | Default address | Purpose |
| --- | --- | --- |
| Frontend | `http://127.0.0.1:8125` | Browser pages and same-origin API proxy |
| API | `http://127.0.0.1:8080` | API, health endpoints, development OpenAPI |
| MySQL | `127.0.0.1:3306` | Local database access and optional trainer |
| Support agent | Compose network only: `http://support-agent:8001` | Private LangGraph service called by Java; no host port |

All published host ports bind to loopback. Do not start both the container API and native API on the same host port. For example, if another MySQL uses port 3306 and another web app uses 8080, set `MYSQL_PORT=13306` and `BACKEND_PORT=18080` in `.env`. If you also change `FRONTEND_PORT=18125`, set `APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:18125,http://localhost:18125`, run `python3 tools/dev.py up`, and open `http://127.0.0.1:18125`. These settings change host ports; Compose service-to-service addresses keep their container ports.

The `demo` profile seeds each database once. Public accounts are `linzhixia / demo12345`, `org / org123`, and `admin / admin123`. The seed is a historical demonstration fixture, not production traffic. Repeated starts do not overwrite changes or refresh activity dates. With `APP_SECURITY_EMAIL_DEV_MODE=true`, click **Send code** after entering an eligible email address. The registration page shows the code in an alert; email sign-in, password recovery and profile account changes show it in the nearby status text as “development code”. The send-code API response contains `data.devCode` (for example, `POST /api/auth/email/send`). No SMTP email is sent in this mode, so the code is on the page rather than in your inbox. Email sign-in and recovery still require an email already bound to an account.

```bash
# Stop containers; retain database and uploaded files
python3 tools/dev.py down

# Show service status and recent logs
python3 tools/dev.py run docker compose ps
python3 tools/dev.py run docker compose logs --tail=100 backend
```

Compose `down -v` deletes named volumes and their data. It is not the normal stop command. To evaluate a fresh demo while preserving an existing one, choose an unused Compose project name and different host ports.

## Environment files and applying changes

`.env.example` documents defaults; `python3 tools/dev.py init` creates the private root `.env` used by the helper. Keep credentials in that local file. The helper parses `KEY=value` as data, strips matching outer quotes, and does not execute shell code or expand variable references. Prefer single-quoted literal values such as `SUPPORT_LLM_API_KEY='your-provider-key'`, especially when a value contains `$` or `#`; do not use `source .env`.

For helper-launched commands, an existing process environment variable overrides the selected file, including an empty value. If an edit appears ignored, remove an old exported value or open a terminal with the intended environment. Helper defaults such as `SERVER_PORT`, `PORT`, `TARGET`, `DB_URL` and `CAMPUS_DB_*` apply only if that variable is absent; an explicit value can override the mapped port/database settings. Compose forwards only settings declared in [docker-compose.yml](../docker-compose.yml), not every variable in the file.

`--env-file` selects one file; it does not merge `.env.prod` or `.env.verify` with `.env`. It also does not select a new Compose project: use `-p` to isolate containers and volumes. Use the same file and project for startup, status, logs and shutdown. For example:

```bash
python3 tools/dev.py --env-file .env.prod run docker compose --env-file .env.prod -p campuspulse-prod ps
python3 tools/dev.py --env-file .env.prod run docker compose --env-file .env.prod -p campuspulse-prod logs --tail=100 support-agent
```

After editing `.env`, run `python3 tools/dev.py up` again. Compose recreates services whose configuration changed; `docker compose restart` restarts the old containers with their old environment. For a named production project, rerun the full `up` command in [Production configuration](#production-configuration). Native processes must be stopped with Ctrl+C and launched again through the helper with the same `--env-file`.

## Native development

Install JDK 17, Maven 3.9+, Node.js 22+, Python 3.11+ for the helpers and a MySQL 8 server. Running the LangGraph service natively also requires Python 3.13. Docker is optional when using your own MySQL; alternatively, let Docker provide only MySQL:

```bash
python3 tools/dev.py init
python3 tools/dev.py run docker compose up -d --wait mysql
python3 tools/dev.py backend
```

In a second terminal:

```bash
python3 tools/dev.py frontend
```

To run LangGraph locally, use a third terminal at the repository root. Create its virtual environment and install dependencies once, then launch it through the same helper so Java and Python receive the same root `.env` and `SUPPORT_AGENT_TOKEN`:

```bash
python3.13 -m venv support-agent/.venv
support-agent/.venv/bin/python -m pip install -r support-agent/requirements.txt
python3 tools/dev.py run support-agent/.venv/bin/python -m uvicorn app.main:app --app-dir support-agent --host 127.0.0.1 --port 8001 --workers 1 --no-access-log
```

On Windows, create the environment with `py -3.13 -m venv support-agent/.venv`, replace `support-agent/.venv/bin/python` with `support-agent/.venv/Scripts/python.exe`, and use `py -3.11` for the helper if needed. Java defaults to `SUPPORT_AGENT_URL=http://127.0.0.1:8001`; this must match the agent listener. If port 8001 is occupied, change both the Uvicorn `--port` and `SUPPORT_AGENT_URL` in the shared file, then relaunch both processes. The Compose hostname `support-agent` is not the address for native Java. Check the agent with `curl --fail http://127.0.0.1:8001/healthz`. See the [agent guide](../support-agent/README.md) for detailed native configuration. Without that process, Java’s local knowledge fallback can answer, but it does not run LangGraph or call a model.

The helper maps local ports and database settings for Java, the Node proxy and the Python trainer. Direct `mvn`, `node` or `uvicorn` commands do not load the root `.env` on their own. With a custom file, add the same `--env-file` before `backend`, `frontend` or `run` in each terminal. To use your own MySQL server, configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in that file or the process environment and create the database first. Flyway applies the schema at API startup. If training against an external database too, set the trainer’s matching `CAMPUS_DB_*` settings as described in [ml/README.md](../ml/README.md); a custom JDBC `DB_URL` is not parsed into trainer settings.

The Node service serves HTML and browser assets without an npm installation. Install npm dependencies for development checks or browser tests. Never serve the repository root with an unrestricted static server: it contains local configuration, source and development artifacts.

## Production configuration

The supplied production profile is a configuration boundary for a single instance. It does not install HTTPS, a public reverse proxy, shared rate limits, failover or monitoring infrastructure.

Start with a separate environment file and a **fresh database/project**, because changing the profile does not remove public demo accounts:

```bash
python3 tools/dev.py --env-file .env.prod init --profile prod
```

Edit `.env.prod` locally before startup:

| Setting | Required action |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Keep `prod` |
| `APP_SECURITY_TOKEN_SECRET`, `APP_SECURITY_EMAIL_SECRET` | Keep separate random secrets generated by the helper; store them privately |
| `APP_SECURITY_EMAIL_DEV_MODE` | Keep `false` |
| `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` | Use the generated production values; existing volumes do not automatically adopt changed passwords |
| `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_EMAIL` | Set the initial administrator name and valid email; the helper generates a password |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Supply the real SMTP provider settings |
| `MAIL_AUTH`, `MAIL_STARTTLS`, `APP_SECURITY_EMAIL_FROM` | Match the provider's authentication, STARTTLS and verified sender requirements |
| `APP_CORS_ALLOWED_ORIGINS` | Set exact browser origins, including scheme and port |
| `FRONTEND_PORT`, `BACKEND_PORT`, `MYSQL_PORT` | Select unoccupied host ports; these remain bound to loopback |

The first administrator username accepts 3–64 letters, digits or underscores; the password must contain 16–128 characters and sufficient distinct characters. The initialization fails on invalid details rather than granting an existing account elevated access. After the first successful start, remove `APP_ADMIN_PASSWORD` from the deployment configuration; ordinary restarts use the existing administrator account.

```bash
python3 tools/dev.py --env-file .env.prod run docker compose --env-file .env.prod -p campuspulse-prod up --build -d --wait --wait-timeout 240
```

SMTP authentication uses your provider’s SMTP username and password/app password, independent of any model API key. `MAIL_STARTTLS=true` enables STARTTLS on an SMTP connection (commonly port 587); it does not enable implicit TLS/SMTPS on port 465 or require STARTTLS negotiation. The supplied Compose configuration has no implicit-TLS or STARTTLS-required switch. Use a provider’s supported SMTP/STARTTLS endpoint; a provider requiring other transport settings needs explicit application/Compose configuration. When the backend is in Docker, `MAIL_HOST=localhost` refers to that backend container, so replace it with the actual SMTP host. Use an authorized sender in `APP_SECURITY_EMAIL_FROM`.

The API refuses weak/equal application secrets or enabled development verification codes. These checks do not verify real SMTP delivery. Test email registration, recovery and account changes against your chosen provider before admitting users. Mail connectivity is intentionally not part of the health probe; an `UP` result is not proof that email works.

Place the frontend behind an HTTPS reverse proxy and keep database/API host ports private. Ensure the proxy forwards SSE without buffering and allows long-lived responses. The application limits requests using the socket peer IP; the bundled proxy can aggregate many users under one limit. Establish trusted proxy handling and perimeter limits before public use. Do not simply trust arbitrary `X-Forwarded-For` headers.

Only one API instance is supported by the supplied SSE/ticket/rate-limit implementation. Horizontal scaling needs shared ticket storage, coordinated rate limits and shared event delivery, plus an explicit image-storage design. A mounted shared directory alone is insufficient.

## Optional integrations and training

The local LangGraph service starts with Compose and needs no model credentials for cited guide answers. `tools/dev.py init` creates the shared internal `SUPPORT_AGENT_TOKEN`; it is separate from a provider API key and must match between Java and the agent. The service has no published host port. To enable model-generated replies, follow the complete [README configuration steps](../README.md#enable-model-generated-support-optional), then recreate the configured services as described above. No model is selected by default.

Authenticated requests use per-user quotas and `SUPPORT_MAX_DAILY_GENERATIONS` (100 by default); anonymous questions use retrieval only. Enabling generation sends the question and bounded recent history to that provider, with best-effort redaction. See the [agent guide](../support-agent/README.md) for native startup, limits and tests. Real external model and SMTP delivery remain unverified. Historical Dify settings are not read by the current implementation; configure the LangGraph service instead.

Training is an explicit offline command, not a background service. See [ml/README.md](../ml/README.md) for the read-only dry run, bounded training, model release and rollback. No trained release is required to use the application. Expired or missing model scores fall back to rule recommendations.

## Health, logs and checks

The [validation record](remediation-status.md) lists the locally executed results and remaining checks; the commands below reproduce the checks.

```bash
curl --fail http://127.0.0.1:8125/healthz
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:8080/actuator/health/liveness
# Inspect metrics only through the private API address
curl --fail http://127.0.0.1:8080/actuator/metrics

# Java tests, including real MySQL integration tests; requires Docker
python3 tools/dev.py test

# Frontend source, unit and proxy checks
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend test
node --test tools/tests/proxy.test.cjs

# Helper unit tests
python3 -m unittest discover -s tools/tests
```

The limited `tools/smoke_test.py` checks anonymous team discovery, logs in with the public student fixture, reads the profile/activity/recommendation routes and verifies denial of administrator access. Run `python3 tools/dev.py run python3 tools/smoke_test.py`; its default base URL is `http://127.0.0.1:8125`, overridable with `--base-url`. It requires the unchanged demo credentials and does not validate the complete workflow or a production deployment. Although it creates no business fixtures, login updates account lock counters and writes authentication audit records; it is not strictly read-only. Browser and model checks have separate instructions in their component READMEs.

Choose a target appropriate to the check:

| Check | Target and write behavior |
| --- | --- |
| Java `tools/dev.py test` | Writes fixtures into its own temporary MySQL Testcontainers databases; requires host JDK 17, Maven and Docker, not a running demo stack |
| Frontend unit/mock browser tests; helper/agent unit tests | Local or mocked services; no live application fixtures or paid model calls |
| `tools/smoke_test.py` | Running demo; login counters and authentication audit only |
| `tools/support_smoke_test.py` | Running disposable demo; writes conversation turns and deletes its conversation on cleanup; login audit remains |
| `tools/media_smoke_test.py` | Running disposable demo; leaves an uploaded image and private message |
| Frontend `e2e:live` | Running disposable demo; leaves a replied/closed support ticket and browser telemetry; creates a support conversation and attempts to delete it |
| `ml/tests/integration_publication.py` | Explicit disposable MySQL target; writes model versions/scores, temporarily switches the active release, then restores and cleans its test versions |
| `tools/integration_backup.py` | Disposable Compose source and new restore project; writes an image/message, pauses the source API and retains backups/projects |

Keep `SUPPORT_LLM_ENABLED=false` in a live test stack and apply it before running support or live browser checks. Their authenticated questions can call the configured real provider if generation is enabled. Deleting test conversations does not undo provider requests or login audit writes.

Readiness includes database health; liveness checks process availability separately. Metrics are exposed on the private API port and are not forwarded by the frontend proxy. The API emits ECS structured console logs and an `X-Request-ID` response header for correlation. Collect logs in the deployment environment; avoid exporting credentials, authorization headers, email codes or private message bodies. Health endpoints do not expose detailed internals. OpenAPI is available in development at `/api-docs-ui` and `/v3/api-docs` and is disabled in `prod`.

## Isolated integration exercises

The extra checks below write fixtures and pause the test API during backup. Use a **new, disposable** Compose project and its own environment; do not run them against an existing personal or production database. The ordinary backup commands in the next section remain the operational interface.

Create `.env.verify` with `python3 tools/dev.py --env-file .env.verify init`. Set `MYSQL_PORT=23306`, `BACKEND_PORT=28080`, `FRONTEND_PORT=28125`, and `APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:28125,http://localhost:28125` in that file. Choose another set if those ports or the project names below are already in use. Keep the `demo` profile, its isolated fixture accounts and `SUPPORT_LLM_ENABLED=false`.

```bash
python3 tools/dev.py --env-file .env.verify run docker compose --env-file .env.verify -p campuspulse-verify up --build -d --wait --wait-timeout 240

# Writes a support conversation, checks history/ownership, then deletes it
python3 tools/support_smoke_test.py --base-url http://127.0.0.1:28125

# Creates an image and direct message; verifies access and retry behavior
python3 tools/media_smoke_test.py --base-url http://127.0.0.1:28125

# Requires the Python environment from ml/README.md; explicit test-database opt-in
CAMPUS_TEST_DATABASE=1 python3 tools/dev.py --env-file .env.verify run .venv/bin/python ml/tests/integration_publication.py

# Pauses only this disposable source API and restores to a new test project
CAMPUS_TEST_DATABASE=1 python3 tools/dev.py --env-file .env.verify run python3 tools/integration_backup.py --source-project campuspulse-verify --source-url http://127.0.0.1:28125 --target-project campuspulse-verify-restore --backup-path runtime/verify-backup --mysql-port 23307 --backend-port 28081 --frontend-port 28126
```

`media_smoke_test.py` checks the sender/recipient can fetch the image, an unrelated user and anonymous request cannot, the public `/uploads/chat` path returns `404`, and a retried send has the same ID. `integration_publication.py` uses small synthetic models/fixtures in real MySQL, restores the previous active release and removes its test versions. It verifies publication mechanics, not recommendation quality.

`integration_backup.py` checks counts for eleven persistent content tables, restores the private image for its recipient and compares its SHA-256, then runs the restored API smoke. It writes `verification.json` in the new backup directory and leaves both projects available for inspection. Select an unused backup directory and restore project on each run. `CAMPUS_TEST_DATABASE=1` is an explicit opt-in, not automatic proof that a database is disposable. The script receives database settings through the environment helper; retain the matching `.env.verify` when inspecting the projects.

These examples use POSIX shell environment-assignment syntax; in PowerShell set `$env:CAMPUS_TEST_DATABASE='1'` before the guarded commands. See the [validation ledger](remediation-status.md) for which exercises have actually completed.

## Backup and restore

The helper requires the Compose backend and MySQL services to be running. A backup briefly stops the backend writer, dumps the database and archives `/app/uploads`, then restarts the backend even if capture fails. This is a maintenance window: requests may fail during the pause. Suspend separate training jobs and other database writers as well; the helper controls only the Compose API writer.

For the default demo project:

```bash
python3 tools/backup.py backup backups/demo-snapshot --project campuspulse
```

For the production project above:

```bash
python3 tools/backup.py backup backups/prod-snapshot --project campuspulse-prod --env-file .env.prod
```

Choose a new output directory each time. A completed backup contains `database.sql`, `uploads.tar.gz` and a SHA-256 manifest. It includes private account, registration, chat and upload data; keep the backup directory private. It does not include `.env`, secrets, source revisions or local ML artifacts. Preserve the deployed source revision, secrets and any model artifacts separately in your secure backup process. A directory without a complete manifest is not a successful backup.

Restore into an **unused project name**, never over the running deployment. Make a separate environment file, retain the relevant database credentials/profile, and choose different `MYSQL_PORT`, `BACKEND_PORT` and `FRONTEND_PORT` values so the original project can remain running. Update its browser CORS origin to match the restored frontend. For example, copy `.env.prod` to `.env.restore` privately and use ports `13306`, `18080`, `18125`.

```bash
python3 tools/backup.py restore backups/prod-snapshot --project campuspulse-restore --env-file .env.restore
```

Restore verifies checksums and rejects unsafe archive entries, the source project name, or a target project with existing containers/volumes. It starts new MySQL storage, imports the dump and images, and starts the application. If a restore fails, it may leave a partial target project for diagnosis; use another unused name for the next attempt after inspecting it.

After restore, check readiness, login, representative registrations/team membership, message history and an uploaded image on the restored port. Compare the source revision with its Flyway history. There is no measured recovery-time or data-loss guarantee; practice this procedure with your deployment and retain the original system until the restored result is verified.

## Retention and upgrades

Bounded cleanup jobs run approximately hourly. The general job removes notifications older than six months and behavior/event/reminder-delivery records older than twelve months, at most 500 records per category per tick. Its expired-code threshold is seven days, but the separate authentication job already deletes codes expired for more than one day, send guards older than two days and authentication audit records older than 90 days, up to 1000 per category per tick. Support cleanup deletes up to 100 conversations inactive for 30 days with no active turn lease, plus up to 1000 generation-budget records older than 90 days. Users can also select or delete their own saved conversations; each account has at most 50 conversations, with at most 50 turns each. These jobs do not delete business records, peer/group chat or administration audit history. Table growth, old model releases and upload usage need operational review; the repository does not provide a universal retention policy or complete erasure workflow.

Take a backup before changing the deployed revision. Flyway validates and applies pending migrations; do not edit already-applied migrations or disable checks to bypass a failure. An older application binary may not understand a newer schema. Restore into an isolated project to rehearse rollback instead of assuming database downgrades are reversible.

## Common problems

| Symptom | First action |
| --- | --- |
| Port already allocated | Change the corresponding environment-file port and browser origin, then rerun that project’s `up` command to recreate containers |
| Changed key/configuration has no effect | Check the selected `--env-file`, project name and old exported variables; recreate Compose services with `up` or relaunch native processes |
| Native support answers only from fallback | Start the separate agent process; check its loopback URL, health and shared `SUPPORT_AGENT_TOKEN` |
| Database access denied after a password edit | Existing MySQL volumes retain their original users/passwords; restore the matching configuration or deliberately rotate credentials |
| Startup fails in `prod` | Read the validation error; check secrets, development mode and first-administrator fields |
| Compose remains unhealthy | Inspect `docker compose ps` and backend/MySQL logs; check memory, Docker availability and dependency downloads |
| Browser shows a backend error | Check API readiness and the proxy target; native and container APIs must not compete for the same port |
| New demo cannot train a model | Expected until sufficient real, mature observations exist; demo/legacy events are excluded |
| Activity dates look old after restart | Seeding is one-time; create new activities or use a separate fresh demo database |
| SMTP fails while readiness is UP | Mail is not a readiness dependency; verify provider credentials, sender and STARTTLS settings |
| Burst traffic receives 429 behind the proxy | Current IP quotas see the shared proxy address; review perimeter/tenant limits before increasing exposure |
