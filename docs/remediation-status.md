# Remediation and Validation Record

[简体中文](remediation-status.zh-CN.md) · [Original assessment](engineering-assessment.zh-CN.md) · [Runbook](operations.md)

This record follows the F01–F15 findings in the **2026-09-10** engineering assessment. That assessment is the pre-remediation snapshot; its old line references, test counts and failure descriptions are not the current acceptance record. Results below describe local checks against the revised source and isolated test data. They do not claim a hosted GitHub Actions run, a production deployment or performance on real campus traffic.

F01–F15 are remediated and validated within the local scopes recorded below. The default `8125` stack, isolated stack, fresh isolated `prod` initialization, backup/restore and listed automated checks have passed. SMTP/model delivery, real-scale performance and real-user recommendation effects remain outside the completed validation.

## Bilingual content and LangGraph update

Reviewed translations cover 40 activities and 33 teams, including generated covers, labels, details, records and source-preserving edit forms. English titles and canonical labels are searchable. V7 binds seed identity, V8 adds owned support conversations and model budgets, and V9 refreshes only unchanged legacy support-demo descriptions. User edits are preserved.

Private FastAPI/LangGraph support provides 15 bilingual guides, contextual follow-ups, citations and optional generation. Java owns conversation listing/recovery/deletion, ownership, 45-second leases, per-user limits and atomic daily generation budgets. Anonymous users cannot generate with a model; unknown answers only suggest an explicit ticket action.

The LangGraph service passed **52 tests** including HTTP and simulated provider calls; its 47 Python runtime dependencies returned no known audit findings. No real external model was called. Earlier three-service isolated prod/recovery exercises remain evidence for the preceding release; the default four-service stack is now running.

## F01–F15 disposition

“Verified” means the relevant automated or local check passed for the stated case. It does not mean every possible browser, network failure, workload or third-party provider has been exercised.

| Finding | Current correction | Verification and remaining boundary |
| --- | --- | --- |
| F01 · Flyway startup failure | Added MySQL support; Flyway owns baseline/upgrades and one-time initialization. | Real MySQL migration/infrastructure tests, isolated Compose startup and default `8125` startup/API smoke passed; the current default stack has four healthy services; the earlier isolated stack had three. |
| F02 · Frontend container bind address | Container listener uses `0.0.0.0`; host-published ports remain on loopback. Native ports and proxy target are configurable. | Isolated host-accessible stack/real browser tests and default host `8125` API smoke passed; local-only port bindings are retained. |
| F03 · Malformed paths terminate Node | URL decoding, streams and proxy errors have bounded HTTP error handling. | Three proxy tests passed, including malformed-path rejection followed by a successful normal request, private/traversal paths and HEAD/security headers. |
| F04 · Unsafe return URL | Central navigation allowlist rejects dangerous protocols, external hosts, invalid paths and ambiguous separators. | Node navigation tests and mocked browser safe-return/dialog tests passed. No arbitrary user-provided navigation is treated as trusted. |
| F05 · Production accepts public secrets | Production rejects published placeholders, weak/equal secrets and development verification mode; the helper generates random secrets. | Configuration tests and production-bootstrap integration checks passed. HTTPS, secrets custody and the operator's external deployment configuration remain operational responsibilities. |
| F06 · Legacy unverified registration | `/auth/register` returns `410`; email-code registration is the supported entry. Migration clears unverified legacy email reservations, and identifier conflicts are serialized. | MySQL tests passed for rejected legacy registration, rollback/reusable codes and concurrent cross-column account identifiers. Real SMTP delivery is untested. |
| F07 · Capacity locking protocol | Activity/team approval and capacity edits share parent-first locks and current reads. | MySQL concurrency tests passed for the final activity/team place and concurrent shrinking/approval at REPEATABLE READ. These are targeted race tests, not a throughput benchmark. |
| F08 · Partial state transitions | Approval, cancellation, rejection, membership and notification changes use consistent transactions/state checks; history is archived. | MySQL lifecycle/concurrency tests and injected notification-failure rollback passed, including cancellation/approval, transfer/leave/reapply and archive access. |
| F09 · Old sessions survive recovery/logout | Database token versions invalidate old credentials; protected requests recheck current account role/status/version. | Token tests and MySQL reset/revocation tests passed. Both logout routes revoke all current tokens for the account. |
| F10 · OTP/counter races | Email-purpose guards, atomic code consumption, persisted failed-attempt counts and login lockout updates serialize competing requests. | MySQL tests passed for concurrent send, single concurrent consumption, reusable code after rollback, atomic two-code changes and preserved failure increments. External email side effects were not exercised. |
| F11 · Chat cannot recover after disconnect | Fresh one-use tickets, retry/backoff/polling, persisted history catchup, duplicate merging and stable send IDs replace blind EventSource replay. | Node and mocked browser recovery/retry tests passed; MySQL tests verify cursor order, direct-message serialization and idempotent sends. SSE/tickets remain process-local, not a durable distributed queue. |
| F12 · Incomplete list/history pagination | Filters/pages are sent to the server; chat opens the latest page, loads older records and drains newer cursors. Personal/admin/organizer records also paginate. | MySQL tests cover records beyond old limits; browser fixtures cover activity 63, saved-record paging and chat history/catchup. Array endpoints may expose an empty final next page when the last batch was full. |
| F13 · Registration/upload mismatch | The avatar remains local until registration returns a token. Authenticated uploads enforce multipart/byte/pixel/quota limits and re-encode supported images. | Mocked browser registration/avatar and private-image retry tests, image unit checks and MySQL ownership/quota tests passed. Chat images require authenticated uploader/conversation access. |
| F14 · Demo restart overwrites records | A transactional initialization marker seeds each demo database once; current scores/state are retained. | MySQL initialization test passed after saved-state changes and repeated bootstrap. Demo dates are intentionally not refreshed on restart. |
| F15 · Fabricated metrics and temporal leakage | Synthetic/legacy events are excluded; GradientBoostingClassifier uses past-event features, purged temporal validation, computed metrics/baselines/ablation and immutable release scores. | Six temporal tests and an isolated real-MySQL release exercise passed: publish, injected failure rollback, previous-release activation, expiry fallback and artifact hash verification. Controlled fixtures validate the pipeline, not real-user recommendation quality. |

Source evidence: [infrastructure integration tests](../backend/src/test/java/com/campuspulse/infrastructure/InfrastructureIT.java), [business integration tests](../backend/src/test/java/com/campuspulse/business/BusinessWorkflowIT.java), [security integration tests](../backend/src/test/java/com/campuspulse/auth/SecurityFlowsIT.java), [frontend unit tests](../frontend/tests/core.test.mjs), [mocked browser tests](../frontend/tests/e2e/ui.spec.mjs), [real-stack browser tests](../frontend/tests/e2e/live.spec.mjs), [proxy tests](../tools/tests/proxy.test.cjs), [helper tests](../tools/tests/test_dev.py), [backup safety tests](../tools/tests/test_backup.py), and [temporal model tests](../ml/tests/test_temporal.py), [live private-media check](../tools/media_smoke_test.py), and [isolated model publication check](../ml/tests/integration_publication.py).

## Executed validation

| Check | Recorded result | Evidence scope |
| --- | --- | --- |
| Java/MySQL integration | **48 passed** | Real MySQL through Testcontainers; migrations through V9, translation identity/search, conversation ownership/leases/budgets, security, business state, concurrency, media and recommendation serving |
| Java unit checks | **24 passed** | Final `mvn clean verify` exited 0; unit and integration reports contain no failures, errors or skips; JaCoCo report generated |
| Frontend Node | **12 passed** | Navigation, locale, request cancellation, stale results, pagination, chat retry and stream tickets |
| Mocked Playwright | **20 passed** | Real browser UI with deterministic API fixtures; does not prove database/provider integration |
| Real-stack Playwright | **4 passed** | Current default four-service stack: student sign-in/main pages, ticket reply/close, demo content/cover language switching and LangGraph citations/history restoration/deletion |
| LangGraph Python | **52 passed** | Actual graph, bilingual retrieval, HTTP boundaries, limits and simulated provider transport; no real external model call |
| Live support API smoke | **Passed** | English title search, bilingual graph answers, multi-turn context, conversation ownership/deletion and explicit escalation |
| Node proxy | **3 passed** | Invalid/private paths, continued availability, HEAD and security headers |
| Python environment helper | **3 passed** | Values parsed as data, port mapping, private initialization and preservation of custom secrets |
| Python backup safety | **3 passed** | API restarted after dump failure; altered checksum and an existing stopped target container rejected |
| Temporal model unit tests | **6 passed** | Future/same-second event ordering, trusted labels, purged windows and computed metrics |
| Isolated Docker Compose | **Passed: three services healthy** | Source-built MySQL/API/frontend test project, separate from existing data volumes |
| Isolated MySQL model publication | **Passed** | Controlled fixture publication, rollback after failure, reactivation, expiry and artifact SHA-256 |
| Dependency audits | **No known findings returned** | 73 Maven runtime dependencies, npm and pinned Python dependencies at the time of this local audit |
| Default local startup at `8125` | **Passed: four services healthy** | `dev.py init/up` completed; frontend-port API/support smoke and all four real-stack browser cases passed; upload volume ownership/write access checked |
| Database plus uploads backup/restore | **Passed** | Eleven content-table counts match; recipient-authorized restored private-image SHA-256 matches; restored API smoke passes; source table counts unchanged |
| Fresh isolated `prod` first start | **Passed: three services healthy** | Generated bootstrap administrator can sign in; one user and no activities/teams; public demo login denied; Swagger unavailable; readiness includes DB; metrics available only on direct API port |
| Independent local change review | **Completed** | No additional P1/P2 findings reported in the final scoped review; not an external security certification |
| Unknown-resource status regression | **Passed** | Framework missing routes/resources return `404`; unit regression tests and live private-path denial exercised |
| Live private-image smoke | **Passed** | Sender/recipient `200`, unrelated user `403`, anonymous `401`, public upload path `404`; repeated send returns the same message ID |

All four real-stack browser cases were run against the current default `8125` stack. Earlier release checks are recorded in `runtime/verification-20260910.json`; the bilingual content/LangGraph update is recorded separately in `runtime/verification-langgraph-20260910.json`. Both local records are ignored by Git.

The six Python tool tests comprise three environment-helper tests and three backup-safety tests. The recovery exercise ran from `campuspulse-validation` into a new `campuspulse-restore-check` project, with a local report at `/private/tmp/campuspulse-backup-20260910/verification.json`. The isolated production report is `/private/tmp/campuspulse-prod-check.json`. These reports record local checks; database dumps, backup contents and environment secrets are not committed. The [recovery exercise script](../tools/integration_backup.py) provides the repeatable procedure.

Mocked browser counts must not be described as complete live workflows. Dependency audit results are time-dependent and cover the checked dependency inventories; they are not proof of absence of application vulnerabilities or a complete container/operating-system audit. No coverage percentage, QPS, recovery-time objective or scale limit has been inferred from these counts.

Reproduce checks using the [runbook](operations.md#health-logs-and-checks), [frontend guide](../frontend/README.md#verification), [backend guide](../backend/README.md#tests) and [model guide](../ml/README.md). Local reports live under `backend/target/surefire-reports`, `backend/target/failsafe-reports`, `backend/target/site/jacoco` and `frontend/playwright-report`; these generated reports are not committed and later runs may replace them. Fixture-writing media/publication/recovery commands are documented under [isolated integration exercises](operations.md#isolated-integration-exercises), for disposable projects only. The [CI workflow](../.github/workflows/ci.yml) defines automated checks; configuration alone is not evidence of a completed remote run.

New source evidence: [content migration tests](../backend/src/test/java/com/campuspulse/content/ContentTranslationMigrationIT.java), [support conversation tests](../backend/src/test/java/com/campuspulse/support/SupportConversationIT.java), [internal support client tests](../backend/src/test/java/com/campuspulse/support/SupportAgentClientTest.java), [support API smoke](../tools/support_smoke_test.py), and [LangGraph testing/configuration](../support-agent/README.md).

## Features completed alongside the findings

The revised implementation includes team transfer/leave/close/member removal, archive-aware workflows, private image messages and group read cursors, durable reminder deduplication, persistent support ticket replies/status, administrator audit and role governance, paginated discovery/personal records, modular frontend code and English/Chinese UI. Local Font Awesome assets retain their license; project code has an MIT license and bilingual setup/operations/model documentation. The source reference for these capabilities is [architecture](architecture.md) and [API](api.md), with their tested portions identified above.

The project remains a collaborative course-derived system. Documentation does not attribute all code to an individual or imply university endorsement. Historical binaries, third-party institutional artwork, secrets, uploads and local generated artifacts are outside the source release.

## Explicit limits

- One API instance: SSE emitters, stream tickets and general request limits are in memory. Multiple instances need shared coordination/event delivery and a storage design.
- Anonymous IP limits see the proxy socket address; authenticated support uses independent user quotas. Other IP-based buckets can still be shared. Public deployment needs a trusted-proxy/perimeter policy and HTTPS termination.
- SMTP and optional model calls have **not** been jointly tested with real providers. Local LangGraph retrieval and explicit tickets work without a model key; production email flows still require verified provider configuration.
- ML integration uses controlled test data in real MySQL. Fresh demos normally lack sufficient genuine observations and use rules. No real-campus accuracy, causal recommendation lift or production adoption is claimed.
- No production-scale load test, measured QPS, multi-region/high-availability exercise, external security audit or complete accessibility certification is recorded.
- Health, database-aware readiness, private metrics and structured logs exist; a full monitoring/alerting stack, ongoing backup schedule and a completed public release are not implied by them.

## Preservation and provenance

Before remediation, a local source/configuration snapshot was saved as `/tmp/campuspulse-before-fixes-20260910-172718.tar.gz`. It is local preservation evidence, not a downloadable release. The original database was read-only checked as empty before default startup; its existing volume was retained and migrated/initialized without a reset. Fixture-writing exercises used isolated databases/projects, which were removed after validation while local evidence was retained. Private environment files were excluded from the checked release inventory; the local `.env` had mode `0600`. Before the content/LangGraph migrations, the populated default database and uploads were backed up under `backups/pre-langgraph-20260910/`; its existing volumes were preserved. The original assessment remains unchanged as historical context, with this ledger linked as the current record.
