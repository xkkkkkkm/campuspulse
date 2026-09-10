# Portfolio Overview

[Project](../README.md) · [中文版项目介绍](../README.zh-CN.md) · [Current validation status](remediation-status.md)

CampusPulse is a collaborative university course project addressing activity discovery, registration and teammate coordination. The repository provides a runnable full-stack application and documents how it behaves, how its state is protected and where its deployment model stops. It is not evidence of a university-operated service or a measured production deployment.

## Suggested review path

1. Run the local demo from the root README and switch the interface between English and Chinese.
2. Sign in as a student to browse activities, use favorites and inspect personal registration/team records.
3. Use the organizer and administrator demo accounts to inspect publication, approval and governance workflows. New changes persist across restarts.
4. Review [architecture](architecture.md), [API](api.md) and [operations](operations.md), then follow the source pointers below.
5. Run the included checks in an isolated local environment and read their reports. Treat historical assessments as dated context.

## Implemented engineering work

| Concern | Implementation and evidence |
| --- | --- |
| Business state | Activity/team services, transactional parent-row locks, optimistic versions, archive semantics and database constraints |
| Account security | Password hashing, signed expiring tokens, account token versions, email-code consumption, production configuration checks |
| Realtime collaboration | Persisted messages, client retry IDs, forward/backward cursors, read states, one-use SSE tickets and private image access |
| Administrative governance | Role/status controls, last-active-administrator protection, activity review, tag/featured management and audit records |
| Recommendation pipeline | Bounded historical conversion training, temporal validation, baseline/ablation metrics, immutable release scores and fallback rules |
| Reproducibility | Source-built Compose stack, safe environment loader, Flyway migrations, unit/integration/browser tests and backup/restore helper |
| Accessibility and language | English/Chinese interface, local assets and automated browser/source checks; see the frontend guide for exact coverage |

Useful source entry points are `backend/src/main/java/com/campuspulse/activity/ActivityService.java`, `team/TeamService.java`, `message/MessageService.java`, `security/TokenService.java`, `auth/EmailCodeService.java`, `recommend/RecommendService.java`, and `ml/train_recommendation.py`. The migration and test directories document the persistence assumptions and selected failure cases.

## What the repository does not claim

The model is a GradientBoostingClassifier, with no implemented LambdaMART training or semantic embedding retrieval. Seeded examples are synthetic and excluded from training evidence. A new demo is expected to have insufficient real observations for a trained release. Observational ranking metrics do not establish causal recommendation lift.

The runtime uses one API instance. SSE, tickets and request counters are process-local; shared messaging, shared rate limits, HTTPS termination, high availability and an operational monitoring stack need additional deployment work. The current SQL and bounded candidate strategy are not a benchmark for a large campus population.

LangGraph support runs locally without a model key and supports optional grounded generation, saved conversations and explicit tickets. Real external model/SMTP delivery has not been established by local tests. Test code, CI configuration and documentation are reviewable evidence of engineering practice; they are not claims that every test passed on every platform or that the application has completed an external security or accessibility certification.

## Contribution and provenance

This repository grew from collaborative course work. No individual authorship, institution endorsement, grade, deployment adoption, performance number or research outcome is inferred from that origin. Anyone presenting it as a personal portfolio should identify their own commits and explain which components were inherited, implemented or revised. Historical plans and presentations may describe ambitions that exceed the current code; the current implementation documents take precedence for technical claims.
